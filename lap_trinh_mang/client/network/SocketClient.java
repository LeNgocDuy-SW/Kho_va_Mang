package client.network;

import common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * SocketClient: Quản lý kết nối TCP Socket từ Client tới Server.
 */
public class SocketClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ServerListener listenerThread;

    private String host;
    private int port;
    private boolean connected = false;

    public synchronized boolean connect(String host, int port, ChatEventListener eventListener) {
        if (connected) return true;
        this.host = host;
        this.port = port;

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            listenerThread = new ServerListener(in, eventListener);
            listenerThread.start();

            connected = true;
            return true;
        } catch (IOException e) {
            disconnect();
            return false;
        }
    }

    /**
     * Chuyển giao Listener nhận sự kiện mạng cho màn hình mới (ví dụ khi mở ChatRoomFrame)
     */
    public void setEventListener(ChatEventListener listener) {
        if (listenerThread != null) {
            listenerThread.setEventListener(listener);
        }
    }

    public synchronized void sendPacket(String packet) {
        if (out != null && connected) {
            out.println(packet);
        }
    }

    public void login(String username, String password) {
        sendPacket(Protocol.CMD_LOGIN + Protocol.DELIMITER + username + Protocol.DELIMITER + password);
    }

    public void register(String username, String password, String role) {
        sendPacket(Protocol.CMD_REGISTER + Protocol.DELIMITER + username + Protocol.DELIMITER + password + Protocol.DELIMITER + role);
    }

    public void createGroup(String groupName, String groupCode) {
        sendPacket(Protocol.CMD_CREATE_GROUP + Protocol.DELIMITER + groupName + Protocol.DELIMITER + groupCode);
    }

    public void joinGroup(String groupName, String groupCode) {
        sendPacket(Protocol.CMD_JOIN_GROUP + Protocol.DELIMITER + groupName + Protocol.DELIMITER + groupCode);
    }

    public void sendPublicMessage(String content) {
        sendPacket(Protocol.CMD_MSG_ALL + Protocol.DELIMITER + content);
    }

    public void sendGroupMessage(String groupName, String content) {
        sendPacket(Protocol.CMD_MSG_GROUP + Protocol.DELIMITER + groupName + Protocol.DELIMITER + content);
    }

    public void sendPrivateMessage(String targetUser, String content) {
        sendPacket(Protocol.CMD_MSG_PRIVATE + Protocol.DELIMITER + targetUser + Protocol.DELIMITER + content);
    }

    public void sendAdminCommand(String action, String targetUser, String extra) {
        StringBuilder sb = new StringBuilder(Protocol.CMD_ADMIN).append(Protocol.DELIMITER).append(action);
        if (targetUser != null && !targetUser.isEmpty()) {
            sb.append(Protocol.DELIMITER).append(targetUser);
        }
        if (extra != null && !extra.isEmpty()) {
            sb.append(Protocol.DELIMITER).append(extra);
        }
        sendPacket(sb.toString());
    }

    public void changePassword(String oldPassword, String newPassword) {
        sendPacket(Protocol.CMD_CHANGE_PASS + Protocol.DELIMITER + oldPassword + Protocol.DELIMITER + newPassword);
    }

    public void requestUserList() {
        sendPacket(Protocol.CMD_GET_USERS);
    }

    public void requestGroupList() {
        sendPacket(Protocol.CMD_GET_ROOMS);
    }

    public void logout() {
        sendPacket(Protocol.CMD_LOGOUT);
        disconnect();
    }

    public synchronized void disconnect() {
        connected = false;
        if (listenerThread != null) {
            listenerThread.stopListening();
            listenerThread = null;
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

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
}
