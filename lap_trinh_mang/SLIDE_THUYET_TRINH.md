# KỊCH BẢN THUYẾT TRÌNH BẢO VỆ ĐỒ ÁN LẬP TRÌNH MẠNG
## ĐỀ TÀI: MULTI-CLIENT CHAT SERVER & MODERN MESSENGER CLIENT
**Thời lượng dự kiến:** 10 - 15 phút | **Đối tượng:** Hội đồng chấm thi môn Lập trình mạng

---

### SLIDE 1: GIỚI THIỆU TỔNG QUAN
- **Tiêu đề:** Hệ Thống Phòng Chat Thời Gian Thực Đa Nền Tảng (Multi-Client Chat System).
- **Thành viên nhóm:** 4 thành viên.
- **Mục tiêu đề tài:**
  - Áp dụng kỹ thuật lập trình Socket TCP hướng luồng tin cậy.
  - Quản lý đa nhiệm đồng thời (Multi-threading Concurrency) với ThreadPool.
  - Thiết kế Giao diện người dùng hiện đại 3 cột (chuẩn thiết kế Dribbble / Modern Messenger).
  - Quản lý dữ liệu bền vững bằng SQLite với cơ chế WAL (Write-Ahead Logging).

---

### SLIDE 2: KIẾN TRÚC HỆ THỐNG TỔNG THỂ
- **Mô hình kiến trúc:** Client - Server tập trung.
- **Cổng kết nối:** TCP Port `5000`.
- **Sơ đồ luồng:**
  - Client 1 & Client 2 $\leftrightarrow$ Socket TCP $\leftrightarrow$ `ChatServer`.
  - Server sử dụng `CachedThreadPool` để cấp phát luồng `ClientHandler` cho từng kết nối.
  - Quản lý trạng thái bằng cấu trúc dữ liệu an toàn luồng `ConcurrentHashMap`.
  - Tầng dữ liệu tương tác qua JDBC SQLite (`chat.db`).

---

### SLIDE 3: BẢN TIN GIAO THỨC TẦNG ỨNG DỤNG (APPLICATION PROTOCOL)
- **Quy ước đóng gói:** Chuỗi ký tự UTF-8 phân tách bằng ký tự pipe (`|`).
- **Các nhóm lệnh chính:**
  - `REGISTER` / `LOGIN`: Xác thực tài khoản, kiểm tra chống trùng lặp.
  - `MSG_ALL`: Broadcast tin nhắn công khai trong phòng.
  - `MSG_PRIVATE`: Gửi tin nhắn riêng tư bảo mật 1-1.
  - `ADMIN_CMD`: Kiểm duyệt (KICK, MUTE có thời hạn, BAN tài khoản, ALERT toàn phòng).
  - `HISTORY`: Đồng bộ lại tin nhắn cũ khi người dùng đăng nhập.

---

### SLIDE 4: ĐA LUỒNG & AN TOÀN LUỒNG (CONCURRENCY)
- **Phía Server:**
  - `ServerSocket.accept()` chạy trên luồng nền `Server-Accept-Thread`.
  - Tối ưu tài nguyên CPU và RAM với `Executors.newCachedThreadPool()` (tự giải phóng luồng nhàn rỗi sau 60s).
  - Bảo vệ luồng ghi Socket bằng `synchronized sendPacket()`.
- **Phía Client:**
  - Tách bạch luồng giao diện Swing (Event Dispatch Thread - EDT) và luồng mạng nền (`ServerListener Thread`).
  - Đẩy dữ liệu bất đồng bộ qua `SwingUtilities.invokeLater()`, cam kết không bị đơ giao diện (UI Freeze).
  - Đóng kết nối an toàn chống Deadlock: Đóng socket trước để unblock `readLine()`.

---

### SLIDE 5: CƠ SỞ DỮ LIỆU SQLITE & BẢO MẬT (BACKEND & SECURITY)
- **Chế độ WAL (`PRAGMA journal_mode=WAL;`):** Cho phép các luồng đọc và ghi diễn ra đồng thời mà không bị khóa file database.
- **Bảo mật mật khẩu:**
  - Băm mật khẩu chuẩn **SHA-256** kết hợp khóa Salt bí mật.
  - Chống tấn công dò quét Rainbow Table.
- **Lưu trữ lịch sử:**
  - Bảng `users` (tài khoản, phân quyền, trạng thái).
  - Bảng `messages` (phòng chat, người gửi, người nhận, thời gian, nội dung).

---

### SLIDE 6: THIẾT KẾ GIAO DIỆN HIỆN ĐẠI (MODERN UI/UX)
- **Bố cục 3 cột chuẩn thiết kế quốc tế:**
  1. **Cột trái:** Profile người dùng (Avatar tròn, Tên), Thanh tìm kiếm Pill `🔍 Search`, Thẻ Favourites có ngăn xếp avatar (+5), Danh sách nhóm chat Groups.
  2. **Cột giữa:** Khung chat trực quan, bong bóng chat Pastel (Xanh pastel cho tin của mình, Trắng viền xám cho người khác, Tím lavender cho tin mật 1-1), Thanh nhập liệu Pill tích hợp Emoji và gửi file đính kèm.
  3. **Cột phải:** Group Info chi tiết, danh sách thành viên trực tuyến kèm chấm trạng thái `🟢 Online` và huy hiệu `👑 Admin`.
- **Công nghệ đồ họa:** Java Swing kết hợp `Graphics2D` khử răng cưa toàn diện (`ANTIALIAS_ON`).

---

### SLIDE 7: PHÂN QUYỀN & KIỂM DUYỆT (ROLE-BASED ACCESS CONTROL)
- **Người dùng thường (USER):** Tham gia chat phòng chung, nhắn tin mật 1-1, xem danh sách online, đổi mật khẩu.
- **Quản trị viên (ADMIN):**
  - Huy hiệu 👑 màu vàng hoàng gia.
  - Quyền đuổi khỏi phòng (`Kick`).
  - Quyền cấm chat có thời hạn (`Mute 5m / 15m / Vĩnh viễn`).
  - Quyền mở cấm chat (`Unmute`).
  - Quyền khóa tài khoản vĩnh viễn (`Ban`).
  - Phát thông báo khẩn cấp toàn phòng (`Broadcast Alert`).
  - Xóa sạch lịch sử chat (`Clear History`).

---

### SLIDE 8: DEMO TRỰC TIẾP SẢN PHẨM (LIVE DEMO)
- Khởi động Server GUI với bảng điều khiển giám sát.
- Mở đồng thời 2 Client:
  1. `admin` (Quản trị viên)
  2. `user1` (Thành viên)
- Thao tác thực tế:
  - Chat công khai giữa 2 tài khoản.
  - Chat riêng tư mật 1-1.
  - Đính kèm tệp và chèn Emoji.
  - Admin thực hiện Mute và Kick tài khoản vi phạm.
  - Đăng xuất và kiểm tra lịch sử chat được lưu trong CSDL.

---

### SLIDE 9: KẾT LUẬN & ĐÁNH GIÁ
- Hoàn thành 100% mục tiêu của đề tài Lập trình mạng.
- Kiến trúc phân tầng rõ ràng, sẵn sàng mở rộng.
- Cảm ơn Thầy/Cô và Hội đồng đã lắng nghe!

