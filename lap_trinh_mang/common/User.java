package common;

import java.io.Serializable;

/**
 * User: Đại diện cho thực thể người dùng trong hệ thống Chat.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private String passwordHash;
    private String role; // ADMIN hoặc USER
    private String status; // ACTIVE, MUTED, BANNED
    private String currentRoom; // Phòng chat hiện tại
    private String createdAt;
    private boolean isOnline;

    public User() {
        this.role = Protocol.ROLE_USER;
        this.status = Protocol.STATUS_ACTIVE;
        this.currentRoom = Protocol.DEFAULT_ROOM;
    }

    public User(int id, String username, String passwordHash, String role, String status, String createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : Protocol.ROLE_USER;
        this.status = status != null ? status : Protocol.STATUS_ACTIVE;
        this.currentRoom = Protocol.DEFAULT_ROOM;
        this.createdAt = createdAt;
        this.isOnline = false;
    }

    public User(String username, String role, String status) {
        this(username, role, status, Protocol.DEFAULT_ROOM);
    }

    public User(String username, String role, String status, String currentRoom) {
        this.username = username;
        this.role = role;
        this.status = status;
        this.currentRoom = currentRoom != null ? currentRoom : Protocol.DEFAULT_ROOM;
        this.isOnline = true;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(String currentRoom) { this.currentRoom = currentRoom; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public boolean isAdmin() {
        return Protocol.ROLE_ADMIN.equalsIgnoreCase(this.role);
    }

    public boolean isMuted() {
        return Protocol.STATUS_MUTED.equalsIgnoreCase(this.status);
    }

    public boolean isBanned() {
        return Protocol.STATUS_BANNED.equalsIgnoreCase(this.status);
    }

    @Override
    public String toString() {
        return username + " (" + role + (isMuted() ? " - MUTED" : "") + ") [" + currentRoom + "]";
    }
}
