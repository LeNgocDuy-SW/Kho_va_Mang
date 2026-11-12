package client.ui;

import client.network.SocketClient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * JoinGroupDialog: Hộp thoại yêu cầu nhập Mã Nhóm (PIN) trước khi vào phòng.
 */
public class JoinGroupDialog extends JDialog {
    private final SocketClient socketClient;
    private final String groupName;
    private PillTextField txtGroupCode;
    private PillButton btnJoin;

    public JoinGroupDialog(JFrame parent, SocketClient socketClient, String groupName) {
        super(parent, "Yêu Cầu Mã Bảo Vệ Nhóm", true);
        this.socketClient = socketClient;
        this.groupName = groupName;
        initComponents();
    }

    private void initComponents() {
        setSize(400, 270);
        setResizable(false);
        setLocationRelativeTo(getParent());

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(UIUtils.BG_MAIN);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel formCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        formCard.setOpaque(false);
        formCard.setLayout(new GridLayout(3, 1, 0, 10));
        formCard.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel lblTitle = new JLabel("🔐 Nhập Mã Nhóm: [" + groupName + "]", JLabel.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitle.setForeground(UIUtils.TEXT_DARK);
        formCard.add(lblTitle);

        txtGroupCode = new PillTextField("Nhập mã nhóm (PIN) được cấp...", "🔑");
        txtGroupCode.setPreferredSize(new Dimension(280, 36));
        formCard.add(txtGroupCode);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.setOpaque(false);

        PillButton btnCancel = new PillButton("Hủy", PillButton.Style.SECONDARY);
        btnCancel.setPreferredSize(new Dimension(90, 36));
        btnCancel.addActionListener(e -> dispose());

        btnJoin = new PillButton("Vào Nhóm", PillButton.Style.PRIMARY);
        btnJoin.setPreferredSize(new Dimension(120, 36));
        btnJoin.addActionListener(e -> doJoinGroup());

        btnRow.add(btnCancel);
        btnRow.add(btnJoin);
        formCard.add(btnRow);

        root.add(formCard, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void doJoinGroup() {
        String code = txtGroupCode.getText().trim();
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập mã nhóm!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        socketClient.joinGroup(groupName, code);
        dispose();
    }
}

