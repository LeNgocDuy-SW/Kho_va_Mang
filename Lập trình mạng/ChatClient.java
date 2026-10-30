import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            Scanner scanner = new Scanner(System.in, "UTF-8");

            // Bước 1: Thực hiện Đăng nhập trước khi vào phòng
            while (true) {
                System.out.print("Tài khoản (admin / duy / hai / long): ");
                String user = scanner.nextLine().trim();
                System.out.print("Mật khẩu (mặc định: 123): ");
                String pass = scanner.nextLine().trim();

                // Đóng gói theo giao thức: LOGIN|username|password
                out.println("LOGIN|" + user + "|" + pass);

                String response = in.readLine();
                if (response == null) return;

                if (response.startsWith("LOGIN_OK|")) {
                    String[] parts = response.split("\\|", 3);
                    System.out.println("\n✅ " + parts[2]);
                    printHelpMenu(parts[1]);
                    break;
                } else {
                    System.out.println("❌ " + response.replace("LOGIN_FAIL|", "") + "\n");
                }
            }

            // Bước 2: Bật luồng ngầm chuyên đọc dữ liệu từ Server gửi về
            Thread listener = new Thread(() -> {
                try {
                    String packet;
                    while ((packet = in.readLine()) != null) {
                        String[] parts = packet.split("\\|", 2);
                        String type = parts[0];
                        String content = parts.length > 1 ? parts[1] : "";

                        switch (type) {
                            case "CHAT":
                            case "HISTORY":
                                System.out.println(content);
                                break;
                            case "PRIVATE":
                                System.out.println("🔒 " + content);
                                break;
                            case "SERVER":
                                System.out.println("📢 [HỆ THỐNG] " + content);
                                break;
                            case "ERROR":
                                System.out.println("⚠️ [LỖI] " + content);
                                break;
                            case "KICKED":
                                System.out.println("🚫 " + content);
                                System.exit(0);
                                break;
                            case "ONLINE_LIST":
                                break;
                            default:
                                System.out.println(packet);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Đã ngắt kết nối khỏi Server.");
                    System.exit(0);
                }
            });
            listener.setDaemon(true);
            listener.start();

            // Bước 3: Luồng chính đọc lệnh từ bàn phím và đóng gói theo Protocol
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                if (input.equalsIgnoreCase("/quit")) {
                    break;
                } else if (input.equalsIgnoreCase("/online")) {
                    out.println("ONLINE");
                } else if (input.startsWith("/pm ")) {
                    // Cú pháp: /pm <user> <nội dung> -> MSG_PRIVATE|user|nội dung
                    String[] tokens = input.split("\\s+", 3);
                    if (tokens.length < 3) {
                        System.out.println("⚠️ Cú pháp đúng: /pm <tên_người_nhận> <tin_nhắn>");
                    } else {
                        out.println("MSG_PRIVATE|" + tokens[1] + "|" + tokens[2]);
                    }
                } else if (input.startsWith("/kick ")) {
                    String target = input.substring(6).trim();
                    out.println("ADMIN_KICK|" + target);
                } else if (input.startsWith("/mute ")) {
                    String target = input.substring(6).trim();
                    out.println("ADMIN_MUTE|" + target + "|true");
                } else if (input.startsWith("/unmute ")) {
                    String target = input.substring(8).trim();
                    out.println("ADMIN_MUTE|" + target + "|false");
                } else {
                    // Tin nhắn thông thường -> MSG_ALL|nội_dung
                    out.println("MSG_ALL|" + input);
                }
            }

        } catch (IOException e) {
            System.err.println("Không kết nối được Server: " + e.getMessage());
        }
    }

    private static void printHelpMenu(String role) {
        System.out.println("==================================================");
        System.out.println("HƯỚNG DẪN LỆNH PHÒNG CHAT:");
        System.out.println(" - Chat chung: Gõ trực tiếp và nhấn Enter");
        System.out.println(" - Chat mật 1-1: /pm <tên_user> <nội_dung>");
        System.out.println(" - Xem ai đang online: /online");
        if ("ADMIN".equals(role)) {
            System.out.println(" 👑 QUYỀN ADMIN:");
            System.out.println("   + Đuổi khỏi phòng: /kick <tên_user>");
            System.out.println("   + Cấm chat:        /mute <tên_user>");
            System.out.println("   + Mở khóa chat:    /unmute <tên_user>");
        }
        System.out.println(" - Thoát: /quit");
        System.out.println("==================================================\n");
    }
}