package client.network;

import common.ChatGroup;
import common.MessagePacket;
import common.User;
import java.util.List;

/**
 * ChatEventListener: Giao diện định nghĩa các sự kiện mạng từ Server về Client.
 */
public interface ChatEventListener {
    void onLoginResult(boolean success, String role, String username, String message);
    void onRegisterResult(boolean success, String message);

    // Sự kiện Nhóm & Mã Nhóm
    void onCreateGroupResult(boolean success, String groupName, String groupCode, String message);
    void onJoinGroupResult(boolean success, String groupName, String message);
    void onGroupListUpdated(List<ChatGroup> groups);

    // Sự kiện Tin Nhắn
    void onPublicMessageReceived(String sender, String role, String timestamp, String content);
    void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content);
    void onPrivateMessageReceived(String sender, String target, String timestamp, String content);
    void onSystemMessage(String content);

    // Sự kiện Quản trị & Trạng thái
    void onUserListUpdated(List<User> onlineUsers);
    void onHistoryReceived(String groupName, List<MessagePacket> history);
    void onKicked(String reason);
    void onMuted(int minutes, String reason);
    void onUnmuted();
    void onChangePasswordResult(boolean success, String message);
    void onConnectionLost(String reason);
}
