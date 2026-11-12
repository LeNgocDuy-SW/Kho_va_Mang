package client.ui;

import client.network.SocketClient;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * CreateGroupDialog: Hộp thoại tạo nhóm chat mới có mã bảo vệ (Group Code).
 */
public class CreateGroupDialog extends JDialog {
    private final SocketClient socketClient;
    private PillTextField txtGroupName;
    private PillTextField txtGroupCode;
    private PillButton btnCreate;

    public CreateGroupDialog(JFrame parent, SocketClient socketClient) {
        super(parent, "Tạo Nhóm Chat Mới", true);
        this.socketClient = socketClient;
        initComponents();
    }

    private void initComponents() {
        setSize(420, 320);
        setResizable(false);
        setLocationRelativeTo(getParent());

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(UIUtils.BG_MAIN);
        root.setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel formCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        formCard.setOpaque(false);
        formCard.setLayout(new GridLayout(4, 1, 0, 8));
        formCard.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel lblTitle = new JLabel("✨ Tạo Phòng / Nhóm Mới", JLabel.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(UIUtils.TEXT_DARK);
        formCard.add(lblTitle);

        txtGroupName = new PillTextField("Tên nhóm (ví dụ: Team Dự Án)...", "👥");
        txtGroupName.setPreferredSize(new Dimension(300, 36));
        formCard.add(txtGroupName);

        txtGroupCode = new PillTextField("Mã nhóm / PIN (ví dụ: 1234)...", "🔑");
        txtGroupCode.setPreferredSize(new Dimension(300, 36));
        formCard.add(txtGroupCode);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.setOpaque(false);

        PillButton btnCancel = new PillButton("Hủy", PillButton.Style.SECONDARY);
        btnCancel.setPreferredSize(new Dimension(90, 36));
        btnCancel.addActionListener(e -> dispose());

        btnCreate = new PillButton("Tạo Nhóm", PillButton.Style.PRIMARY);
        btnCreate.setPreferredSize(new Dimension(130, 36));
        btnCreate.addActionListener(e -> doCreateGroup());

        btnRow.add(btnCancel);
        btnRow.add(btnCreate);
        formCard.add(btnRow);

        root.add(formCard, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void doCreateGroup() {
        String name = txtGroupName.getText().trim();
        String code = txtGroupCode.getText().trim();

        if (name.isEmpty() || code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Tên nhóm và Mã nhóm!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        socketClient.createGroup(name, code);
        dispose();
    }
}

