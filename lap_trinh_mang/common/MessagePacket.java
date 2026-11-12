package common;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MessagePacket: Đóng gói đối tượng tin nhắn trao đổi trong hệ thống.
 */
public class MessagePacket implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    public static final String TYPE_PUBLIC = "PUBLIC";
    public static final String TYPE_PRIVATE = "PRIVATE";
    public static final String TYPE_SYSTEM = "SYSTEM";

    private int id;
    private String groupName; // Tên nhóm (Mặc định: Friends & Family)
    private String type; // PUBLIC, PRIVATE, SYSTEM
    private String sender;
    private String senderRole;
    private String receiver; // "ALL" hoặc username
    private String content;
    private String timestamp;

    public MessagePacket() {
        this.timestamp = LocalDateTime.now().format(TIME_FMT);
        this.type = TYPE_PUBLIC;
        this.receiver = "ALL";
        this.groupName = Protocol.DEFAULT_GROUP;
    }

    public MessagePacket(String type, String sender, String senderRole, String receiver, String content) {
        this(type, sender, senderRole, receiver, Protocol.DEFAULT_GROUP, content);
    }

    public MessagePacket(String type, String sender, String senderRole, String receiver, String groupName, String content) {
        this.type = type != null ? type : TYPE_PUBLIC;
        this.sender = sender;
        this.senderRole = senderRole != null ? senderRole : Protocol.ROLE_USER;
        this.receiver = receiver != null ? receiver : "ALL";
        this.groupName = groupName != null ? groupName : Protocol.DEFAULT_GROUP;
        this.content = content;
        this.timestamp = LocalDateTime.now().format(TIME_FMT);
    }

    public MessagePacket(int id, String type, String sender, String senderRole, String receiver, String content, String timestamp) {
        this(id, type, sender, senderRole, receiver, Protocol.DEFAULT_GROUP, content, timestamp);
    }

    public MessagePacket(int id, String type, String sender, String senderRole, String receiver, String groupName, String content, String timestamp) {
        this.id = id;
        this.type = type;
        this.sender = sender;
        this.senderRole = senderRole;
        this.receiver = receiver;
        this.groupName = groupName != null ? groupName : Protocol.DEFAULT_GROUP;
        this.content = content;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    /**
     * Mã hóa tin nhắn thành chuỗi an toàn trong gói HISTORY
     * Cấu trúc: type~sender~role~receiver~timestamp~groupName~contentBase64
     */
    public String encodeForHistory() {
        String safeContent = content != null ? java.util.Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : "";
        return type + "~" + (sender != null ? sender : "") + "~" + (senderRole != null ? senderRole : "") + "~"
                + (receiver != null ? receiver : "ALL") + "~" + timestamp + "~" + (groupName != null ? groupName : Protocol.DEFAULT_GROUP) + "~" + safeContent;
    }

    public static MessagePacket decodeFromHistory(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split("~", 7);
        if (parts.length == 6) {
            String rawContent = "";
            try {
                rawContent = new String(java.util.Base64.getDecoder().decode(parts[5]), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                rawContent = parts[5];
            }
            return new MessagePacket(0, parts[0], parts[1], parts[2], parts[3], Protocol.DEFAULT_GROUP, rawContent, parts[4]);
        } else if (parts.length >= 7) {
            String rawContent = "";
            try {
                rawContent = new String(java.util.Base64.getDecoder().decode(parts[6]), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                rawContent = parts[6];
            }
            return new MessagePacket(0, parts[0], parts[1], parts[2], parts[3], parts[5], rawContent, parts[4]);
        }
        return null;
    }
}
