package server;

import common.User;
import java.util.List;

/**
 * ServerEventListener: Giao diện callback thông báo sự kiện từ ChatServer tới Server GUI/Console.
 */
public interface ServerEventListener {
    void onLog(String logMessage);
    void onClientConnected(String username, String role, String ipAddress);
    void onClientDisconnected(String username);
    void onMessageCountIncremented();
    void onOnlineListUpdated(List<User> onlineList);
}

