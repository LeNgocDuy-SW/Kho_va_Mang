package server.db;

import common.Protocol;
import common.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * UserDAO: Thao tác dữ liệu người dùng (Đăng ký, Đăng nhập, Phân quyền, Ban, Mute).
 */
public class UserDAO {

    /**
     * Đăng ký tài khoản mới.
     * @return true nếu đăng ký thành công, false nếu tên người dùng đã tồn tại.
     */
    public boolean register(String username, String password, String role) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }
        username = username.trim();
        String passwordHash = DatabaseConnection.hashPassword(password);
        String finalRole = (role != null && Protocol.ROLE_ADMIN.equalsIgnoreCase(role)) ? Protocol.ROLE_ADMIN : Protocol.ROLE_USER;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String sql = "INSERT INTO users (username, password_hash, role, status, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, finalRole);
            ps.setString(4, Protocol.STATUS_ACTIVE);
            ps.setString(5, now);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            // Lỗi SQLite UNIQUE constraint (tài khoản đã tồn tại)
            return false;
        }
    }

    /**
     * Xác thực thông tin đăng nhập.
     * @return Đối tượng User nếu đúng tài khoản và mật khẩu, ngược lại trả về null.
     */
    public User login(String username, String password) {
        if (username == null || password == null) return null;
        username = username.trim();
        String inputHash = DatabaseConnection.hashPassword(password);

        String sql = "SELECT id, username, password_hash, role, status, created_at FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbHash = rs.getString("password_hash");
                    // Hỗ trợ kiểm tra hash bảo mật
                    if (inputHash.equals(dbHash) || password.equals(dbHash)) {
                        return new User(
                                rs.getInt("id"),
                                rs.getString("username"),
                                dbHash,
                                rs.getString("role"),
                                rs.getString("status"),
                                rs.getString("created_at")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi truy vấn đăng nhập: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tìm thông tin người dùng theo tên
     */
    public User findByUsername(String username) {
        if (username == null) return null;
        String sql = "SELECT id, username, password_hash, role, status, created_at FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi tìm user: " + e.getMessage());
        }
        return null;
    }

    /**
     * Đổi mật khẩu người dùng
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = login(username, oldPassword);
        if (user == null) return false;

        String newHash = DatabaseConnection.hashPassword(newPassword);
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi đổi mật khẩu: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái người dùng (ACTIVE, MUTED, BANNED)
     */
    public boolean updateStatus(String username, String newStatus) {
        String sql = "UPDATE users SET status = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi cập nhật status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật vai trò (ADMIN, USER)
     */
    public boolean updateRole(String username, String newRole) {
        String sql = "UPDATE users SET role = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi cập nhật vai trò: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy danh sách tất cả tài khoản
     */
    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username, password_hash, role, status, created_at FROM users ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] Lỗi lấy danh sách user: " + e.getMessage());
        }
        return list;
    }
}

