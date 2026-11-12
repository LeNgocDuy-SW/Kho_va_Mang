package server;

import server.ui.ServerGUI;
import javax.swing.*;
import java.util.Scanner;

/**
 * ServerMain: Điểm khởi chạy chính của Server.
 * Hỗ trợ cả 2 chế độ:
 * 1. Giao diện đồ họa trực quan (GUI - mặc định)
 * 2. Dòng lệnh không đầu (Console CLI - truyền tham số --cli hoặc --console)
 */
public class ServerMain {
    public static void main(String[] args) {
        boolean useCli = false;
        int port = ChatServer.DEFAULT_PORT;
        int webPort = ChatServer.DEFAULT_WEB_PORT;

        // Đọc biến môi trường PORT (dùng cho Hugging Face Spaces, Render, Railway...)
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                webPort = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }

        for (int i = 0; i < args.length; i++) {
            if ("--cli".equalsIgnoreCase(args[i]) || "--console".equalsIgnoreCase(args[i])) {
                useCli = true;
            } else if ("--port".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {}
            } else if ("--web-port".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try {
                    webPort = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (useCli) {
            runConsoleMode(port, webPort);
        } else {
            runGuiMode();
        }
    }

    private static void runGuiMode() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }

    private static void runConsoleMode(int port, int webPort) {
        System.out.println("==================================================");
        System.out.println("   MÁY CHỦ CHAT ROOM ĐA LUỒNG (CLI CONSOLE)       ");
        System.out.println("==================================================");

        ChatServer server = new ChatServer(port, webPort);
        server.setListener(new ServerEventListener() {
            @Override
            public void onLog(String logMessage) {
                System.out.println(logMessage);
            }
            @Override
            public void onClientConnected(String username, String role, String ip) {
                System.out.println("[+ ONLINE] " + username + " (" + role + ") từ " + ip);
            }
            @Override
            public void onClientDisconnected(String username) {
                System.out.println("[- OFFLINE] " + username + " đã thoát.");
            }
            @Override
            public void onMessageCountIncremented() {}
            @Override
            public void onOnlineListUpdated(java.util.List<common.User> list) {}
        });

        boolean ok = server.startServer();
        if (!ok) {
            System.err.println("[LỖI] Không thể mở Server tại cổng " + port);
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nĐang tắt Server...");
            server.stopServer();
        }));

        System.out.println("Nhập 'quit' hoặc 'exit' để tắt Server, 'users' để xem người online.");
        Scanner scanner = new Scanner(System.in);
        try {
            while (server.isRunning() && scanner.hasNextLine()) {
                String cmd = scanner.nextLine().trim();
                if ("quit".equalsIgnoreCase(cmd) || "exit".equalsIgnoreCase(cmd)) {
                    server.stopServer();
                    return;
                } else if ("users".equalsIgnoreCase(cmd)) {
                    System.out.println("Online: " + server.getRoomManager().getOnlineUsersList());
                }
            }
        } catch (Exception ignored) {}

        // Nếu System.in kết thúc (chạy nền hoặc qua script), tiếp tục duy trì Server chạy vô tận
        while (server.isRunning()) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}

