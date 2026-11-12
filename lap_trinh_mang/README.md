# Đồ Án Lập Trình Mạng: Hệ Thống Chat Room Nhiều Người (Multi-client Chat Server)

Hệ thống phòng chat trực tuyến đa người dùng thời gian thực (Real-time Multi-client Chat Room) được xây dựng theo mô hình kiến trúc **Client - Server** bằng ngôn ngữ **Java**, sử dụng **Java Socket TCP Đa luồng (Multi-threading)**, giao diện đồ họa **Java Swing**, và cơ sở dữ liệu quan hệ **SQLite**.

Dự án được phân chia module hóa chặt chẽ theo đúng **Kế hoạch phân công công việc 4 thành viên** để đảm bảo không xung đột mã nguồn và hỗ trợ báo cáo bảo vệ đồ án chuyên nghiệp.

---

## 👥 Phân Chia Vai Trò & Trách Nhiệm Chi Tiết (4 Thành Viên)

| Thành viên | Phụ trách chính | Module & Lớp phụ trách | Nhiệm vụ kỹ thuật |
| :--- | :--- | :--- | :--- |
| **Thành viên 1** | **Core Server, Concurrency & Tích hợp** | `common/Protocol.java`<br>`server/ChatServer.java`<br>`server/ClientHandler.java`<br>`server/RoomManager.java`<br>`server/ui/ServerGUI.java`<br>`server/ServerMain.java` | • Thiết kế giao thức truyền tin (Application Protocol)<br>• Khởi tạo `ServerSocket`, quản lý luồng bằng `ExecutorService` (Thread Pool)<br>• Quản lý danh sách online thread-safe (`ConcurrentHashMap`)<br>• Định tuyến tin nhắn: Broadcast (phòng chung), Unicast (chat riêng 1-1)<br>• Xây dựng giao diện Server Monitor xem log, quản lý kết nối, kick client |
| **Thành viên 2** | **Backend & Security (Auth, Phân quyền, DB)** | `common/User.java`<br>`common/MessagePacket.java`<br>`server/db/DatabaseConnection.java`<br>`server/db/UserDAO.java`<br>`server/db/MessageDAO.java` | • Thiết kế lược đồ CSDL SQLite (`users`, `messages`), bật chế độ WAL<br>• Mã hóa mật khẩu bảo mật chuẩn **SHA-256** kết hợp Salt<br>• Module Đăng nhập / Đăng ký, chống đăng nhập trùng lặp 1 tài khoản ở 2 nơi<br>• Phân quyền RBAC: User thường vs Admin (Kick, Mute, Ban, Alert, Clear History)<br>• Lưu trữ và nạp lại 30 tin nhắn gần nhất khi login |
| **Thành viên 3** | **Client Core (Xử lý Mạng & Luồng dữ liệu)** | `client/network/ChatEventListener.java`<br>`client/network/SocketClient.java`<br>`client/network/ServerListener.java` | • Xây dựng `SocketClient` kết nối TCP Socket tới Server qua IP và Port<br>• Quản lý luồng đọc bất đồng bộ `ServerListener` (Listener Thread) không làm đơ UI<br>• Đóng gói (Serialize) và giải mã (Deserialize) gói tin theo Protocol<br>• Xử lý an toàn ngoại lệ mạng: timeout, mất kết nối, tự đóng socket khi thoát |
| **Thành viên 4** | **Frontend & GUI (Giao diện người dùng)** | `client/ui/LoginFrame.java`<br>`client/ui/ChatRoomFrame.java`<br>`client/ui/ChangePasswordDialog.java`<br>`client/ClientMain.java` | • Thiết kế giao diện Đăng nhập / Đăng ký và cấu hình IP/Port Server<br>• Thiết kế phòng chat Messenger: danh sách online, icon vai trò 👑 Admin / 👤 User<br>• Khung chat HTML hiện đại, phân biệt bong bóng chat của mình, người khác, tin mật 1-1<br>• Thanh công cụ và menu chuột phải Quản trị Admin (Kick, Mute, Ban)<br>• Gắn sự kiện (Event Listener), hỗ trợ phím Enter để gửi |

---

## 📡 Đặc Tả Giao Thức Bản Tin (Application Layer Protocol)

Hệ thống sử dụng giao thức chuỗi văn bản UTF-8 phân tách bởi ký tự gạch đứng `|`:

| Chiều gửi | Header | Cú pháp Gói tin | Ý nghĩa / Xử lý |
| :--- | :--- | :--- | :--- |
| **Client $\rightarrow$ Server** | `REGISTER` | `REGISTER\|username\|password\|role` | Yêu cầu đăng ký tài khoản mới (`USER` hoặc `ADMIN`) |
| **Server $\rightarrow$ Client** | `REGISTER_RES`| `REGISTER_RES\|SUCCESS/FAILED\|thông_báo` | Phản hồi kết quả đăng ký |
| **Client $\rightarrow$ Server** | `LOGIN` | `LOGIN\|username\|password` | Yêu cầu đăng nhập vào hệ thống |
| **Server $\rightarrow$ Client** | `LOGIN_RES` | `LOGIN_RES\|SUCCESS\|role\|username`<br>`LOGIN_RES\|FAILED\|lý_do` | Phản hồi kết quả đăng nhập kèm vai trò quyền hạn |
| **Client $\rightarrow$ Server** | `MSG_ALL` | `MSG_ALL\|nội_dung_tin_nhắn` | Gửi tin nhắn công khai vào phòng chat chung |
| **Server $\rightarrow$ Client** | `NEW_MSG` | `NEW_MSG\|sender\|role\|timestamp\|nội_dung` | Broadcast tin nhắn mới tới toàn bộ các Client |
| **Client $\rightarrow$ Server** | `MSG_PRIVATE` | `MSG_PRIVATE\|target_username\|nội_dung` | Gửi tin nhắn riêng tư bảo mật 1-1 |
| **Server $\rightarrow$ Client** | `NEW_PRIVATE_MSG`| `NEW_PRIVATE_MSG\|sender\|target\|time\|nội_dung` | Chuyển tiếp tin nhắn riêng tới đích và phản hồi người gửi |
| **Server $\rightarrow$ Client** | `SYSTEM_MSG` | `SYSTEM_MSG\|nội_dung_thông_báo` | Thông báo trạng thái hệ thống |
| **Server $\rightarrow$ Client** | `HISTORY` | `HISTORY\|count\|msg1;;msg2;;...` | Trả về 30 tin nhắn gần nhất sau khi đăng nhập |
| **Client $\rightarrow$ Server** | `GET_USERS` | `GET_USERS` | Yêu cầu cập nhật danh sách người dùng online |
| **Server $\rightarrow$ Client** | `USER_LIST` | `USER_LIST\|u1:ROLE:STATUS,u2:ROLE:STATUS` | Cập nhật danh sách và trạng thái (Active/Muted) |
| **Client(Admin) $\rightarrow$ Server** | `ADMIN_CMD` | `ADMIN_CMD\|KICK\|target\|lý_do`<br>`ADMIN_CMD\|MUTE\|target\|phút`<br>`ADMIN_CMD\|UNMUTE\|target`<br>`ADMIN_CMD\|BAN\|target\|lý_do`<br>`ADMIN_CMD\|ALERT\|nội_dung`<br>`ADMIN_CMD\|CLEAR_HISTORY` | Các lệnh quản trị, kiểm duyệt của Admin |
| **Server $\rightarrow$ Client** | `KICKED` | `KICKED\|lý_do` | Buộc Client bị đuổi phải đóng kết nối |
| **Server $\rightarrow$ Client** | `MUTED` | `MUTED\|phút\|lý_do` | Thông báo khóa quyền phát ngôn của Client |
| **Client $\rightarrow$ Server** | `CHANGE_PASS` | `CHANGE_PASS\|mật_khẩu_cũ\|mật_khẩu_mới` | Đổi mật khẩu tài khoản |
| **Client $\rightarrow$ Server** | `LOGOUT` | `LOGOUT` | Đăng xuất an toàn |

---

## 📂 Cấu Trúc Thư Mục Dự Án (Chuẩn Chỉ Không Xung Đột)

```text
MultiClientChatProject/
├── common/                                 # Dùng chung cho Client & Server
│   ├── Protocol.java                       # Định nghĩa hằng số Header giao thức
│   ├── User.java                           # Thực thể người dùng
│   └── MessagePacket.java                  # Thực thể đóng gói tin nhắn
├── server/                                 # Mã nguồn Máy chủ (TV1 & TV2)
│   ├── ChatServer.java                     # Lõi ServerSocket & ThreadPool
│   ├── ClientHandler.java                  # Luồng xử lý cho từng Client
│   ├── RoomManager.java                    # Quản lý danh sách online & định tuyến
│   ├── ServerEventListener.java            # Interface callback sự kiện Server
│   ├── ServerMain.java                     # Điểm chạy Server (hỗ trợ cả GUI và CLI)
│   ├── ui/
│   │   └── ServerGUI.java                  # Giao diện giám sát & quản trị Server
│   └── db/
│       ├── DatabaseConnection.java         # Kết nối JDBC SQLite & băm SHA-256
│       ├── UserDAO.java                    # Thao tác CSDL người dùng
│       └── MessageDAO.java                 # Thao tác CSDL lịch sử tin nhắn
├── client/                                 # Mã nguồn Máy trạm (TV3 & TV4)
│   ├── ClientMain.java                     # Điểm chạy Client GUI
│   ├── network/
│   │   ├── SocketClient.java               # Kết nối Socket TCP & gửi gói tin
│   │   ├── ServerListener.java             # Luồng nền lắng nghe Server
│   │   └── ChatEventListener.java         # Interface callback sự kiện về UI
│   └── ui/
│       ├── LoginFrame.java                 # Giao diện Đăng nhập & Đăng ký
│       ├── ChatRoomFrame.java              # Giao diện Phòng chat & Bảng điều khiển Admin
│       └── ChangePasswordDialog.java       # Hộp thoại đổi mật khẩu
├── test/
│   └── SystemIntegrationTest.java          # Bộ kiểm thử tích hợp tự động toàn diện
├── lib/                                    # Thư viện ngoài
│   ├── sqlite-jdbc.jar                     # JDBC Driver cho SQLite
│   ├── slf4j-api.jar
│   └── slf4j-nop.jar
├── compile.bat                             # Script biên dịch toàn bộ dự án
├── run_server.bat                          # Script chạy Server GUI
├── run_server_cli.bat                      # Script chạy Server CLI
├── run_client.bat                          # Script chạy Client GUI
├── run_demo_two_clients.bat                # Script chạy thử nghiệm 1 Server + 2 Client
└── run_tests.bat                           # Script chạy bài test tự động
```

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### 1. Yêu cầu hệ thống
- Đã cài đặt **JDK 11** trở lên (Khuyến nghị Java 17 hoặc Java 21/25).
- Đảm bảo lệnh `javac` và `java` có trong biến môi trường PATH.

### 2. Biên dịch nhanh
Chạy file `compile.bat` hoặc gõ lệnh:
```powershell
javac -encoding UTF-8 -cp ".;lib/*" common/*.java server/*.java server/db/*.java server/ui/*.java client/*.java client/network/*.java client/ui/*.java test/*.java
```

### 3. Khởi chạy Server
- **Chế độ Giao diện đồ họa GUI (Khuyến nghị khi báo cáo)**: Chạy `run_server.bat` hoặc:
  ```powershell
  java -Dfile.encoding=UTF-8 -cp ".;lib/*" server.ServerMain
  ```
- **Chế độ Dòng lệnh CLI Console**: Chạy `run_server_cli.bat` hoặc:
  ```powershell
  java -Dfile.encoding=UTF-8 -cp ".;lib/*" server.ServerMain --cli
  ```

### 4. Khởi chạy Client
Chạy `run_client.bat` hoặc:
```powershell
java -Dfile.encoding=UTF-8 -cp ".;lib/*" client.ClientMain
```

### 5. Chạy thử nghiệm đồng thời 1 Server + 2 Client với 1-Click
Chạy file `run_demo_two_clients.bat`. Hệ thống sẽ tự động bật 1 cửa sổ Server và 2 cửa sổ Client để người dùng thử nghiệm chat giữa 2 tài khoản ngay lập tức!

### 6. Chạy Kiểm thử Tự động (Integration Tests)
Chạy file `run_tests.bat` hoặc:
```powershell
java -Dfile.encoding=UTF-8 -cp ".;lib/*" test.SystemIntegrationTest
```

---

## 🔑 Tài Khoản Mặc Định Được Seed Sẵn Trong CSDL SQLite
- **Tài khoản Quản trị viên (ADMIN)**:
  - Username: `admin`
  - Mật khẩu: `admin123`
  - Quyền hạn: Toàn quyền Kick, Mute, Ban, Gửi thông báo khẩn, Xóa lịch sử.
- **Tài khoản Thành viên thường (USER)**:
  - Username: `user1`
  - Mật khẩu: `123456`
  - Quyền hạn: Chat phòng chung, chat riêng tư 1-1, xem danh sách online, đổi mật khẩu.
*(Người dùng có thể tự đăng ký thêm tài khoản mới bất kỳ lúc nào trên màn hình Đăng ký).*

