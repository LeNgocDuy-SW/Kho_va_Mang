package client.network;

import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ServerListener: Luồng lắng nghe ngầm liên tục nhận và phân tích gói tin từ Server.
 */
public class ServerListener extends Thread {
    private final BufferedReader in;
    private volatile ChatEventListener listener;
    private volatile boolean running = true;

    public ServerListener(BufferedReader in, ChatEventListener listener) {
        super("Client-ServerListener-Thread");
        setDaemon(true);
        this.in = in;
        this.listener = listener;
    }

    public void setEventListener(ChatEventListener listener) {
        this.listener = listener;
    }

    public void stopListening() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                parsePacket(line);
            }
        } catch (IOException e) {
            // Mất kết nối tới Server
        } finally {
            if (running) {
                dispatch(() -> listener.onConnectionLost("Mất kết nối tới máy chủ!"));
            }
        }
    }

    private void parsePacket(String line) {
        String[] parts = line.split(Protocol.DELIMITER_REGEX, 2);
        String header = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";

        switch (header) {
            case Protocol.RES_LOGIN:
                handleLoginRes(payload);
                break;
            case Protocol.RES_REGISTER:
                handleRegisterRes(payload);
                break;
            case Protocol.RES_CREATE_GROUP:
                handleCreateGroupRes(payload);
                break;
            case Protocol.RES_JOIN_GROUP:
                handleJoinGroupRes(payload);
                break;
            case Protocol.RES_GROUP_LIST:
                handleGroupList(payload);
                break;
            case Protocol.RES_NEW_MSG:
                handleNewMsg(payload);
                break;
            case Protocol.RES_NEW_PRIVATE_MSG:
                handleNewPrivateMsg(payload);
                break;
            case Protocol.RES_SYSTEM_MSG:
                dispatch(() -> listener.onSystemMessage(payload));
                break;
            case Protocol.RES_USER_LIST:
                handleUserList(payload);
                break;
            case Protocol.RES_HISTORY:
                handleHistory(payload);
                break;
            case Protocol.NOTIFY_KICKED:
                dispatch(() -> listener.onKicked(payload));
                break;
            case Protocol.NOTIFY_MUTED:
                handleMuted(payload);
                break;
            case Protocol.NOTIFY_UNMUTED:
                dispatch(listener::onUnmuted);
                break;
            case Protocol.RES_CHANGE_PASS:
                handleChangePassRes(payload);
                break;
            default:
                dispatch(() -> listener.onSystemMessage(line));
                break;
        }
    }

    private void handleLoginRes(String payload) {
        String[] items = payload.split(Protocol.DELIMITER_REGEX);
        boolean success = Protocol.SUCCESS.equalsIgnoreCase(items[0]);
        String role = items.length > 1 ? items[1] : Protocol.ROLE_USER;
        String username = items.length > 2 ? items[2] : "";
        String msg = success ? "Đăng nhập thành công!" : (items.length > 1 ? items[1] : "Thất bại");
        dispatch(() -> listener.onLoginResult(success, role, username, msg));
    }

    private void handleRegisterRes(String payload) {
        String[] items = payload.split(Protocol.DELIMITER_REGEX, 2);
        boolean success = Protocol.SUCCESS.equalsIgnoreCase(items[0]);
        String msg = items.length > 1 ? items[1] : "";
        dispatch(() -> listener.onRegisterResult(success, msg));
    }

    private void handleCreateGroupRes(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        boolean success = Protocol.SUCCESS.equalsIgnoreCase(parts[0]);
        String gName = parts.length > 1 ? parts[1] : "";
        String gCode = parts.length > 2 ? parts[2] : "";
        String msg = success ? "Tạo nhóm thành công!" : (parts.length > 1 ? parts[1] : "Lỗi tạo nhóm");
        dispatch(() -> listener.onCreateGroupResult(success, gName, gCode, msg));
    }

    private void handleJoinGroupRes(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        boolean success = Protocol.SUCCESS.equalsIgnoreCase(parts[0]);
        String gName = parts.length > 1 ? parts[1] : "";
        String msg = success ? "Tham gia nhóm thành công!" : (parts.length > 1 ? parts[1] : "Sai mã nhóm");
        dispatch(() -> listener.onJoinGroupResult(success, gName, msg));
    }

    private void handleGroupList(String payload) {
        List<ChatGroup> list = new ArrayList<>();
        if (!payload.isEmpty()) {
            String[] items = payload.split(",");
            for (String it : items) {
                String[] tokens = it.split(":");
                String gName = tokens[0];
                int count = tokens.length > 1 ? Integer.parseInt(tokens[1]) : 1;
                list.add(new ChatGroup(0, gName, "", "", "", count));
            }
        }
        dispatch(() -> listener.onGroupListUpdated(list));
    }

    private void handleNewMsg(String payload) {
        // NEW_MSG|groupName|sender|role|timestamp|content (hoặc cũ 4 parts)
        String[] items = payload.split(Protocol.DELIMITER_REGEX, 5);
        if (items.length == 5) {
            String groupName = items[0];
            String sender = items[1];
            String role = items[2];
            String time = items[3];
            String content = items[4];
            dispatch(() -> listener.onGroupMessageReceived(groupName, sender, role, time, content));
        } else if (items.length == 4) {
            String sender = items[0];
            String role = items[1];
            String time = items[2];
            String content = items[3];
            dispatch(() -> listener.onPublicMessageReceived(sender, role, time, content));
        }
    }

    private void handleNewPrivateMsg(String payload) {
        String[] items = payload.split(Protocol.DELIMITER_REGEX, 4);
        if (items.length >= 4) {
            String sender = items[0];
            String target = items[1];
            String time = items[2];
            String content = items[3];
            dispatch(() -> listener.onPrivateMessageReceived(sender, target, time, content));
        }
    }

    private void handleUserList(String payload) {
        List<User> list = new ArrayList<>();
        if (!payload.isEmpty()) {
            String[] users = payload.split(",");
            for (String u : users) {
                String[] tokens = u.split(":");
                if (tokens.length >= 2) {
                    String username = tokens[0];
                    String role = tokens[1];
                    String status = tokens.length > 2 ? tokens[2] : Protocol.STATUS_ACTIVE;
                    list.add(new User(username, role, status));
                }
            }
        }
        dispatch(() -> listener.onUserListUpdated(list));
    }

    private void handleHistory(String payload) {
        // HISTORY|groupName|count|encoded_items... HOẶC HISTORY|count|encoded_items...
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 3);
        String groupName = "Friends & Family";
        String rawItems = "";

        if (parts.length == 3) {
            groupName = parts[0];
            rawItems = parts[2];
        } else if (parts.length == 2) {
            rawItems = parts[1];
        }

        List<MessagePacket> history = new ArrayList<>();
        if (!rawItems.isEmpty()) {
            String[] items = rawItems.split(";;");
            for (String item : items) {
                MessagePacket packet = MessagePacket.decodeFromHistory(item);
                if (packet != null) history.add(packet);
            }
        }
        final String finalGroup = groupName;
        dispatch(() -> listener.onHistoryReceived(finalGroup, history));
    }

    private void handleMuted(String payload) {
        String[] parts = payload.split(Protocol.DELIMITER_REGEX, 2);
        int mins = 5;
        try { mins = Integer.parseInt(parts[0]); } catch (Exception ignored) {}
        String reason = parts.length > 1 ? parts[1] : "Bị cấm chat bởi Admin.";
        final int finalMins = mins;
        dispatch(() -> listener.onMuted(finalMins, reason));
    }

    private void handleChangePassRes(String payload) {
        String[] items = payload.split(Protocol.DELIMITER_REGEX, 2);
        boolean success = Protocol.SUCCESS.equalsIgnoreCase(items[0]);
        String msg = items.length > 1 ? items[1] : "";
        dispatch(() -> listener.onChangePasswordResult(success, msg));
    }

    private void dispatch(Runnable r) {
        if (listener != null) {
            if (SwingUtilities.isEventDispatchThread() || java.awt.Window.getWindows().length == 0) {
                r.run();
            } else {
                SwingUtilities.invokeLater(r);
            }
        }
    }
}
