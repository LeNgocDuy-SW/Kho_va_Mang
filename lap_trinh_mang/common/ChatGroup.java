package common;

import java.io.Serializable;

/**
 * ChatGroup: Đại diện cho một nhóm chat có mã bảo vệ (Group Code).
 */
public class ChatGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String groupName;
    private String groupCode;
    private String creator;
    private String createdAt;
    private int memberCount;

    public ChatGroup() {}

    public ChatGroup(String groupName, String groupCode, String creator, String createdAt) {
        this.groupName = groupName;
        this.groupCode = groupCode;
        this.creator = creator;
        this.createdAt = createdAt;
        this.memberCount = 1;
    }

    public ChatGroup(int id, String groupName, String groupCode, String creator, String createdAt, int memberCount) {
        this.id = id;
        this.groupName = groupName;
        this.groupCode = groupCode;
        this.creator = creator;
        this.createdAt = createdAt;
        this.memberCount = memberCount;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
}

