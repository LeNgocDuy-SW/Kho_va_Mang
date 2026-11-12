package client;

import client.ui.LoginFrame;
import javax.swing.*;

/**
 * ClientMain: Điểm khởi chạy chính của ứng dụng Client (Giao diện người dùng Swing).
 */
public class ClientMain {
    public static void main(String[] args) {
        // Thiết lập Look and Feel hệ thống cho giao diện hiện đại, bóng bẩy
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Đảm bảo chạy trên luồng Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}

