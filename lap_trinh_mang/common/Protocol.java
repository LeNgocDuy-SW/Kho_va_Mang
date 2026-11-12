package common;

/**
 * Protocol: Định nghĩa quy ước giao thức truyền thông giữa Client và Server.
 * Dữ liệu đóng gói dạng chuỗi UTF-8 phân tách bởi dấu gạch đứng '|'.
 */
public class Protocol {
    // Ký tự phân cách chính
    public static final String DELIMITER = "|";
    public static final String DELIMITER_REGEX = "\\|";

    // --- Xác thực (Auth) ---
    public static final String CMD_LOGIN = "LOGIN";                 // C -> S: LOGIN|username|password
    public static final String RES_LOGIN = "LOGIN_RES";             // S -> C: LOGIN_RES|SUCCESS|role|username HOẶC LOGIN_RES|FAILED|reason
    public static final String CMD_REGISTER = "REGISTER";           // C -> S: REGISTER|username|password|role
    public static final String RES_REGISTER = "REGISTER_RES";       // S -> C: REGISTER_RES|SUCCESS|msg HOẶC REGISTER_RES|FAILED|reason
    public static final String CMD_LOGOUT = "LOGOUT";               // C -> S: LOGOUT
    public static final String CMD_CHANGE_PASS = "CHANGE_PASS";     // C -> S: CHANGE_PASS|oldPassword|newPassword
    public static final String RES_CHANGE_PASS = "CHANGE_PASS_RES"; // S -> C: CHANGE_PASS_RES|SUCCESS|msg HOẶC FAILED|reason

    // --- Quản lý Nhóm & Mã Nhóm (Groups & Group Code) ---
    public static final String CMD_CREATE_GROUP = "CREATE_GROUP";   // C -> S: CREATE_GROUP|groupName|groupCode
    public static final String RES_CREATE_GROUP = "CREATE_GROUP_RES";// S -> C: CREATE_GROUP_RES|SUCCESS|groupName HOẶC FAILED|reason
    public static final String CMD_JOIN_GROUP = "JOIN_GROUP";       // C -> S: JOIN_GROUP|groupName|groupCode
    public static final String RES_JOIN_GROUP = "JOIN_GROUP_RES";   // S -> C: JOIN_GROUP_RES|SUCCESS|groupName HOẶC FAILED|reason
    public static final String CMD_GET_GROUPS = "GET_GROUPS";       // C -> S: GET_GROUPS
    public static final String CMD_GET_ROOMS = CMD_GET_GROUPS;      // Alias
    public static final String RES_GROUP_LIST = "GROUP_LIST";       // S -> C: GROUP_LIST|g1:count,g2:count,...

    // --- Tin nhắn (Chat) ---
    public static final String CMD_MSG_ALL = "MSG_ALL";             // C -> S: MSG_ALL|nội_dung (Phòng mặc định)
    public static final String CMD_MSG_GROUP = "MSG_GROUP";         // C -> S: MSG_GROUP|groupName|nội_dung
    public static final String RES_NEW_MSG = "NEW_MSG";             // S -> C: NEW_MSG|groupName|sender|role|timestamp|nội_dung
    public static final String CMD_MSG_PRIVATE = "MSG_PRIVATE";     // C -> S: MSG_PRIVATE|targetUser|nội_dung
    public static final String RES_NEW_PRIVATE_MSG = "NEW_PRIVATE_MSG"; // S -> C: NEW_PRIVATE_MSG|sender|target|timestamp|nội_dung
    public static final String RES_SYSTEM_MSG = "SYSTEM_MSG";       // S -> C: SYSTEM_MSG|nội_dung
    public static final String RES_HISTORY = "HISTORY";             // S -> C: HISTORY|groupName|count|item1;;item2;;...

    // --- Truyền File (File Transfer) ---
    public static final String CMD_SEND_FILE = "SEND_FILE";         // C -> S: SEND_FILE|target|fileName|fileSize
    public static final String RES_FILE_MSG = "FILE_MSG";           // S -> C: FILE_MSG|sender|role|timestamp|target|fileName|fileSize

    // --- Danh sách người dùng & Trạng thái ---
    public static final String CMD_GET_USERS = "GET_USERS";         // C -> S: GET_USERS
    public static final String RES_USER_LIST = "USER_LIST";         // S -> C: USER_LIST|user1:ROLE:STATUS,user2:ROLE:STATUS

    // --- Lệnh Quản trị (Admin Moderation) ---
    public static final String CMD_ADMIN = "ADMIN_CMD";             // C -> S: ADMIN_CMD|ACTION|targetUser|params
    public static final String ACT_KICK = "KICK";                   // ADMIN_CMD|KICK|targetUser|reason
    public static final String ACT_MUTE = "MUTE";                   // ADMIN_CMD|MUTE|targetUser|minutes
    public static final String ACT_UNMUTE = "UNMUTE";               // ADMIN_CMD|UNMUTE|targetUser
    public static final String ACT_BAN = "BAN";                     // ADMIN_CMD|BAN|targetUser|reason
    public static final String ACT_UNBAN = "UNBAN";                 // ADMIN_CMD|UNBAN|targetUser
    public static final String ACT_ALERT = "ALERT";                 // ADMIN_CMD|ALERT|message
    public static final String ACT_CLEAR_HISTORY = "CLEAR_HISTORY"; // ADMIN_CMD|CLEAR_HISTORY

    // --- Thông báo ép buộc từ Server tới Client ---
    public static final String NOTIFY_KICKED = "KICKED";            // S -> C: KICKED|reason
    public static final String NOTIFY_MUTED = "MUTED";              // S -> C: MUTED|minutes|reason
    public static final String NOTIFY_UNMUTED = "UNMUTED";          // S -> C: UNMUTED

    // --- Vai trò (Roles) ---
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    // --- Trạng thái người dùng (Status) ---
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_MUTED = "MUTED";
    public static final String STATUS_BANNED = "BANNED";

    // --- Nhóm mặc định ---
    public static final String DEFAULT_GROUP = "Friends & Family";
    public static final String DEFAULT_ROOM = DEFAULT_GROUP;
    public static final String DEFAULT_GROUP_CODE = "1234";

    // Phản hồi kết quả
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
}
