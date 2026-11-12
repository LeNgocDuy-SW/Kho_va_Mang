package server.db;

import common.MessagePacket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MessageDAO: Lưu trữ và truy xuất lịch sử tin nhắn phòng chung và các nhóm riêng biệt.
 */
public class MessageDAO {

    public boolean saveMessage(MessagePacket msg) {
        if (msg == null || msg.getContent() == null || msg.getContent().trim().isEmpty()) {
            return false;
        }

        String sql = "INSERT INTO messages (group_name, type, sender, sender_role, receiver, content, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getGroupName() != null ? msg.getGroupName() : "Friends & Family");
            ps.setString(2, msg.getType() != null ? msg.getType() : MessagePacket.TYPE_PUBLIC);
            ps.setString(3, msg.getSender() != null ? msg.getSender() : "Unknown");
            ps.setString(4, msg.getSenderRole() != null ? msg.getSenderRole() : "USER");
            ps.setString(5, msg.getReceiver() != null ? msg.getReceiver() : "ALL");
            ps.setString(6, msg.getContent());
            ps.setString(7, msg.getTimestamp());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi lưu tin: " + e.getMessage());
            return false;
        }
    }

    public List<MessagePacket> getRecentMessages(int limit) {
        return getGroupMessages("Friends & Family", limit);
    }

    /**
     * Lấy N tin nhắn gần nhất của một nhóm cụ thể
     */
    public List<MessagePacket> getGroupMessages(String groupName, int limit) {
        List<MessagePacket> list = new ArrayList<>();
        String sql = "SELECT id, group_name, type, sender, sender_role, receiver, content, timestamp "
                + "FROM messages WHERE group_name = ? AND type = 'PUBLIC' "
                + "ORDER BY id DESC LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName != null ? groupName : "Friends & Family");
            ps.setInt(2, limit <= 0 ? 50 : limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MessagePacket(
                            rs.getInt("id"),
                            rs.getString("type"),
                            rs.getString("sender"),
                            rs.getString("sender_role"),
                            rs.getString("receiver"),
                            rs.getString("group_name"),
                            rs.getString("content"),
                            rs.getString("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] Lỗi tải tin nhắn nhóm " + groupName + ": " + e.getMessage());
        }

        Collections.reverse(list);
        return list;
    }

    public boolean clearHistory() {
        String sql = "DELETE FROM messages";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
