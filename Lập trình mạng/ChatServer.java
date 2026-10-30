import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer Đa Nền Tảng:
 * - Hỗ trợ TCP Socket thuần (Java Swing GUI, Console Client, Flutter)
 * - Hỗ trợ WebSocket và HTTP Web Server (Trình duyệt Safari trên iPhone, Android, PC)
 * - Cổng dùng chung: 5000 (Mọi thiết bị đều kết nối chung 1 cổng này!)
 */
public class ChatServer {
    private static int getPort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (Exception ignored) {}
        }
        return 5000;
    }
    private static final int PORT = getPort();
    private static final String HISTORY_FILE = "chat_history.txt";
    private static final String ACCOUNTS_FILE = "accounts.txt";
    private static final String WEB_FILE = "web_chat.html";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Quản lý danh sách Client đang online: Username -> ClientHandler
    private static final Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    // Database tài khoản: Username -> [Password, Role]
    private static final Map<String, String[]> userDatabase = new ConcurrentHashMap<>();

    static {
        loadAccounts();
    }

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("[SERVER] Khởi động Server Đa Nền Tảng tại cổng " + PORT);
        System.out.println("[SERVER] + TCP Socket: Kết nối qua Java GUI / Console / Flutter");
        System.out.println("[SERVER] + Web/iPhone: Mở trình duyệt truy cập: http://localhost:" + PORT);
        System.out.println("==================================================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[SERVER ERROR] " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    // ================= QUẢN LÝ TÀI KHOẢN =================
    private static synchronized void loadAccounts() {
        File file = new File(ACCOUNTS_FILE);
        if (!file.exists()) {
            userDatabase.put("admin", new String[]{"123", "ADMIN"});
            userDatabase.put("duy",   new String[]{"123", "USER"});
            userDatabase.put("hai",   new String[]{"123", "USER"});
            userDatabase.put("long",  new String[]{"123", "USER"});
            saveAccounts();
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split(":", 3);
                if (p.length >= 2) {
                    String u = p[0].trim().toLowerCase();
                    String pass = p[1].trim();
                    String role = p.length >= 3 ? p[2].trim().toUpperCase() : "USER";
                    userDatabase.put(u, new String[]{pass, role});
                }
            }
        } catch (IOException e) {
            System.err.println("[ACCOUNTS ERROR] " + e.getMessage());
        }
    }

    private static synchronized void saveAccounts() {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ACCOUNTS_FILE), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, String[]> entry : userDatabase.entrySet()) {
                bw.write(entry.getKey() + ":" + entry.getValue()[0] + ":" + entry.getValue()[1]);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("[ACCOUNTS ERROR] " + e.getMessage());
        }
    }

    // ================= LỊCH SỬ CHAT =================
    public static synchronized void saveHistory(String logLine) {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(HISTORY_FILE, true), StandardCharsets.UTF_8))) {
            bw.write(logLine);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[HISTORY ERROR] " + e.getMessage());
        }
    }

    public static synchronized List<String> getRecentHistory(int limit) {
        List<String> lines = getAllHistory();
        int start = Math.max(0, lines.size() - limit);
        return new ArrayList<>(lines.subList(start, lines.size()));
    }

    public static synchronized List<String> getAllHistory() {
        List<String> lines = new ArrayList<>();
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return lines;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    public static synchronized void clearHistory() {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(HISTORY_FILE), StandardCharsets.UTF_8))) {
            pw.print("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcast(String message) {
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(message);
        }
    }

    public static void broadcastOnlineList() {
        StringBuilder sb = new StringBuilder("ONLINE_LIST|");
        boolean first = true;
        for (Map.Entry<String, ClientHandler> entry : onlineClients.entrySet()) {
            if (!first) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue().role);
            first = false;
        }
        broadcast(sb.toString());
    }

    // ================= LỚP XỬ LÝ TỪNG CLIENT (TCP + WEBSOCKET) =================
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private InputStream rawIn;
        private OutputStream rawOut;
        private BufferedReader in;
        private PrintWriter out;
        private boolean isWebSocket = false;

        private String username = null;
        private String role = "USER";
        private boolean isMuted = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                rawIn = socket.getInputStream();
                rawOut = socket.getOutputStream();

                // Đọc dòng đầu tiên để phân loại kết nối: HTTP/WebSocket hay TCP Socket thuần
                in = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));
                String firstLine = in.readLine();
                if (firstLine == null) return;

                if (firstLine.startsWith("GET ")) {
                    // Xử lý yêu cầu HTTP / WebSocket từ Trình duyệt (iPhone, Android, PC...)
                    Map<String, String> headers = new HashMap<>();
                    String headerLine;
                    while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                        int colon = headerLine.indexOf(':');
                        if (colon != -1) {
                            headers.put(headerLine.substring(0, colon).trim().toLowerCase(), headerLine.substring(colon + 1).trim());
                        }
                    }

                    if ("websocket".equalsIgnoreCase(headers.get("upgrade"))) {
                        // Bắt tay nâng cấp lên WebSocket
                        doWebSocketHandshake(headers);
                        System.out.println("[WEBSOCKET] Thiết lập kết nối WebSocket với " + socket.getRemoteSocketAddress());

                        // Vòng lặp đọc các khung tin (Frames) WebSocket
                        while (true) {
                            String packet = readWebSocketFrame();
                            if (packet == null) break;
                            handlePacket(packet);
                        }
                    } else {
                        // Trả về trang Web App (web_chat.html) cho trình duyệt iPhone/Android
                        serveWebPage();
                        socket.close();
                        return;
                    }
                } else {
                    // Kết nối TCP Socket thuần (Java Swing GUI, Console, Flutter)
                    out = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true);
                    handlePacket(firstLine);

                    String rawPacket;
                    while ((rawPacket = in.readLine()) != null) {
                        handlePacket(rawPacket);
                    }
                }
            } catch (IOException e) {
                System.out.println("[DISCONNECT] Kết nối của " + (username != null ? username : "khách") + " đã đóng.");
            } catch (Exception e) {
                System.err.println("[HANDLER ERROR] " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        // ================= PHỤC VỤ WEB & WEBSOCKET =================
        private void serveWebPage() throws IOException {
            File htmlFile = new File(WEB_FILE);
            byte[] htmlBytes;
            if (htmlFile.exists()) {
                htmlBytes = java.nio.file.Files.readAllBytes(htmlFile.toPath());
            } else {
                htmlBytes = "<h1>Tour Crew Chat Server</h1><p>File web_chat.html chua duoc tao!</p>".getBytes(StandardCharsets.UTF_8);
            }

            String responseHeader = "HTTP/1.1 200 OK\r\n" +
                                   "Content-Type: text/html; charset=UTF-8\r\n" +
                                   "Content-Length: " + htmlBytes.length + "\r\n" +
                                   "Connection: close\r\n\r\n";
            rawOut.write(responseHeader.getBytes(StandardCharsets.UTF_8));
            rawOut.write(htmlBytes);
            rawOut.flush();
        }

        private void doWebSocketHandshake(Map<String, String> headers) throws Exception {
            String key = headers.get("sec-websocket-key");
            if (key == null) return;
            String acceptKey = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest(
                    (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)
                )
            );
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                              "Upgrade: websocket\r\n" +
                              "Connection: Upgrade\r\n" +
                              "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            rawOut.write(response.getBytes(StandardCharsets.UTF_8));
            rawOut.flush();
            this.isWebSocket = true;
        }

        private String readWebSocketFrame() throws IOException {
            int b0 = rawIn.read();
            if (b0 == -1) return null;
            int opcode = b0 & 0x0F;
            if (opcode == 8) return null; // Close frame

            int b1 = rawIn.read();
            if (b1 == -1) return null;
            boolean isMasked = (b1 & 0x80) != 0;
            int length = b1 & 0x7F;

            if (length == 126) {
                int ch1 = rawIn.read();
                int ch2 = rawIn.read();
                if ((ch1 | ch2) < 0) return null;
                length = (ch1 << 8) + ch2;
            } else if (length == 127) {
                long len = 0;
                for (int i = 0; i < 8; i++) {
                    int c = rawIn.read();
                    if (c < 0) return null;
                    len = (len << 8) | c;
                }
                length = (int) len;
            }

            byte[] mask = new byte[4];
            if (isMasked) {
                int r = 0;
                while (r < 4) {
                    int c = rawIn.read(mask, r, 4 - r);
                    if (c < 0) return null;
                    r += c;
                }
            }

            byte[] payload = new byte[length];
            int totalRead = 0;
            while (totalRead < length) {
                int r = rawIn.read(payload, totalRead, length - totalRead);
                if (r < 0) return null;
                totalRead += r;
            }

            if (isMasked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }

            return new String(payload, StandardCharsets.UTF_8);
        }

        private synchronized void sendWebSocketFrame(String text) {
            try {
                byte[] payload = text.getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x81); // Text frame opcode
                if (payload.length <= 125) {
                    frame.write(payload.length);
                } else if (payload.length <= 65535) {
                    frame.write(126);
                    frame.write((payload.length >> 8) & 0xFF);
                    frame.write(payload.length & 0xFF);
                } else {
                    frame.write(127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((int) ((payload.length >> (8 * i)) & 0xFF));
                    }
                }
                frame.write(payload);
                rawOut.write(frame.toByteArray());
                rawOut.flush();
            } catch (IOException e) {
                forceClose();
            }
        }

        // ================= XỬ LÝ GÓI TIN CHUNG (PROTOCOL ROUTER) =================
        private void handlePacket(String rawPacket) {
            if (rawPacket.trim().isEmpty()) return;

            String command;
            int firstPipe = rawPacket.indexOf('|');
            if (firstPipe != -1) {
                command = rawPacket.substring(0, firstPipe).toUpperCase();
            } else {
                command = rawPacket.toUpperCase();
            }

            if (username == null && !command.equals("LOGIN") && !command.equals("REGISTER")) {
                sendMessage("ERROR|Bạn phải đăng nhập trước!");
                return;
            }

            switch (command) {
                case "REGISTER":
                    handleRegister(rawPacket);
                    break;
                case "LOGIN":
                    handleLogin(rawPacket);
                    break;
                case "MSG_ALL":
                    handlePublicChat(rawPacket);
                    break;
                case "MSG_PRIVATE":
                    handlePrivateChat(rawPacket);
                    break;
                case "ONLINE":
                    sendOnlineList();
                    sendMessage("SERVER|Đang online (" + onlineClients.size() + "): " + onlineClients.keySet());
                    break;
                case "GET_FULL_HISTORY":
                    handleGetFullHistory();
                    break;
                case "ADMIN_KICK":
                    handleAdminKick(rawPacket);
                    break;
                case "ADMIN_MUTE":
                    handleAdminMute(rawPacket);
                    break;
                case "ADMIN_CLEAR_HISTORY":
                    handleAdminClearHistory();
                    break;
                case "ADMIN_ANNOUNCE":
                    handleAdminAnnounce(rawPacket);
                    break;
                default:
                    sendMessage("ERROR|Lệnh không hợp lệ: " + command);
            }
        }

        private void handleRegister(String rawPacket) {
            String[] parts = rawPacket.split("\\|", 4);
            if (parts.length < 3) {
                sendMessage("REGISTER_FAIL|Thiếu thông tin đăng ký!");
                return;
            }
            String user = parts[1].trim().toLowerCase();
            String pass = parts[2].trim();
            String regRole = parts.length > 3 ? parts[3].trim().toUpperCase() : "USER";
            if (!regRole.equals("ADMIN")) regRole = "USER";

            if (user.isEmpty() || pass.isEmpty()) {
                sendMessage("REGISTER_FAIL|Tài khoản và mật khẩu không được rỗng!");
                return;
            }
            if (userDatabase.containsKey(user)) {
                sendMessage("REGISTER_FAIL|Tài khoản '" + user + "' đã tồn tại!");
                return;
            }

            userDatabase.put(user, new String[]{pass, regRole});
            saveAccounts();
            System.out.println("[REGISTER] Tài khoản mới: " + user + " (" + regRole + ")");
            sendMessage("REGISTER_OK|Đăng ký thành công tài khoản [" + user + "] với quyền " + regRole + "!");
        }

        private void handleLogin(String rawPacket) {
            String[] parts = rawPacket.split("\\|", 3);
            if (parts.length < 3) {
                sendMessage("LOGIN_FAIL|Thiếu thông tin đăng nhập.");
                return;
            }
            String user = parts[1].trim().toLowerCase();
            String pass = parts[2].trim();

            if (!userDatabase.containsKey(user) || !userDatabase.get(user)[0].equals(pass)) {
                sendMessage("LOGIN_FAIL|Sai tài khoản hoặc mật khẩu!");
                return;
            }
            if (onlineClients.containsKey(user)) {
                sendMessage("LOGIN_FAIL|Tài khoản này đang được đăng nhập ở nơi khác!");
                return;
            }

            this.username = user;
            this.role = userDatabase.get(user)[1];
            onlineClients.put(username, this);

            sendMessage("LOGIN_OK|" + this.role + "|Đăng nhập thành công với quyền " + this.role);
            System.out.println("[AUTH] " + username + " (" + role + ") đã đăng nhập.");

            List<String> history = getRecentHistory(30);
            if (!history.isEmpty()) {
                sendMessage("HISTORY|--- LỊCH SỬ CHAT GẦN ĐÂY ---");
                for (String h : history) {
                    sendMessage("HISTORY|" + h);
                }
                sendMessage("HISTORY|----------------------------");
            }

            broadcast("SERVER|[" + username + " (" + role + ")] đã tham gia phòng chat!");
            broadcastOnlineList();
        }

        private void handlePublicChat(String rawPacket) {
            if (isMuted) {
                sendMessage("ERROR|Bạn đang bị Admin cấm chat (Muted)!");
                return;
            }
            String[] parts = rawPacket.split("\\|", 2);
            if (parts.length < 2 || parts[1].trim().isEmpty()) return;

            String time = LocalDateTime.now().format(TIME_FORMAT);
            String formattedMsg = String.format("[%s] [%s-%s]: %s", time, role, username, parts[1]);

            saveHistory(formattedMsg);
            broadcast("CHAT|" + formattedMsg);
        }

        private void handlePrivateChat(String rawPacket) {
            if (isMuted) {
                sendMessage("ERROR|Bạn đang bị cấm chat!");
                return;
            }
            String[] parts = rawPacket.split("\\|", 3);
            if (parts.length < 3) {
                sendMessage("ERROR|Cú pháp: MSG_PRIVATE|targetUser|nội dung");
                return;
            }
            String targetUser = parts[1].trim().toLowerCase();
            String content = parts[2];

            ClientHandler targetHandler = onlineClients.get(targetUser);
            if (targetHandler == null) {
                sendMessage("ERROR|Người dùng '" + targetUser + "' không online!");
            } else {
                String time = LocalDateTime.now().format(TIME_FORMAT);
                targetHandler.sendMessage("PRIVATE|[" + time + "] [Mật từ " + username + "]: " + content);
                this.sendMessage("PRIVATE|[" + time + "] [Mật tới " + targetUser + "]: " + content);
            }
        }

        private void handleGetFullHistory() {
            List<String> history = getAllHistory();
            sendMessage("FULL_HISTORY_START|" + history.size());
            for (String line : history) {
                sendMessage("FULL_HISTORY_ITEM|" + line);
            }
            sendMessage("FULL_HISTORY_END");
        }

        private void handleAdminClearHistory() {
            if (!this.role.equals("ADMIN")) {
                sendMessage("ERROR|Chỉ ADMIN mới được xóa lịch sử.");
                return;
            }
            clearHistory();
            broadcast("SERVER|👑 Admin [" + username + "] đã xóa toàn bộ lịch sử phòng chat.");
        }

        private void handleAdminAnnounce(String rawPacket) {
            if (!this.role.equals("ADMIN")) {
                sendMessage("ERROR|Chỉ ADMIN mới được gửi thông báo khẩn.");
                return;
            }
            String[] parts = rawPacket.split("\\|", 2);
            if (parts.length > 1) {
                String announcement = parts[1].trim();
                broadcast("ANNOUNCEMENT|📢 [THÔNG BÁO TỪ ADMIN " + username + "]: " + announcement);
            }
        }

        private void handleAdminKick(String rawPacket) {
            if (!this.role.equals("ADMIN")) {
                sendMessage("ERROR|Chỉ ADMIN mới được dùng lệnh KICK.");
                return;
            }
            String[] parts = rawPacket.split("\\|", 2);
            if (parts.length < 2) return;
            String targetUser = parts[1].trim().toLowerCase();

            ClientHandler target = onlineClients.get(targetUser);
            if (target == null) {
                sendMessage("ERROR|Không tìm thấy user: " + targetUser);
                return;
            }
            target.sendMessage("KICKED|Bạn đã bị Admin [" + username + "] đuổi khỏi phòng chat!");
            broadcast("SERVER|Admin đã kick [" + targetUser + "] ra khỏi phòng.");
            onlineClients.remove(targetUser);
            broadcastOnlineList();
            target.forceClose();
        }

        private void handleAdminMute(String rawPacket) {
            if (!this.role.equals("ADMIN")) {
                sendMessage("ERROR|Chỉ ADMIN mới được dùng lệnh MUTE.");
                return;
            }
            String[] parts = rawPacket.split("\\|", 3);
            if (parts.length < 3) return;
            String targetUser = parts[1].trim().toLowerCase();
            boolean muteStatus = Boolean.parseBoolean(parts[2].trim());

            ClientHandler target = onlineClients.get(targetUser);
            if (target == null) {
                sendMessage("ERROR|Không tìm thấy user: " + targetUser);
                return;
            }
            target.isMuted = muteStatus;
            String action = muteStatus ? "cấm chat (Mute)" : "mở khóa chat (Unmute)";
            broadcast("SERVER|Admin đã " + action + " đối với [" + targetUser + "].");
        }

        public synchronized void sendMessage(String msg) {
            if (isWebSocket) {
                sendWebSocketFrame(msg);
            } else if (out != null) {
                out.println(msg);
            }
        }

        public void forceClose() {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }

        private void disconnect() {
            if (username != null && onlineClients.remove(username, this)) {
                broadcast("SERVER|[" + username + "] đã thoát khỏi phòng chat.");
                broadcastOnlineList();
            }
            forceClose();
        }

        private void sendOnlineList() {
            StringBuilder sb = new StringBuilder("ONLINE_LIST|");
            boolean first = true;
            for (Map.Entry<String, ClientHandler> entry : onlineClients.entrySet()) {
                if (!first) sb.append(",");
                sb.append(entry.getKey()).append(":").append(entry.getValue().role);
                first = false;
            }
            sendMessage(sb.toString());
        }
    }
}