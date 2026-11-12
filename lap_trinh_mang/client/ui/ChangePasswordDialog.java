package client.ui;

import client.network.SocketClient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * ChangePasswordDialog: Hộp thoại đổi mật khẩu người dùng.
 */
public class ChangePasswordDialog extends JDialog {
    private final SocketClient client;
    private JPasswordField txtOldPass;
    private JPasswordField txtNewPass;
    private JPasswordField txtConfirmPass;
    private JButton btnSubmit;

    public ChangePasswordDialog(JFrame parent, SocketClient client) {
        super(parent, "Đổi Mật Khẩu", true);
        this.client = client;
        initComponents();
    }

    private void initComponents() {
        setSize(380, 260);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 12));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);

        panel.add(new JLabel("Mật khẩu hiện tại:"));
        txtOldPass = new JPasswordField();
        panel.add(txtOldPass);

        panel.add(new JLabel("Mật khẩu mới:"));
        txtNewPass = new JPasswordField();
        panel.add(txtNewPass);

        panel.add(new JLabel("Nhập lại mật khẩu mới:"));
        txtConfirmPass = new JPasswordField();
        panel.add(txtConfirmPass);

        JButton btnCancel = new JButton("Hủy");
        btnCancel.addActionListener(e -> dispose());
        panel.add(btnCancel);

        btnSubmit = new JButton("Xác Nhận");
        btnSubmit.setBackground(new Color(13, 110, 253));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSubmit.addActionListener(e -> doChangePassword());
        panel.add(btnSubmit);

        setContentPane(panel);
    }

    private void doChangePassword() {
        String oldPass = new String(txtOldPass.getPassword()).trim();
        String newPass = new String(txtNewPass.getPassword()).trim();
        String confirmPass = new String(txtConfirmPass.getPassword()).trim();

        if (oldPass.isEmpty() || newPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ mật khẩu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!newPass.equals(confirmPass)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu mới xác nhận không khớp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        client.changePassword(oldPass, newPass);
        dispose();
    }
}

