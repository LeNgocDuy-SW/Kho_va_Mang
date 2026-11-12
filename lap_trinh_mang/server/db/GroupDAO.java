package server.db;

import common.ChatGroup;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * GroupDAO: Thao tác CSDL về Nhóm Chat và Mã Nhóm (chat_groups & group_members).
 */
public class GroupDAO {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Tạo nhóm mới có mã bảo vệ
     */
    public boolean createGroup(String groupName, String groupCode, String creator) {
        if (groupName == null || groupName.trim().isEmpty() || groupCode == null || groupCode.trim().isEmpty()) {
            return false;
        }
        groupName = groupName.trim();
        groupCode = groupCode.trim();
        String now = LocalDateTime.now().format(FMT);

        String sql = "INSERT INTO chat_groups (group_name, group_code, creator, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, groupCode);
            ps.setString(3, creator);
            ps.setString(4, now);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Thêm người tạo làm thành viên đầu tiên
                addMember(groupName, creator);
                return true;
            }
        } catch (SQLException e) {
            // Tên nhóm đã tồn tại
        }
        return false;
    }

    /**
     * Xác thực mã nhóm khi người dùng muốn tham gia
     */
    public boolean verifyGroupCode(String groupName, String inputCode) {
        if (groupName == null || inputCode == null) return false;
        String sql = "SELECT group_code FROM chat_groups WHERE group_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actualCode = rs.getString("group_code");
                    return actualCode.trim().equals(inputCode.trim());
                }
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi kiểm tra mã nhóm: " + e.getMessage());
        }
        return false;
    }

    /**
     * Thêm thành viên vào nhóm sau khi nhập đúng mã
     */
    public boolean addMember(String groupName, String username) {
        String now = LocalDateTime.now().format(FMT);
        String sql = "INSERT OR IGNORE INTO group_members (group_name, username, joined_at) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            ps.setString(3, now);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Kiểm tra người dùng đã tham gia nhóm này chưa
     */
    public boolean isMember(String groupName, String username) {
        String sql = "SELECT 1 FROM group_members WHERE group_name = ? AND username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            ps.setString(2, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Lấy danh sách tất cả các nhóm kèm số lượng thành viên
     */
    public List<ChatGroup> getAllGroups() {
        List<ChatGroup> list = new ArrayList<>();
        String sql = "SELECT g.id, g.group_name, g.group_code, g.creator, g.created_at, "
                + "(SELECT COUNT(*) FROM group_members m WHERE m.group_name = g.group_name) as member_count "
                + "FROM chat_groups g ORDER BY g.id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ChatGroup(
                        rs.getInt("id"),
                        rs.getString("group_name"),
                        rs.getString("group_code"),
                        rs.getString("creator"),
                        rs.getString("created_at"),
                        Math.max(1, rs.getInt("member_count"))
                ));
            }
        } catch (SQLException e) {
            System.err.println("[GroupDAO] Lỗi tải danh sách nhóm: " + e.getMessage());
        }
        return list;
    }
}

