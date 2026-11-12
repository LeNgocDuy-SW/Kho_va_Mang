package server;

import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import server.db.GroupDAO;
import server.db.MessageDAO;
import server.db.UserDAO;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ClientHandler: Xử lý giao tiếp mạng cho từng Client.
 * Hỗ trợ Tạo nhóm mới, Xác thực mã nhóm, Chat nhóm và Chat riêng tư.
 */
public class ClientHandler implements Runnable {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    private final Socket socket;
    private final RoomManager roomManager;
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final GroupDAO groupDAO;

    private BufferedReader in;
    private PrintWriter out;

    private String username;
    private String role = Protocol.ROLE_USER;
    private boolean authenticated = false;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, RoomManager roomManager, UserDAO userDAO, MessageDAO messageDAO) {
        this.socket = socket;
        this.roomManager = roomManager;
        this.userDAO = userDAO;
        this.messageDAO = messageDAO;
        this.groupDAO = roomManager.getGroupDAO();
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public boolean isAuthenticated() { return authenticated; }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress() != null ? socket.getRemoteSocketAddress().toString() : "Unknown";
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                processPacket(line);
            }
        } catch (IOException e) {
            // Client ngắt kết nối
        } finally {
            closeConnection();
        }
    }

    private void processPacket(String packet) {
        String[] parts = packet.split(Protocol.DELIMITER_REGEX, 2);
        String command = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case Protocol.CMD_LOGIN:
                handleLogin(payload);
                break;
            case Protocol.CMD_REGISTER:
                handleRegister(payload);
                break;
            case Protocol.CMD_CREATE_GROUP:
                handleCreateGroup(payload);
                break;
            case Protocol.CMD_JOIN_GROUP:
                handleJoinGroup(payload);
                break;
            case Protocol.CMD_GET_GROUPS:
                sendGroupList();
                break;
            case Protocol.CMD_MSG_GROUP:
                handleGroupMessage(payload);
                break;
            case Protocol.CMD_MSG_ALL:
                handlePublicMessage(payload);
                break;
            case Protocol.CMD_MSG_PRIVATE:
                handlePrivateMessage(payload);
                break;
            case Protocol.CMD_ADMIN:
                handleAdminCommand(payload);
                break;
            case Protocol.CMD_CHANGE_PASS:
                handleChangePassword(payload);
                break;
            case Protocol.CMD_GET_USERS:
                roomManager.broadcastUserList();
                break;
            case Protocol.CMD_LOGOUT:
                closeConnection();
                break;
            default:
                sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Lệnh không hợp lệ: " + command);
                break;
        }
    }

    private void handleLogin(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng nhập đầy đủ tài khoản và mật khẩu.");
            return;
        }

        String user = parts[0].trim();
        String pass = parts[1].trim();

        User dbUser = userDAO.login(user, pass);
        if (dbUser == null) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Sai tên đăng nhập hoặc mật khẩu.");
            return;
        }

        if (dbUser.isBanned()) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tài khoản của bạn đã bị khóa (BANNED).");
            return;
        }

        if (roomManager.isUserOnline(user)) {
            sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tài khoản đang online ở một nơi khác.");
            return;
        }

        this.username = dbUser.getUsername();
        this.role = dbUser.getRole();
        this.authenticated = true;

        sendPacket(Protocol.RES_LOGIN + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + this.role + Protocol.DELIMITER + this.username);
        roomManager.addClient(this.username, this);

        // Nạp lịch sử phòng mặc định
        sendChatHistory("Friends & Family");
        sendGroupList();
    }

    private void handleRegister(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng điền đủ thông tin.");
            return;
        }

        String user = parts[0].trim();
        String pass = parts[1].trim();
        String reqRole = parts.length > 2 ? parts[2].trim() : Protocol.ROLE_USER;

        if (user.length() < 3 || pass.length() < 3) {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tài khoản và mật khẩu phải từ 3 ký tự trở lên.");
            return;
        }

        boolean ok = userDAO.register(user, pass, reqRole);
        if (ok) {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + "Đăng ký thành công! Hãy đăng nhập.");
        } else {
            sendPacket(Protocol.RES_REGISTER + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tên đăng nhập đã tồn tại trên hệ thống.");
        }
    }

    /**
     * Tạo nhóm mới có mã bảo vệ
     */
    private void handleCreateGroup(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng nhập tên nhóm và mã nhóm.");
            return;
        }

        String groupName = parts[0].trim();
        String groupCode = parts[1].trim();

        if (groupName.isEmpty() || groupCode.isEmpty()) {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tên nhóm và mã bảo vệ không được để trống.");
            return;
        }

        boolean ok = groupDAO.createGroup(groupName, groupCode, this.username);
        if (ok) {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + groupName + Protocol.DELIMITER + groupCode);
            roomManager.broadcastGroupList();
            roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🎉 " + this.username + " vừa tạo nhóm mới: [" + groupName + "]!");
        } else {
            sendPacket(Protocol.RES_CREATE_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Tên nhóm [" + groupName + "] đã tồn tại.");
        }
    }

    /**
     * Tham gia nhóm khi nhập mã nhóm
     */
    private void handleJoinGroup(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) {
            sendPacket(Protocol.RES_JOIN_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Vui lòng nhập mã nhóm.");
            return;
        }

        String groupName = parts[0].trim();
        String inputCode = parts[1].trim();

        boolean valid = groupDAO.verifyGroupCode(groupName, inputCode);
        if (valid) {
            groupDAO.addMember(groupName, this.username);
            sendPacket(Protocol.RES_JOIN_GROUP + Protocol.DELIMITER + Protocol.SUCCESS + Protocol.DELIMITER + groupName);
            sendChatHistory(groupName);
            roomManager.broadcastGroupList();
        } else {
            sendPacket(Protocol.RES_JOIN_GROUP + Protocol.DELIMITER + Protocol.FAILED + Protocol.DELIMITER + "Mã nhóm không chính xác! Vui lòng thử lại.");
        }
    }

    private void handleGroupMessage(String payload) {
        if (!authenticated) return;
        if (roomManager.isUserMuted(this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Bạn đang bị cấm chat! Không thể gửi tin.");
            return;
        }

        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) return;

        String groupName = parts[0].trim();
        String content = parts[1].trim();
        String time = LocalDateTime.now().format(TIME_FMT);

        MessagePacket msg = new MessagePacket(MessagePacket.TYPE_PUBLIC, this.username, this.role, "ALL", groupName, content);
        msg.setTimestamp(time);
        messageDAO.saveMessage(msg);

        // NEW_MSG|groupName|sender|role|timestamp|content
        String broadcastPacket = Protocol.RES_NEW_MSG + Protocol.DELIMITER
                + groupName + Protocol.DELIMITER
                + this.username + Protocol.DELIMITER
                + this.role + Protocol.DELIMITER
                + time + Protocol.DELIMITER
                + content;

        roomManager.broadcastToGroup(groupName, broadcastPacket);
    }

    private void handlePublicMessage(String content) {
        handleGroupMessage("Friends & Family" + Protocol.DELIMITER + content);
    }

    private void handlePrivateMessage(String payload) {
        if (!authenticated) return;
        if (roomManager.isUserMuted(this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Bạn đang bị cấm chat!");
            return;
        }

        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) return;

        String target = parts[0].trim();
        String content = parts[1].trim();

        if (target.equalsIgnoreCase(this.username)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Không thể tự gửi tin nhắn riêng cho chính mình.");
            return;
        }

        String time = LocalDateTime.now().format(TIME_FMT);
        MessagePacket msg = new MessagePacket(MessagePacket.TYPE_PRIVATE, this.username, this.role, target, content);
        msg.setTimestamp(time);
        messageDAO.saveMessage(msg);

        String packet = Protocol.RES_NEW_PRIVATE_MSG + Protocol.DELIMITER
                + this.username + Protocol.DELIMITER
                + target + Protocol.DELIMITER
                + time + Protocol.DELIMITER
                + content;

        roomManager.sendPrivate(this.username, target, packet, packet);
    }

    private void handleAdminCommand(String payload) {
        if (!authenticated || !Protocol.ROLE_ADMIN.equalsIgnoreCase(this.role)) {
            sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "LỖI: Bạn không có quyền quản trị viên!");
            return;
        }

        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        if (parts.length < 1) return;

        String action = parts[0].toUpperCase();
        String target = parts.length > 1 ? parts[1].trim() : "";
        String extra = parts.length > 2 ? parts[2].trim() : "";

        switch (action) {
            case Protocol.ACT_KICK:
                if (!target.isEmpty()) roomManager.kickUser(target, extra.isEmpty() ? "Bị Admin đuổi" : extra);
                break;
            case Protocol.ACT_MUTE:
                if (!target.isEmpty()) {
                    int mins = 5;
                    try { if (!extra.isEmpty()) mins = Integer.parseInt(extra); } catch (Exception ignored) {}
                    roomManager.muteUser(target, mins);
                }
                break;
            case Protocol.ACT_UNMUTE:
                if (!target.isEmpty()) roomManager.unmuteUser(target);
                break;
            case Protocol.ACT_BAN:
                if (!target.isEmpty()) {
                    userDAO.updateStatus(target, Protocol.STATUS_BANNED);
                    roomManager.kickUser(target, "Tài khoản bị khóa vĩnh viễn.");
                }
                break;
            case Protocol.ACT_ALERT:
                String alertMsg = extra.isEmpty() ? target : (target + " - " + extra);
                roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "📢 [THÔNG BÁO TỪ ADMIN]: " + alertMsg);
                break;
            case Protocol.ACT_CLEAR_HISTORY:
                messageDAO.clearHistory();
                roomManager.broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "🧹 Admin đã xóa toàn bộ lịch sử phòng chat.");
                break;
            default:
                break;
        }
    }

    private void handleChangePassword(String payload) {
        if (!authenticated) return;
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        if (parts.length < 2) return;
        boolean ok = userDAO.changePassword(this.username, parts[0], parts[1]);
        sendPacket(Protocol.RES_CHANGE_PASS + Protocol.DELIMITER + (ok ? Protocol.SUCCESS : Protocol.FAILED) + Protocol.DELIMITER + (ok ? "Đổi mật khẩu thành công!" : "Mật khẩu cũ không đúng."));
    }

    private void sendChatHistory(String groupName) {
        List<MessagePacket> history = messageDAO.getGroupMessages(groupName, 30);
        if (history.isEmpty()) return;

        StringBuilder sb = new StringBuilder(Protocol.RES_HISTORY).append(Protocol.DELIMITER);
        sb.append(groupName).append(Protocol.DELIMITER);
        sb.append(history.size()).append(Protocol.DELIMITER);
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(";;");
            sb.append(history.get(i).encodeForHistory());
        }
        sendPacket(sb.toString());
    }

    private void sendGroupList() {
        List<ChatGroup> list = groupDAO.getAllGroups();
        StringBuilder sb = new StringBuilder(Protocol.RES_GROUP_LIST).append(Protocol.DELIMITER);
        boolean first = true;
        for (ChatGroup g : list) {
            if (!first) sb.append(",");
            sb.append(g.getGroupName()).append(":").append(g.getMemberCount());
            first = false;
        }
        sendPacket(sb.toString());
    }

    public synchronized void sendPacket(String packet) {
        if (out != null && !socket.isClosed()) {
            out.println(packet);
        }
    }

    public void closeConnection() {
        running = false;
        if (authenticated && username != null) {
            roomManager.removeClient(username, true);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        try {
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException ignored) {}
    }
}
