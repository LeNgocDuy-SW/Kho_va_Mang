package server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import server.RoomManager;
import server.db.GroupDAO;
import server.db.MessageDAO;
import server.db.UserDAO;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * WebChatServer: Máy chủ HTTP & Server-Sent Events (SSE) tích hợp trong Java.
 * Cho phép người dùng truy cập trực tiếp bằng trình duyệt Web (Chrome, Safari, Edge, Mobile)
 * và trò chuyện thời gian thực (Real-time) đồng bộ 100% với các Client Desktop Java Swing!
 */
public class WebChatServer implements RoomManager.WebEventListener {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int webPort;
    private final RoomManager roomManager;
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;

    private HttpServer server;
    private volatile boolean running = false;

    // Quản lý các luồng SSE đang kết nối theo từng username
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<OutputStream>> sseStreams = new ConcurrentHashMap<>();

    public WebChatServer(int webPort, RoomManager roomManager, UserDAO userDAO, MessageDAO messageDAO) {
        this.webPort = webPort > 0 ? webPort : 8080;
        this.roomManager = roomManager;
        this.userDAO = userDAO;
        this.messageDAO = messageDAO;
        this.groupDAO = roomManager.getGroupDAO();

        // Đăng ký nhận sự kiện từ RoomManager để đẩy sang Web SSE
        this.roomManager.setWebListener(this);
    }

    public synchronized boolean start() {
        if (running) return true;

        try {
            server = HttpServer.create(new InetSocketAddress(webPort), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // 1. Phục vụ các API JSON
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/register", new RegisterHandler());
            server.createContext("/api/groups", new GroupsHandler());
            server.createContext("/api/create_group", new CreateGroupHandler());
            server.createContext("/api/join_group", new JoinGroupHandler());
            server.createContext("/api/send", new SendHandler());
            server.createContext("/api/history", new HistoryHandler());
            server.createContext("/api/users", new UsersHandler());
            server.createContext("/api/events", new SseHandler());

            // 2. Phục vụ giao diện tĩnh (HTML / CSS / JS trong thư mục web/)
            server.createContext("/", new StaticFileHandler());

            server.start();
            running = true;
            roomManager.log("[WEB SERVER] Cổng giao diện Web đang hoạt động tại http://localhost:" + webPort + " (hoặc IP mạng LAN)");
            return true;
        } catch (IOException e) {
            roomManager.log("[WEB SERVER] Không thể khởi động Web Server tại cổng " + webPort + ": " + e.getMessage());
            running = false;
            return false;
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        // Đóng tất cả các luồng SSE
        for (CopyOnWriteArrayList<OutputStream> list : sseStreams.values()) {
            for (OutputStream os : list) {
                try { os.close(); } catch (Exception ignored) {}
            }
        }
        sseStreams.clear();

        if (server != null) {
            server.stop(1);
        }
        roomManager.log("[WEB SERVER] Đã dừng máy chủ Web.");
    }

    public int getWebPort() {
        return webPort;
    }

    // =========================================================================
    // TRIỂN KHAI WebEventListener ĐỂ ĐỒNG BỘ TIN NHẮN TỪ DESKTOP SANG WEB
    // =========================================================================

    @Override
    public void onWebBroadcast(String groupName, String packet) {
        String[] parts = packet.split(Protocol.DELIMITER_REGEX);
        String header = parts[0];

        if (Protocol.RES_NEW_MSG.equals(header)) {
            // NEW_MSG|groupName|sender|role|timestamp|content
            if (parts.length >= 6) {
                String g = parts[1];
                String s = parts[2];
                String r = parts[3];
                String t = parts[4];
                String c = parts[5];
                String json = String.format("{\"type\":\"NEW_MSG\",\"groupName\":\"%s\",\"sender\":\"%s\",\"role\":\"%s\",\"timestamp\":\"%s\",\"content\":\"%s\"}",
                        escapeJson(g), escapeJson(s), escapeJson(r), escapeJson(t), escapeJson(c));
                broadcastSse(json);
            }
        } else if (Protocol.RES_SYSTEM_MSG.equals(header)) {
            String content = parts.length > 1 ? parts[1] : "";
            String json = String.format("{\"type\":\"SYSTEM_MSG\",\"content\":\"%s\"}", escapeJson(content));
            broadcastSse(json);
        } else if (Protocol.RES_GROUP_LIST.equals(header)) {
            onGroupListUpdated(groupDAO.getAllGroups());
        }
    }

    @Override
    public boolean onWebPrivate(String sender, String receiver, String packet, String echoPacket) {
        String[] parts = packet.split(Protocol.DELIMITER_REGEX);
        if (parts.length >= 5) {
            String s = parts[1];
            String target = parts[2];
            String t = parts[3];
            String c = parts[4];
            String json = String.format("{\"type\":\"NEW_PRIVATE_MSG\",\"sender\":\"%s\",\"target\":\"%s\",\"timestamp\":\"%s\",\"content\":\"%s\"}",
                    escapeJson(s), escapeJson(target), escapeJson(t), escapeJson(c));
            sendSseToUser(target, json);
            sendSseToUser(s, json);
            return true;
        }
        return false;
    }

    @Override
    public void onUserListUpdated(List<User> users) {
        StringBuilder sb = new StringBuilder("{\"type\":\"USER_LIST\",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"username\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}",
                    escapeJson(u.getUsername()), escapeJson(u.getRole()), escapeJson(u.getStatus())));
        }
        sb.append("]}");
        broadcastSse(sb.toString());
    }

    @Override
    public void onGroupListUpdated(List<ChatGroup> groups) {
        StringBuilder sb = new StringBuilder("{\"type\":\"GROUP_LIST\",\"groups\":[");
        for (int i = 0; i < groups.size(); i++) {
            ChatGroup g = groups.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format("{\"name\":\"%s\",\"creator\":\"%s\",\"memberCount\":%d}",
                    escapeJson(g.getGroupName()), escapeJson(g.getCreator()), g.getMemberCount()));
        }
        sb.append("]}");
        broadcastSse(sb.toString());
    }

    // =========================================================================
    // HỖ TRỢ PHÁT SỰ KIỆN SERVER-SENT EVENTS (SSE)
    // =========================================================================

    private void broadcastSse(String json) {
        byte[] payload = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<String, CopyOnWriteArrayList<OutputStream>> entry : sseStreams.entrySet()) {
            for (OutputStream os : entry.getValue()) {
                try {
                    synchronized (os) {
                        os.write(payload);
                        os.flush();
                    }
                } catch (IOException e) {
                    entry.getValue().remove(os);
                }
            }
        }
    }

    private void sendSseToUser(String username, String json) {
        CopyOnWriteArrayList<OutputStream> list = sseStreams.get(username);
        if (list == null) return;
        byte[] payload = ("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (OutputStream os : list) {
            try {
                synchronized (os) {
                    os.write(payload);
                    os.flush();
                }
            } catch (IOException e) {
                list.remove(os);
            }
        }
    }

    // =========================================================================
    // HTTP HANDLERS (API JSON & TĨNH)
    // =========================================================================

    private class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String user = query.get("user");
            if (user == null || user.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Missing user query parameter\"}");
                return;
            }
            user = user.trim();

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            sseStreams.computeIfAbsent(user, k -> new CopyOnWriteArrayList<>()).add(os);

            // Gửi lời chào kết nối ban đầu
            byte[] init = ("data: {\"type\":\"CONNECTED\",\"user\":\"" + escapeJson(user) + "\"}\n\n").getBytes(StandardCharsets.UTF_8);
            try {
                synchronized (os) {
                    os.write(init);
                    os.flush();
                }
            } catch (IOException ignored) {}

            // Đồng bộ danh sách online và nhóm hiện thời cho client mới
            onUserListUpdated(roomManager.getOnlineUsersList());
            onGroupListUpdated(groupDAO.getAllGroups());

            // GIỮ LUỒNG SSE SỐNG LIÊN TỤC VỚI HEARTBEAT ĐỊNH KỲ
            try {
                while (running) {
                    Thread.sleep(15000);
                    synchronized (os) {
                        os.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }
            } catch (Exception ignored) {
                // Client ngắt kết nối (tắt tab hoặc mất mạng)
            } finally {
                CopyOnWriteArrayList<OutputStream> list = sseStreams.get(user);
                if (list != null) {
                    list.remove(os);
                    if (list.isEmpty()) {
                        sseStreams.remove(user);
                        roomManager.removeWebClient(user);
                    }
                }
                try { os.close(); } catch (Exception ignored) {}
                try { exchange.close(); } catch (Exception ignored) {}
            }
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonOrForm(readBody(exchange));
            String user = body.getOrDefault("username", "").trim();
            String pass = body.getOrDefault("password", "").trim();

            User dbUser = userDAO.login(user, pass);
            if (dbUser == null) {
                sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Sai tên đăng nhập hoặc mật khẩu!\"}");
                return;
            }
            if (dbUser.isBanned()) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Tài khoản của bạn đã bị khóa!\"}");
                return;
            }

            // Ghi nhận web client online
            roomManager.addWebClient(dbUser.getUsername(), dbUser.getRole());

            sendJsonResponse(exchange, 200, String.format("{\"success\":true,\"username\":\"%s\",\"role\":\"%s\"}",
                    escapeJson(dbUser.getUsername()), escapeJson(dbUser.getRole())));
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonOrForm(readBody(exchange));
            String user = body.getOrDefault("username", "").trim();
            String pass = body.getOrDefault("password", "").trim();
            String role = body.getOrDefault("role", Protocol.ROLE_USER).trim();

            if (user.isEmpty() || pass.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Vui lòng điền đầy đủ thông tin.\"}");
                return;
            }

            boolean ok = userDAO.register(user, pass, role);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Đăng ký tài khoản thành công!\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Tên tài khoản đã tồn tại!\"}");
            }
        }
    }

    private class GroupsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            List<ChatGroup> list = groupDAO.getAllGroups();
            StringBuilder sb = new StringBuilder("{\"success\":true,\"groups\":[");
            for (int i = 0; i < list.size(); i++) {
                ChatGroup g = list.get(i);
                if (i > 0) sb.append(",");
                sb.append(String.format("{\"name\":\"%s\",\"creator\":\"%s\",\"memberCount\":%d}",
                        escapeJson(g.getGroupName()), escapeJson(g.getCreator()), g.getMemberCount()));
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class CreateGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonOrForm(readBody(exchange));
            String groupName = body.getOrDefault("groupName", "").trim();
            String groupCode = body.getOrDefault("groupCode", "").trim();
            String creator = body.getOrDefault("creator", "web_user").trim();

            if (groupName.isEmpty() || groupCode.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Tên nhóm và mã PIN không được để trống.\"}");
                return;
            }

            boolean ok = groupDAO.createGroup(groupName, groupCode, creator);
            if (ok) {
                roomManager.broadcastGroupList();
                roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🎉 " + creator + " vừa tạo nhóm mới: [" + groupName + "]!");
                sendJsonResponse(exchange, 200, String.format("{\"success\":true,\"groupName\":\"%s\",\"groupCode\":\"%s\"}",
                        escapeJson(groupName), escapeJson(groupCode)));
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Tên nhóm đã tồn tại!\"}");
            }
        }
    }

    private class JoinGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonOrForm(readBody(exchange));
            String groupName = body.getOrDefault("groupName", "").trim();
            String groupCode = body.getOrDefault("groupCode", "").trim();
            String username = body.getOrDefault("username", "").trim();

            boolean valid = groupDAO.verifyGroupCode(groupName, groupCode);
            if (valid) {
                groupDAO.addMember(groupName, username);
                roomManager.broadcastGroupList();
                sendJsonResponse(exchange, 200, String.format("{\"success\":true,\"groupName\":\"%s\"}", escapeJson(groupName)));
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Mã bảo vệ nhóm không chính xác!\"}");
            }
        }
    }

    private class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            Map<String, String> body = parseJsonOrForm(readBody(exchange));
            String sender = body.getOrDefault("sender", "").trim();
            String role = body.getOrDefault("role", "USER").trim();
            String groupName = body.getOrDefault("groupName", "Friends & Family").trim();
            String content = body.getOrDefault("content", "").trim();
            String isPrivateStr = body.getOrDefault("isPrivate", "false");
            String targetUser = body.getOrDefault("targetUser", "").trim();

            if (content.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Nội dung tin nhắn trống.\"}");
                return;
            }

            if (roomManager.isUserMuted(sender)) {
                sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Bạn đang bị cấm chat!\"}");
                return;
            }

            String time = LocalDateTime.now().format(TIME_FMT);
            boolean isPrivate = "true".equalsIgnoreCase(isPrivateStr);

            if (isPrivate) {
                MessagePacket msg = new MessagePacket(MessagePacket.TYPE_PRIVATE, sender, role, targetUser, content);
                msg.setTimestamp(time);
                messageDAO.saveMessage(msg);

                String packet = Protocol.RES_NEW_PRIVATE_MSG + Protocol.DELIMITER + sender + Protocol.DELIMITER + targetUser + Protocol.DELIMITER + time + Protocol.DELIMITER + content;
                roomManager.sendPrivate(sender, targetUser, packet, packet);
            } else {
                MessagePacket msg = new MessagePacket(MessagePacket.TYPE_PUBLIC, sender, role, "ALL", groupName, content);
                msg.setTimestamp(time);
                messageDAO.saveMessage(msg);

                String broadcastPacket = Protocol.RES_NEW_MSG + Protocol.DELIMITER
                        + groupName + Protocol.DELIMITER
                        + sender + Protocol.DELIMITER
                        + role + Protocol.DELIMITER
                        + time + Protocol.DELIMITER
                        + content;
                roomManager.broadcastToGroup(groupName, broadcastPacket);
            }

            sendJsonResponse(exchange, 200, String.format("{\"success\":true,\"time\":\"%s\"}", time));
        }
    }

    private class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String group = query.getOrDefault("group", "Friends & Family").trim();

            List<MessagePacket> list = messageDAO.getGroupMessages(group, 50);
            StringBuilder sb = new StringBuilder("{\"success\":true,\"messages\":[");
            for (int i = 0; i < list.size(); i++) {
                MessagePacket m = list.get(i);
                if (i > 0) sb.append(",");
                sb.append(String.format("{\"sender\":\"%s\",\"role\":\"%s\",\"time\":\"%s\",\"content\":\"%s\"}",
                        escapeJson(m.getSender()), escapeJson(m.getSenderRole()), escapeJson(m.getTimestamp()), escapeJson(m.getContent())));
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            List<User> list = roomManager.getOnlineUsersList();
            StringBuilder sb = new StringBuilder("{\"success\":true,\"users\":[");
            for (int i = 0; i < list.size(); i++) {
                User u = list.get(i);
                if (i > 0) sb.append(",");
                sb.append(String.format("{\"username\":\"%s\",\"role\":\"%s\",\"status\":\"%s\"}",
                        escapeJson(u.getUsername()), escapeJson(u.getRole()), escapeJson(u.getStatus())));
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                file = new File("web/index.html");
            }

            if (!file.exists()) {
                String notFound = "<h1>404 Not Found</h1><p>Vui lòng đảm bảo thư mục 'web/' có chứa index.html.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                byte[] b = notFound.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, b.length);
                exchange.getResponseBody().write(b);
                exchange.getResponseBody().close();
                return;
            }

            String mime = "text/html; charset=UTF-8";
            if (path.endsWith(".css")) mime = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) mime = "application/json; charset=UTF-8";
            else if (path.endsWith(".png")) mime = "image/png";
            else if (path.endsWith(".svg")) mime = "image/svg+xml";

            exchange.getResponseHeaders().set("Content-Type", mime);
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private static void setCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        setCors(exchange);
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, b.length);
        OutputStream os = exchange.getResponseBody();
        os.write(b);
        os.close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k, v);
        }
        return map;
    }

    private static Map<String, String> parseJsonOrForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;
        String trimmed = body.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            int i = 1;
            int len = trimmed.length() - 1;
            while (i < len) {
                int keyStart = trimmed.indexOf('"', i);
                if (keyStart == -1 || keyStart >= len) break;
                int keyEnd = trimmed.indexOf('"', keyStart + 1);
                if (keyEnd == -1) break;
                String key = trimmed.substring(keyStart + 1, keyEnd);

                int colon = trimmed.indexOf(':', keyEnd);
                if (colon == -1) break;

                int valStart = colon + 1;
                while (valStart < len && Character.isWhitespace(trimmed.charAt(valStart))) valStart++;
                if (valStart >= len) break;

                String val = "";
                if (trimmed.charAt(valStart) == '"') {
                    StringBuilder sb = new StringBuilder();
                    int j = valStart + 1;
                    boolean escaped = false;
                    while (j < len) {
                        char c = trimmed.charAt(j);
                        if (escaped) {
                            if (c == '"') sb.append('"');
                            else if (c == '\\') sb.append('\\');
                            else if (c == 'n') sb.append('\n');
                            else if (c == 'r') sb.append('\r');
                            else if (c == 't') sb.append('\t');
                            else sb.append(c);
                            escaped = false;
                        } else if (c == '\\') {
                            escaped = true;
                        } else if (c == '"') {
                            j++;
                            break;
                        } else {
                            sb.append(c);
                        }
                        j++;
                    }
                    val = sb.toString();
                    i = j;
                } else {
                    int nextComma = trimmed.indexOf(',', valStart);
                    if (nextComma == -1) nextComma = len;
                    val = trimmed.substring(valStart, nextComma).trim();
                    i = nextComma + 1;
                }
                map.put(key, val);

                int comma = trimmed.indexOf(',', i);
                if (comma != -1 && comma < len) {
                    i = comma + 1;
                } else {
                    break;
                }
            }
            if (!map.isEmpty()) return map;
        }

        return parseQuery(body);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
