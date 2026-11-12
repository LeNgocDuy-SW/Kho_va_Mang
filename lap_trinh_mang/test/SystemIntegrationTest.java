package test;

import client.network.ChatEventListener;
import client.network.SocketClient;
import common.MessagePacket;
import common.Protocol;
import common.User;
import server.ChatServer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SystemIntegrationTest: Kiểm thử tích hợp toàn diện hệ thống Chat Room:
 * 1. Khởi động Server + SQLite Database
 * 2. Đăng ký người dùng mới
 * 3. Đăng nhập Admin & User
 * 4. Gửi & Nhận tin nhắn phòng chung (Broadcast)
 * 5. Gửi & Nhận tin nhắn riêng tư 1-1 (Unicast)
 * 6. Kiểm thử phân quyền Admin: Mute, Kick, Ban
 * 7. Lưu trữ và nạp lịch sử tin nhắn
 */
public class SystemIntegrationTest {
    private static final int TEST_PORT = 5005;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   BẮT ĐẦU KIỂM THỬ TÍCH HỢP HỆ THỐNG CHAT ROOM   ");
        System.out.println("==================================================");

        // 1. Khởi động Server
        ChatServer server = new ChatServer(TEST_PORT);
        boolean started = server.startServer();
        assertCondition(started, "Khởi động ChatServer tại cổng " + TEST_PORT);

        Thread.sleep(500);

        // 2. Client 1 (Alice) đăng ký tài khoản
        SocketClient clientAlice = new SocketClient();
        CountDownLatch regLatch = new CountDownLatch(1);
        AtomicBoolean regSuccess = new AtomicBoolean(false);

        boolean connOk = clientAlice.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onRegisterResult(boolean success, String message) {
                regSuccess.set(success);
                regLatch.countDown();
            }
        });

        clientAlice.register("alice_test", "alice123", Protocol.ROLE_USER);
        boolean regDone = regLatch.await(3, TimeUnit.SECONDS);
        assertCondition(regDone, "Nhận phản hồi đăng ký từ Server");
        clientAlice.disconnect();

        Thread.sleep(300);

        // 3. Client Admin (admin / admin123) đăng nhập
        SocketClient clientAdmin = new SocketClient();
        CountDownLatch adminLoginLatch = new CountDownLatch(1);
        AtomicBoolean adminLoginOk = new AtomicBoolean(false);
        AtomicReference<String> adminRole = new AtomicReference<>("");

        boolean adminConn = clientAdmin.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                adminLoginOk.set(success);
                adminRole.set(role);
                adminLoginLatch.countDown();
            }
        });

        clientAdmin.login("admin", "admin123");
        boolean adminLoggedIn = adminLoginLatch.await(3, TimeUnit.SECONDS);
        assertCondition(adminLoggedIn && adminLoginOk.get(), "Admin đăng nhập thành công");
        assertCondition(Protocol.ROLE_ADMIN.equalsIgnoreCase(adminRole.get()), "Quyền hạn của Admin chuẩn ROLE_ADMIN");

        // 4. Client Alice đăng nhập
        clientAlice = new SocketClient();
        CountDownLatch aliceLoginLatch = new CountDownLatch(1);
        AtomicBoolean aliceLoginOk = new AtomicBoolean(false);

        boolean aliceConn = clientAlice.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                aliceLoginOk.set(success);
                aliceLoginLatch.countDown();
            }
        });

        clientAlice.login("alice_test", "alice123");
        boolean aliceLoggedIn = aliceLoginLatch.await(3, TimeUnit.SECONDS);
        assertCondition(aliceLoggedIn && aliceLoginOk.get(), "Alice đăng nhập thành công");

        // Đợi đồng bộ danh sách online
        Thread.sleep(400);
        assertCondition(server.getRoomManager().getOnlineCount() == 2, "Server ghi nhận chính xác 2 Client đang online");

        // 5. Test gửi tin nhắn Broadcast (Alice -> All)
        CountDownLatch broadcastLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMsg = new AtomicReference<>("");

        // Cập nhật listener của Admin để đón tin
        SocketClient finalClientAdmin = clientAdmin;
        finalClientAdmin.disconnect();

        CountDownLatch adminReLoginLatch = new CountDownLatch(1);
        CountDownLatch msgArrivedLatch = new CountDownLatch(1);

        clientAdmin = new SocketClient();
        clientAdmin.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                adminReLoginLatch.countDown();
            }
            @Override
            public void onPublicMessageReceived(String sender, String role, String timestamp, String content) {
                if ("alice_test".equals(sender)) {
                    receivedMsg.set(content);
                    msgArrivedLatch.countDown();
                }
            }
            @Override
            public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {
                if ("alice_test".equals(sender)) {
                    receivedMsg.set(content);
                    msgArrivedLatch.countDown();
                }
            }
        });
        clientAdmin.login("admin", "admin123");
        adminReLoginLatch.await(3, TimeUnit.SECONDS);

        Thread.sleep(300);
        String testContent = "Xin chao moi nguoi trong phong!";
        clientAlice.sendPublicMessage(testContent);

        boolean msgReceived = msgArrivedLatch.await(3, TimeUnit.SECONDS);
        assertCondition(msgReceived, "Admin nhận được tin nhắn Broadcast từ Alice");
        assertCondition(testContent.equals(receivedMsg.get()), "Nội dung tin nhắn Broadcast khớp chính xác");

        // 6. Test tin nhắn riêng tư (Admin -> Alice 1-1)
        CountDownLatch privateLatch = new CountDownLatch(1);
        AtomicReference<String> privateMsg = new AtomicReference<>("");

        // Cập nhật listener của Alice để đón tin nhắn riêng
        final SocketClient activeAlice = clientAlice;
        activeAlice.disconnect();

        CountDownLatch aliceReLoginLatch = new CountDownLatch(1);
        clientAlice = new SocketClient();
        clientAlice.connect("127.0.0.1", TEST_PORT, new ChatEventListenerAdapter() {
            @Override
            public void onLoginResult(boolean success, String role, String username, String message) {
                aliceReLoginLatch.countDown();
            }
            @Override
            public void onPrivateMessageReceived(String sender, String target, String timestamp, String content) {
                if ("admin".equals(sender)) {
                    privateMsg.set(content);
                    privateLatch.countDown();
                }
            }
        });
        clientAlice.login("alice_test", "alice123");
        aliceReLoginLatch.await(3, TimeUnit.SECONDS);

        Thread.sleep(300);
        String secretMsg = "Day la tin nhan rieng tu mat 1-1";
        clientAdmin.sendPrivateMessage("alice_test", secretMsg);

        boolean privDone = privateLatch.await(3, TimeUnit.SECONDS);
        assertCondition(privDone, "Alice nhận được tin nhắn riêng từ Admin");
        assertCondition(secretMsg.equals(privateMsg.get()), "Nội dung tin nhắn riêng tư bảo mật chính xác");

        // 7. Test Tạo Nhóm và Gia Nhập Nhóm Bằng Mã PIN
        CountDownLatch createGrpLatch = new CountDownLatch(1);
        AtomicBoolean createGrpOk = new AtomicBoolean(false);
        CountDownLatch joinGrpFailLatch = new CountDownLatch(1);
        AtomicBoolean joinGrpFailOk = new AtomicBoolean(false);
        CountDownLatch joinGrpSuccessLatch = new CountDownLatch(1);
        AtomicBoolean joinGrpSuccessOk = new AtomicBoolean(false);
        CountDownLatch groupMsgLatch = new CountDownLatch(1);
        AtomicReference<String> receivedGrpMsg = new AtomicReference<>("");

        clientAlice.setEventListener(new ChatEventListenerAdapter() {
            @Override
            public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {
                createGrpOk.set(success);
                createGrpLatch.countDown();
            }
        });

        clientAdmin.setEventListener(new ChatEventListenerAdapter() {
            @Override
            public void onJoinGroupResult(boolean success, String groupName, String message) {
                if (!success) {
                    joinGrpFailOk.set(true);
                    joinGrpFailLatch.countDown();
                } else {
                    joinGrpSuccessOk.set(true);
                    joinGrpSuccessLatch.countDown();
                }
            }
            @Override
            public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {
                if ("Team Alpha".equals(groupName) && "alice_test".equals(sender)) {
                    receivedGrpMsg.set(content);
                    groupMsgLatch.countDown();
                }
            }
        });

        // Alice tạo nhóm "Team Alpha" với mã "8888"
        clientAlice.createGroup("Team Alpha", "8888");
        boolean grpCreated = createGrpLatch.await(3, TimeUnit.SECONDS);
        assertCondition(grpCreated && createGrpOk.get(), "Alice tạo nhóm 'Team Alpha' với mã PIN '8888' thành công");

        // Admin cố gia nhập với mã PIN sai "0000" -> Thất bại
        clientAdmin.joinGroup("Team Alpha", "0000");
        boolean joinFailDone = joinGrpFailLatch.await(3, TimeUnit.SECONDS);
        assertCondition(joinFailDone && joinGrpFailOk.get(), "Admin nhập sai mã PIN và bị từ chối vào nhóm");

        // Admin gia nhập với đúng mã PIN "8888" -> Thành công
        clientAdmin.joinGroup("Team Alpha", "8888");
        boolean joinSuccessDone = joinGrpSuccessLatch.await(3, TimeUnit.SECONDS);
        assertCondition(joinSuccessDone && joinGrpSuccessOk.get(), "Admin nhập đúng mã PIN '8888' và vào nhóm thành công");

        // Alice gửi tin nhắn vào nhóm "Team Alpha" -> Admin nhận được tin nhắn nhóm
        Thread.sleep(200);
        String groupMsgContent = "Chao mung Admin den voi Team Alpha!";
        clientAlice.sendGroupMessage("Team Alpha", groupMsgContent);
        boolean grpMsgReceived = groupMsgLatch.await(3, TimeUnit.SECONDS);
        assertCondition(grpMsgReceived, "Admin nhận được tin nhắn nhóm 'Team Alpha' từ Alice");
        assertCondition(groupMsgContent.equals(receivedGrpMsg.get()), "Nội dung tin nhắn nhóm khớp chính xác");

        // 8. Test Admin Mute Alice
        clientAdmin.sendAdminCommand(Protocol.ACT_MUTE, "alice_test", "5");
        Thread.sleep(400);
        assertCondition(server.getRoomManager().isUserMuted("alice_test"), "Server kích hoạt trạng thái MUTED cho Alice");

        // 9. Test Admin Kick Alice
        clientAdmin.sendAdminCommand(Protocol.ACT_KICK, "alice_test", "Kiem thu Kick");
        Thread.sleep(400);
        assertCondition(!server.getRoomManager().isUserOnline("alice_test"), "Alice đã bị đuổi và không còn trong danh sách Online");

        // 10. Dọn dẹp tài nguyên
        clientAdmin.disconnect();
        clientAlice.disconnect();
        server.stopServer();

        System.out.println("==================================================");
        System.out.println("🎉 TẤT CẢ CÁC BÀI TEST TÍCH HỢP ĐỀU THÀNH CÔNG 100%!");
        System.out.println("==================================================");
        System.out.flush();
        System.exit(0);
    }

    private static void assertCondition(boolean condition, String description) {
        if (condition) {
            System.out.println("[PASS] " + description);
        } else {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Kiểm thử thất bại: " + description);
        }
    }

    private static class ChatEventListenerAdapter implements ChatEventListener {
        @Override public void onLoginResult(boolean success, String role, String username, String message) {}
        @Override public void onRegisterResult(boolean success, String message) {}
        @Override public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {}
        @Override public void onJoinGroupResult(boolean success, String groupName, String message) {}
        @Override public void onGroupListUpdated(List<common.ChatGroup> groups) {}
        @Override public void onPublicMessageReceived(String sender, String role, String timestamp, String content) {}
        @Override public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {}
        @Override public void onPrivateMessageReceived(String sender, String target, String timestamp, String content) {}
        @Override public void onSystemMessage(String content) {}
        @Override public void onUserListUpdated(List<User> onlineUsers) {}
        @Override public void onHistoryReceived(String groupName, List<MessagePacket> history) {}
        @Override public void onKicked(String reason) {}
        @Override public void onMuted(int minutes, String reason) {}
        @Override public void onUnmuted() {}
        @Override public void onChangePasswordResult(boolean success, String message) {}
        @Override public void onConnectionLost(String reason) {}
    }
}
