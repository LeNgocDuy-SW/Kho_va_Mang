package server;

import common.ChatGroup;
import common.Protocol;
import common.User;
import server.db.GroupDAO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RoomManager: Quản lý danh sách kết nối Client, nhóm chat (Groups),
 * định tuyến tin nhắn (Broadcast, Multicast theo nhóm, Unicast riêng tư).
 */
public class RoomManager {
    private final ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> webClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> mutedUsers = new ConcurrentHashMap<>();
    private final GroupDAO groupDAO = new GroupDAO();

    public interface WebEventListener {
        void onWebBroadcast(String groupName, String packet);
        boolean onWebPrivate(String sender, String receiver, String packet, String echoPacket);
        void onUserListUpdated(List<User> users);
        void onGroupListUpdated(List<ChatGroup> groups);
    }

    private ServerEventListener listener;
    private WebEventListener webListener;

    public void setListener(ServerEventListener listener) {
        this.listener = listener;
    }

    public void setWebListener(WebEventListener webListener) {
        this.webListener = webListener;
    }

    public void log(String msg) {
        if (listener != null) {
            listener.onLog(msg);
        } else {
            System.out.println(msg);
        }
    }

    public GroupDAO getGroupDAO() {
        return groupDAO;
    }

    public synchronized boolean addClient(String username, ClientHandler handler) {
        if (onlineClients.containsKey(username)) {
            return false;
        }

        onlineClients.put(username, handler);
        log("[KẾT NỐI] " + username + " (" + handler.getRole() + ") từ " + handler.getRemoteAddress());

        if (listener != null) {
            listener.onClientConnected(username, handler.getRole(), handler.getRemoteAddress());
            listener.onOnlineListUpdated(getOnlineUsersList());
        }

        broadcastUserList();
        broadcastGroupList();
        return true;
    }

    public synchronized void removeClient(String username, boolean notifyRoom) {
        ClientHandler handler = onlineClients.remove(username);
        if (handler != null) {
            log("[NGẮT KẾT NỐI] " + username + " đã rời phòng.");

            if (listener != null) {
                listener.onClientDisconnected(username);
                listener.onOnlineListUpdated(getOnlineUsersList());
            }

            if (notifyRoom) {
                broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "[Hệ thống] " + username + " đã rời khỏi hệ thống.");
                broadcastUserList();
            }
        }
    }

    public synchronized boolean addWebClient(String username, String role) {
        if (onlineClients.containsKey(username) || webClients.containsKey(username)) {
            return false;
        }
        webClients.put(username, new User(username, role, Protocol.STATUS_ACTIVE));
        log("[KẾT NỐI WEB] " + username + " (" + role + ") từ Trình duyệt Web");

        if (listener != null) {
            listener.onClientConnected(username, role, "Web-Browser");
            listener.onOnlineListUpdated(getOnlineUsersList());
        }

        broadcastUserList();
        broadcastGroupList();
        return true;
    }

    public synchronized void removeWebClient(String username) {
        User u = webClients.remove(username);
        if (u != null) {
            log("[NGẮT KẾT NỐI WEB] " + username + " đã đóng trình duyệt.");
            if (listener != null) {
                listener.onClientDisconnected(username);
                listener.onOnlineListUpdated(getOnlineUsersList());
            }
            broadcastUserList();
        }
    }

    /**
     * Broadcast tới toàn bộ Client (cả Desktop TCP và Web HTTP/SSE)
     */
    public void broadcast(String packet) {
        for (ClientHandler handler : onlineClients.values()) {
            handler.sendPacket(packet);
        }
        if (webListener != null) {
            webListener.onWebBroadcast("ALL", packet);
        }
        if (listener != null) {
            listener.onMessageCountIncremented();
        }
    }

    /**
     * Multicast gửi tin nhắn tới tất cả thành viên trong nhóm cụ thể
     */
    public void broadcastToGroup(String groupName, String packet) {
        for (ClientHandler handler : onlineClients.values()) {
            handler.sendPacket(packet);
        }
        if (webListener != null) {
            webListener.onWebBroadcast(groupName, packet);
        }
        if (listener != null) {
            listener.onMessageCountIncremented();
        }
    }

    /**
     * Unicast gửi tin nhắn riêng tư 1-1
     */
    public boolean sendPrivate(String sender, String receiver, String packet, String echoPacket) {
        ClientHandler target = onlineClients.get(receiver);
        ClientHandler senderHandler = onlineClients.get(sender);
        boolean sent = false;

        if (target != null) {
            target.sendPacket(packet);
            sent = true;
        }

        if (webListener != null && webClients.containsKey(receiver)) {
            if (webListener.onWebPrivate(sender, receiver, packet, echoPacket)) {
                sent = true;
            }
        }

        if (sent) {
            if (senderHandler != null && echoPacket != null) {
                senderHandler.sendPacket(echoPacket);
            }
            if (listener != null) {
                listener.onMessageCountIncremented();
            }
            return true;
        } else {
            if (senderHandler != null) {
                senderHandler.sendPacket(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "Người dùng [" + receiver + "] hiện không online.");
            }
            return false;
        }
    }

    public void broadcastUserList() {
        StringBuilder sb = new StringBuilder(Protocol.RES_USER_LIST).append(Protocol.DELIMITER);
        boolean first = true;
        for (ClientHandler h : onlineClients.values()) {
            if (!first) sb.append(",");
            String status = isUserMuted(h.getUsername()) ? Protocol.STATUS_MUTED : Protocol.STATUS_ACTIVE;
            sb.append(h.getUsername()).append(":").append(h.getRole()).append(":").append(status);
            first = false;
        }
        for (User u : webClients.values()) {
            if (!first) sb.append(",");
            String status = isUserMuted(u.getUsername()) ? Protocol.STATUS_MUTED : Protocol.STATUS_ACTIVE;
            sb.append(u.getUsername()).append(":").append(u.getRole()).append(":").append(status);
            first = false;
        }
        broadcast(sb.toString());

        if (listener != null) {
            listener.onOnlineListUpdated(getOnlineUsersList());
        }
        if (webListener != null) {
            webListener.onUserListUpdated(getOnlineUsersList());
        }
    }

    /**
     * Gửi danh sách cập nhật các nhóm cho toàn bộ Client
     */
    public void broadcastGroupList() {
        List<ChatGroup> list = groupDAO.getAllGroups();
        StringBuilder sb = new StringBuilder(Protocol.RES_GROUP_LIST).append(Protocol.DELIMITER);
        boolean first = true;
        for (ChatGroup g : list) {
            if (!first) sb.append(",");
            sb.append(g.getGroupName()).append(":").append(g.getMemberCount());
            first = false;
        }
        broadcast(sb.toString());

        if (webListener != null) {
            webListener.onGroupListUpdated(list);
        }
    }

    public boolean kickUser(String targetUser, String reason) {
        ClientHandler target = onlineClients.get(targetUser);
        if (target != null) {
            target.sendPacket(Protocol.NOTIFY_KICKED + Protocol.DELIMITER + (reason != null ? reason : "Bị Admin đuổi."));
            target.closeConnection();
            removeClient(targetUser, false);
            broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "[Quản trị] " + targetUser + " đã bị đuổi khỏi phòng.");
            broadcastUserList();
            return true;
        }
        return false;
    }

    public boolean muteUser(String targetUser, int minutes) {
        if (!onlineClients.containsKey(targetUser)) return false;
        long expireTime = minutes > 0 ? System.currentTimeMillis() + (minutes * 60 * 1000L) : 0L;
        mutedUsers.put(targetUser, expireTime);

        ClientHandler target = onlineClients.get(targetUser);
        if (target != null) {
            target.sendPacket(Protocol.NOTIFY_MUTED + Protocol.DELIMITER + minutes + Protocol.DELIMITER + "Bạn đã bị cấm chat bởi Admin.");
        }

        String durationText = minutes > 0 ? minutes + " phút" : "vô thời hạn";
        broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "[Quản trị] " + targetUser + " đã bị cấm chat trong " + durationText + ".");
        broadcastUserList();
        return true;
    }

    public boolean unmuteUser(String targetUser) {
        if (mutedUsers.remove(targetUser) != null) {
            ClientHandler target = onlineClients.get(targetUser);
            if (target != null) {
                target.sendPacket(Protocol.NOTIFY_UNMUTED);
            }
            broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "[Quản trị] " + targetUser + " đã được mở lại quyền chat.");
            broadcastUserList();
            return true;
        }
        return false;
    }

    public boolean isUserMuted(String username) {
        if (username == null) return false;
        Long expire = mutedUsers.get(username);
        if (expire == null) return false;

        if (expire == 0L) return true;
        if (System.currentTimeMillis() < expire) {
            return true;
        } else {
            mutedUsers.remove(username);
            return false;
        }
    }

    public boolean isUserOnline(String username) {
        return username != null && (onlineClients.containsKey(username) || webClients.containsKey(username));
    }

    public int getOnlineCount() {
        return onlineClients.size() + webClients.size();
    }

    public List<User> getOnlineUsersList() {
        List<User> list = new ArrayList<>();
        for (ClientHandler h : onlineClients.values()) {
            String status = isUserMuted(h.getUsername()) ? Protocol.STATUS_MUTED : Protocol.STATUS_ACTIVE;
            User u = new User(h.getUsername(), h.getRole(), status);
            list.add(u);
        }
        for (User u : webClients.values()) {
            String status = isUserMuted(u.getUsername()) ? Protocol.STATUS_MUTED : Protocol.STATUS_ACTIVE;
            list.add(new User(u.getUsername(), u.getRole(), status));
        }
        return list;
    }

    public ClientHandler getClient(String username) {
        return onlineClients.get(username);
    }

    public void clearAll() {
        for (ClientHandler h : onlineClients.values()) {
            h.closeConnection();
        }
        onlineClients.clear();
        webClients.clear();
        mutedUsers.clear();
    }
}
