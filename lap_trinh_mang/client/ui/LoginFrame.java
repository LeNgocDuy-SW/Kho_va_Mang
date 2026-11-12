package client.ui;

import client.network.ChatEventListener;
import client.network.SocketClient;
import common.ChatGroup;
import common.MessagePacket;
import common.Protocol;
import common.User;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.net.InetAddress;
import java.util.List;

/**
 * LoginFrame: Giao diện Đăng nhập & Đăng ký Neumorphic / Soft UI
 * được thiết kế tỉ mỉ theo đúng chuẩn hình ảnh mẫu:
 * - Logo ngọn sóng/lửa 3D sắc nét trên thẻ bo tròn Neumorphism
 * - Tiêu đề "Welcome Back", phụ đề "Sign in to continue"
 * - Hộp nhập bo tròn dạng viên thuốc với icon 👤, 🔒 và nút ẩn/hiện mật khẩu 👁
 * - Checkbox "Remember Me" bo tròn và liên kết "Forgot Password?"
 * - Nút bấm lớn màu xanh dương Gradient kèm huy hiệu mũi tên trắng ->
 * - Đường kẻ phân cách "OR CONTINUE WITH"
 * - 4 nút Neumorphic squircle: Google, Discord, Facebook, và Cấu hình Server (Host/Port)
 * - Chuyển đổi linh hoạt giữa màn hình Đăng Nhập và Đăng Ký (Sign Up)
 */
public class LoginFrame extends JFrame implements ChatEventListener {
    // Cấu hình máy chủ
    private String serverHost = "127.0.0.1";
    private int serverPort = 5000;

    // Trạng thái giao diện
    private boolean isRegisterMode = false;
    private boolean showPassword = false;
    private boolean rememberMe = true;

    // Các trường nhập liệu
    private JTextField txtUser;
    private JPasswordField txtPass;
    private JComboBox<String> cbRole;
    private JLabel lblTitle;
    private JLabel lblSub;
    private JButton btnMainAction;
    private JLabel lblToggleMode;
    private JLabel lblStatus;

    private final SocketClient socketClient;

    public LoginFrame() {
        super("Multi-Client Chat - Welcome Back");
        this.socketClient = new SocketClient();
        initComponents();
    }

    private void initComponents() {
        setSize(440, 680);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Khung nền chính với màu Neumorphic và các khối cầu ánh sáng mờ ảo
        JPanel backgroundPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);

                int w = getWidth();
                int h = getHeight();

                // Nền Gradient chuyển từ trắng xám ánh xanh nhẹ
                GradientPaint bgPaint = new GradientPaint(0, 0, new Color(243, 246, 252), w, h, new Color(233, 240, 250));
                g2.setPaint(bgPaint);
                g2.fillRect(0, 0, w, h);

                // Khối cầu ánh sáng mờ góc trên trái
                g2.setColor(new Color(205, 225, 255, 110));
                g2.fillOval(-60, -60, 220, 220);
                g2.setColor(new Color(255, 255, 255, 140));
                g2.fillOval(-30, -30, 140, 140);

                // Khối cầu ánh sáng góc trên phải
                g2.setColor(new Color(210, 228, 255, 80));
                g2.fillOval(w - 110, 40, 160, 160);

                // Khối cầu ánh sáng góc dưới
                g2.setColor(new Color(220, 235, 255, 90));
                g2.fillOval(w - 140, h - 140, 200, 200);
                g2.fillOval(-50, h - 120, 160, 160);

                g2.dispose();
            }
        };
        backgroundPanel.setOpaque(true);

        // Thẻ trung tâm Neumorphic Card
        JPanel cardPanel = new JPanel();
        cardPanel.setOpaque(false);
        cardPanel.setPreferredSize(new Dimension(380, 630));
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setBorder(new EmptyBorder(10, 20, 14, 20));

        // 1. Logo 3D Wave/Flame trên thẻ Neumorphic squircle
        JPanel logoContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        logoContainer.setOpaque(false);
        logoContainer.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel logoSquircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = 28;

                // Bóng mờ Neumorphic ngoài (dưới phải)
                g2.setColor(new Color(200, 214, 235, 160));
                g2.fillRoundRect(4, 5, w - 8, h - 8, arc, arc);

                // Vùng sáng Neumorphic (trên trái)
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(1, 1, w - 5, h - 5, arc, arc);

                // Nền chính của squircle
                GradientPaint sqGrad = new GradientPaint(0, 0, new Color(255, 255, 255), 0, h, new Color(241, 246, 255));
                g2.setPaint(sqGrad);
                g2.fillRoundRect(2, 2, w - 6, h - 6, arc, arc);

                // Viền mờ tinh tế
                g2.setColor(new Color(230, 238, 248));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(2, 2, w - 6, h - 6, arc, arc);

                // --- VẼ BIỂU TƯỢNG 3D FLAME / WAVE RIBBONS ---
                int cx = w / 2;
                int cy = h / 2;

                // Dải sóng trái (Xanh biển sâu)
                Path2D.Double waveLeft = new Path2D.Double();
                waveLeft.moveTo(cx - 16, cy + 18);
                waveLeft.curveTo(cx - 24, cy + 6, cx - 18, cy - 8, cx - 6, cy - 14);
                waveLeft.curveTo(cx - 12, cy - 2, cx - 10, cy + 8, cx - 4, cy + 16);
                waveLeft.closePath();
                GradientPaint gradDarkBlue = new GradientPaint(cx - 20, cy + 18, new Color(13, 71, 200), cx - 6, cy - 14, new Color(37, 117, 252));
                g2.setPaint(gradDarkBlue);
                g2.fill(waveLeft);

                // Dải sóng giữa (Xanh ngọc lôi cuốn)
                Path2D.Double waveMid = new Path2D.Double();
                waveMid.moveTo(cx - 8, cy + 22);
                waveMid.curveTo(cx - 15, cy + 6, cx - 4, cy - 12, cx + 5, cy - 20);
                waveMid.curveTo(cx + 1, cy - 8, cx + 2, cy + 8, cx + 6, cy + 15);
                waveMid.curveTo(cx + 2, cy + 21, cx - 3, cy + 24, cx - 8, cy + 22);
                waveMid.closePath();
                GradientPaint gradMidBlue = new GradientPaint(cx - 10, cy + 22, new Color(29, 114, 254), cx + 5, cy - 20, new Color(96, 165, 250));
                g2.setPaint(gradMidBlue);
                g2.fill(waveMid);

                // Dải sóng phải (Trắng băng & Xanh nhạt sáng bóng)
                Path2D.Double waveRight = new Path2D.Double();
                waveRight.moveTo(cx + 3, cy + 14);
                waveRight.curveTo(cx - 1, cy + 2, cx + 4, cy - 12, cx + 14, cy - 22);
                waveRight.curveTo(cx + 12, cy - 10, cx + 18, cy + 4, cx + 9, cy + 12);
                waveRight.closePath();
                GradientPaint gradWhiteBlue = new GradientPaint(cx + 3, cy + 14, new Color(191, 219, 254), cx + 14, cy - 22, Color.WHITE);
                g2.setPaint(gradWhiteBlue);
                g2.fill(waveRight);

                g2.dispose();
            }
        };
        logoSquircle.setPreferredSize(new Dimension(84, 84));
        logoSquircle.setOpaque(false);
        logoContainer.add(logoSquircle);
        cardPanel.add(logoContainer);

        cardPanel.add(Box.createVerticalStrut(14));

        // 2. Tiêu đề "Welcome Back" & Phụ đề "Sign in to continue"
        lblTitle = new JLabel("Welcome Back");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblTitle.setForeground(new Color(16, 24, 40));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(lblTitle);

        cardPanel.add(Box.createVerticalStrut(4));

        lblSub = new JLabel("Sign in to continue");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(new Color(100, 116, 139));
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(lblSub);

        cardPanel.add(Box.createVerticalStrut(20));

        // 3. Khung nhập Tên đăng nhập (Username or Email) dạng Neumorphic Pill
        JPanel userFieldWrapper = createNeumorphicFieldPanel();
        userFieldWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconUser = new JLabel("  👤  ");
        iconUser.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        iconUser.setForeground(new Color(148, 163, 184));
        userFieldWrapper.add(iconUser, BorderLayout.WEST);

        txtUser = new JTextField();
        txtUser.setOpaque(false);
        txtUser.setBorder(null);
        txtUser.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtUser.setForeground(new Color(30, 41, 59));
        txtUser.setCaretColor(new Color(37, 117, 252));
        txtUser.putClientProperty("JTextField.placeholderText", "Username or Email");
        userFieldWrapper.add(txtUser, BorderLayout.CENTER);
        cardPanel.add(userFieldWrapper);

        cardPanel.add(Box.createVerticalStrut(12));

        // 4. Khung nhập Mật khẩu (Password) với nút ẩn/hiện 👁
        JPanel passFieldWrapper = createNeumorphicFieldPanel();
        passFieldWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel iconLock = new JLabel("  🔒  ");
        iconLock.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        iconLock.setForeground(new Color(148, 163, 184));
        passFieldWrapper.add(iconLock, BorderLayout.WEST);

        txtPass = new JPasswordField();
        txtPass.setOpaque(false);
        txtPass.setBorder(null);
        txtPass.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtPass.setForeground(new Color(30, 41, 59));
        txtPass.setCaretColor(new Color(37, 117, 252));
        passFieldWrapper.add(txtPass, BorderLayout.CENTER);

        JButton btnToggleEye = new JButton("👁");
        btnToggleEye.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        btnToggleEye.setBorder(new EmptyBorder(0, 0, 0, 12));
        btnToggleEye.setContentAreaFilled(false);
        btnToggleEye.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnToggleEye.setToolTipText("Hiện/Ẩn mật khẩu");
        btnToggleEye.addActionListener(e -> {
            showPassword = !showPassword;
            txtPass.setEchoChar(showPassword ? (char) 0 : '•');
            btnToggleEye.setText(showPassword ? "👁‍🗨" : "👁");
        });
        passFieldWrapper.add(btnToggleEye, BorderLayout.EAST);
        cardPanel.add(passFieldWrapper);

        // 4b. Hàng chọn Vai trò (Chỉ hiển thị khi ở chế độ Đăng Ký)
        cbRole = new JComboBox<>(new String[]{Protocol.ROLE_USER + " (Người Dùng)", Protocol.ROLE_ADMIN + " (Quản Trị Viên)"});
        cbRole.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cbRole.setMaximumSize(new Dimension(340, 36));
        cbRole.setAlignmentX(Component.CENTER_ALIGNMENT);
        cbRole.setVisible(false);
        cardPanel.add(cbRole);

        cardPanel.add(Box.createVerticalStrut(12));

        // 5. Hàng "Remember Me" và "Forgot Password?"
        JPanel rememberRow = new JPanel(new BorderLayout());
        rememberRow.setOpaque(false);
        rememberRow.setMaximumSize(new Dimension(340, 24));
        rememberRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JCheckBox chkRemember = new JCheckBox("Remember Me", rememberMe);
        chkRemember.setOpaque(false);
        chkRemember.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkRemember.setForeground(new Color(71, 85, 105));
        chkRemember.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chkRemember.addActionListener(e -> rememberMe = chkRemember.isSelected());
        rememberRow.add(chkRemember, BorderLayout.WEST);

        JLabel lblForgot = new JLabel("Forgot Password?");
        lblForgot.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblForgot.setForeground(new Color(37, 117, 252));
        lblForgot.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblForgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showForgotPasswordDialog();
            }
        });
        rememberRow.add(lblForgot, BorderLayout.EAST);
        cardPanel.add(rememberRow);

        cardPanel.add(Box.createVerticalStrut(16));

        // 6. Nút Đăng Nhập lớn màu Xanh Gradient kèm huy hiệu mũi tên ->
        btnMainAction = new JButton("Login") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = h;

                // Bóng đổ màu xanh dưới nút
                g2.setColor(new Color(37, 117, 252, 90));
                g2.fillRoundRect(4, 5, w - 8, h - 5, arc, arc);

                // Nền Gradient xanh dương rực rỡ
                GradientPaint btnGrad = new GradientPaint(0, 0, new Color(37, 117, 252), w, 0, new Color(16, 84, 226));
                g2.setPaint(btnGrad);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                // Chữ ở giữa
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                int tx = (w - fm.stringWidth(text)) / 2 - 10;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);

                // Huy hiệu tròn trắng mờ chứa mũi tên -> ở góc phải
                int badgeSize = 34;
                int bx = w - badgeSize - 10;
                int by = (h - badgeSize) / 2;
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillOval(bx, by, badgeSize, badgeSize);

                // Mũi tên ->
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int ax = bx + 11;
                int ay = by + badgeSize / 2;
                g2.drawLine(ax, ay, ax + 12, ay);
                g2.drawLine(ax + 7, ay - 5, ax + 12, ay);
                g2.drawLine(ax + 7, ay + 5, ax + 12, ay);

                g2.dispose();
            }
        };
        btnMainAction.setPreferredSize(new Dimension(340, 50));
        btnMainAction.setMaximumSize(new Dimension(340, 50));
        btnMainAction.setOpaque(false);
        btnMainAction.setContentAreaFilled(false);
        btnMainAction.setBorderPainted(false);
        btnMainAction.setFocusPainted(false);
        btnMainAction.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMainAction.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(btnMainAction);

        cardPanel.add(Box.createVerticalStrut(18));

        // 7. Dòng phân cách "──  OR CONTINUE WITH  ──"
        JPanel dividerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);

                int w = getWidth();
                int h = getHeight();
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                String text = "OR CONTINUE WITH";
                int textW = fm.stringWidth(text);
                int cy = h / 2;

                g2.setColor(new Color(203, 213, 225));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(20, cy, (w - textW) / 2 - 12, cy);
                g2.drawLine((w + textW) / 2 + 12, cy, w - 20, cy);

                g2.setColor(new Color(148, 163, 184));
                g2.drawString(text, (w - textW) / 2, cy + fm.getAscent() / 2 - 1);
                g2.dispose();
            }
        };
        dividerPanel.setOpaque(false);
        dividerPanel.setPreferredSize(new Dimension(340, 20));
        dividerPanel.setMaximumSize(new Dimension(340, 20));
        dividerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(dividerPanel);

        cardPanel.add(Box.createVerticalStrut(14));

        // 8. Hàng 4 nút Neumorphic squircle: Google, Discord, Facebook, Server Config
        JPanel socialRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        socialRow.setOpaque(false);
        socialRow.setMaximumSize(new Dimension(340, 56));
        socialRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Nút 1: Google
        socialRow.add(createNeumorphicSocialButton("Google", (g2, w, h) -> {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            FontMetrics fm = g2.getFontMetrics();
            String gText = "G";
            int gx = (w - fm.stringWidth(gText)) / 2;
            int gy = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(new Color(234, 67, 53)); // Đỏ Google
            g2.drawString(gText, gx, gy);
        }, () -> JOptionPane.showMessageDialog(this, "Đăng nhập nhanh Google SSO được mô phỏng cho đồ án.", "Google Login", JOptionPane.INFORMATION_MESSAGE)));

        // Nút 2: Discord
        socialRow.add(createNeumorphicSocialButton("Discord", (g2, w, h) -> {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            String dText = "🎮";
            int dx = (w - fm.stringWidth(dText)) / 2;
            int dy = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(dText, dx, dy);
        }, () -> JOptionPane.showMessageDialog(this, "Đăng nhập Discord được mô phỏng cho đồ án.", "Discord Login", JOptionPane.INFORMATION_MESSAGE)));

        // Nút 3: Facebook
        socialRow.add(createNeumorphicSocialButton("Facebook", (g2, w, h) -> {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 19));
            FontMetrics fm = g2.getFontMetrics();
            String fText = "f";
            int fx = (w - fm.stringWidth(fText)) / 2;
            int fy = (h - fm.getHeight()) / 2 + fm.getAscent() - 1;
            g2.setColor(new Color(24, 119, 242));
            g2.drawString(fText, fx, fy);
        }, () -> JOptionPane.showMessageDialog(this, "Đăng nhập Facebook được mô phỏng cho đồ án.", "Facebook Login", JOptionPane.INFORMATION_MESSAGE)));

        // Nút 4: Cấu hình Server (Host/Port cho kết nối LAN & Mạng khác nhau)
        socialRow.add(createNeumorphicSocialButton("Cấu Hình Host/Port Server", (g2, w, h) -> {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            String sText = "⚙";
            int sx = (w - fm.stringWidth(sText)) / 2;
            int sy = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(new Color(71, 85, 105));
            g2.drawString(sText, sx, sy);
        }, this::showServerConfigDialog));

        cardPanel.add(socialRow);

        cardPanel.add(Box.createVerticalStrut(14));

        // 9. Dòng chuyển chế độ Đăng nhập <-> Đăng ký
        lblToggleMode = new JLabel("<html>Don't have an account? <font color='#2575FC'><b>Sign up</b></font></html>");
        lblToggleMode.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblToggleMode.setForeground(new Color(100, 116, 139));
        lblToggleMode.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblToggleMode.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblToggleMode.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleAuthMode();
            }
        });
        cardPanel.add(lblToggleMode);

        cardPanel.add(Box.createVerticalStrut(8));

        // 10. Thanh trạng thái kết nối
        lblStatus = new JLabel("● Sẵn sàng kết nối máy chủ (" + serverHost + ":" + serverPort + ")");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStatus.setForeground(new Color(148, 163, 184));
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(lblStatus);

        backgroundPanel.add(cardPanel);
        setContentPane(backgroundPanel);

        // Sự kiện gửi form
        btnMainAction.addActionListener(e -> performSubmit());
        txtUser.addActionListener(e -> txtPass.requestFocus());
        txtPass.addActionListener(e -> performSubmit());
    }

    /**
     * Tạo khung nhập Neumorphic Pill bo cong mềm mại
     */
    private JPanel createNeumorphicFieldPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = h;

                // Đổ bóng Neumorphic nhẹ
                g2.setColor(new Color(226, 232, 240, 140));
                g2.fillRoundRect(2, 2, w - 4, h - 2, arc, arc);

                // Nền trắng tinh tế
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

                // Viền xám mờ mềm
                g2.setColor(new Color(226, 232, 240));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(340, 48));
        p.setMaximumSize(new Dimension(340, 48));
        p.setBorder(new EmptyBorder(4, 12, 4, 10));
        return p;
    }

    /**
     * Tạo nút mạng xã hội Neumorphic Squircle
     */
    private interface IconPainter {
        void paint(Graphics2D g2, int w, int h);
    }

    private JButton createNeumorphicSocialButton(String toolTip, IconPainter painter, Runnable onClick) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                int w = getWidth();
                int h = getHeight();
                int arc = 16;

                // Bóng Neumorphic dưới
                g2.setColor(new Color(214, 223, 238, 150));
                g2.fillRoundRect(2, 3, w - 4, h - 4, arc, arc);

                // Thân nút
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 2, h - 2, arc, arc);

                // Viền nhẹ
                g2.setColor(new Color(230, 238, 248));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 2, h - 2, arc, arc);

                painter.paint(g2, w, h);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(50, 48));
        btn.setMaximumSize(new Dimension(50, 48));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setToolTipText(toolTip);
        btn.addActionListener(e -> onClick.run());
        return btn;
    }

    /**
     * Chuyển đổi giữa chế độ Đăng nhập và Đăng ký
     */
    private void toggleAuthMode() {
        isRegisterMode = !isRegisterMode;
        if (isRegisterMode) {
            lblTitle.setText("Create Account");
            lblSub.setText("Sign up to get started");
            btnMainAction.setText("Sign Up");
            cbRole.setVisible(true);
            lblToggleMode.setText("<html>Already have an account? <font color='#2575FC'><b>Sign in</b></font></html>");
        } else {
            lblTitle.setText("Welcome Back");
            lblSub.setText("Sign in to continue");
            btnMainAction.setText("Login");
            cbRole.setVisible(false);
            lblToggleMode.setText("<html>Don't have an account? <font color='#2575FC'><b>Sign up</b></font></html>");
        }
        revalidate();
        repaint();
    }

    /**
     * Hộp thoại Cấu hình Server Host/Port để chạy trên nhiều máy khác nhau
     */
    private void showServerConfigDialog() {
        JDialog dlg = new JDialog(this, "⚡ Cấu Hình Địa Chỉ Server", true);
        dlg.setSize(380, 260);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(3, 2, 10, 12));
        form.setBorder(new EmptyBorder(20, 24, 10, 24));
        form.setBackground(Color.WHITE);

        form.add(new JLabel("Server IP / Host:"));
        JTextField txtH = new JTextField(serverHost);
        form.add(txtH);

        form.add(new JLabel("Server Port:"));
        JTextField txtP = new JTextField(String.valueOf(serverPort));
        form.add(txtP);

        // Hiển thị IP LAN hiện tại của máy để gợi ý
        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        form.add(new JLabel("Gợi ý IP mạng LAN:"));
        JLabel lblLocal = new JLabel(localIp);
        lblLocal.setForeground(new Color(37, 117, 252));
        lblLocal.setFont(new Font("Segoe UI", Font.BOLD, 12));
        form.add(lblLocal);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnBar.setBackground(Color.WHITE);

        JButton btnSave = new JButton("Lưu Cấu Hình");
        btnSave.setBackground(new Color(37, 117, 252));
        btnSave.setForeground(Color.WHITE);
        btnSave.addActionListener(e -> {
            try {
                this.serverHost = txtH.getText().trim();
                this.serverPort = Integer.parseInt(txtP.getText().trim());
                lblStatus.setText("● Cấu hình máy chủ: " + serverHost + ":" + serverPort);
                dlg.dispose();
                JOptionPane.showMessageDialog(this, "Đã lưu cấu hình kết nối tới: " + serverHost + ":" + serverPort, "Thành Công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Port không hợp lệ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnBar.add(btnSave);
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showForgotPasswordDialog() {
        JOptionPane.showMessageDialog(this,
                "Để bảo mật, vui lòng liên hệ Quản trị viên (Admin) hoặc dùng lệnh đổi mật khẩu trong phòng chat sau khi đăng nhập.",
                "Quên Mật Khẩu",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void performSubmit() {
        String username = txtUser.getText().trim();
        String password = new String(txtPass.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        btnMainAction.setEnabled(false);
        lblStatus.setText("⏳ Đang kết nối tới " + serverHost + ":" + serverPort + "...");
        lblStatus.setForeground(new Color(37, 117, 252));

        new Thread(() -> {
            boolean connected = socketClient.connect(serverHost, serverPort, this);
            if (!connected) {
                SwingUtilities.invokeLater(() -> {
                    btnMainAction.setEnabled(true);
                    showError("Không thể kết nối máy chủ (" + serverHost + ":" + serverPort + "). Hãy kiểm tra xem Server đã bật chưa!");
                });
                return;
            }

            if (isRegisterMode) {
                String role = cbRole.getSelectedIndex() == 1 ? Protocol.ROLE_ADMIN : Protocol.ROLE_USER;
                socketClient.register(username, password, role);
            } else {
                socketClient.login(username, password);
            }
        }).start();
    }

    private void showError(String msg) {
        lblStatus.setText("❌ " + msg);
        lblStatus.setForeground(new Color(239, 68, 68));
    }

    // =========================================================================
    // CALLBACKS GIAO THỨC TỪ SERVERLISTENER
    // =========================================================================

    @Override
    public void onLoginResult(boolean success, String role, String username, String message) {
        SwingUtilities.invokeLater(() -> {
            btnMainAction.setEnabled(true);
            if (success) {
                lblStatus.setText("✅ Đăng nhập thành công! Đang tải...");
                lblStatus.setForeground(new Color(22, 163, 74));
                dispose();
                // Mở cửa sổ phòng chat chính
                ChatRoomFrame chatRoom = new ChatRoomFrame(socketClient, username, role);
                chatRoom.setVisible(true);
            } else {
                showError(message);
            }
        });
    }

    @Override
    public void onRegisterResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            btnMainAction.setEnabled(true);
            if (success) {
                JOptionPane.showMessageDialog(this, "Đăng ký thành công! Hãy tiến hành đăng nhập.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                toggleAuthMode();
            } else {
                showError(message);
            }
        });
    }

    @Override
    public void onConnectionLost(String reason) {
        SwingUtilities.invokeLater(() -> {
            btnMainAction.setEnabled(true);
            showError(reason);
        });
    }

    @Override public void onCreateGroupResult(boolean success, String groupName, String groupCode, String message) {}
    @Override public void onJoinGroupResult(boolean success, String groupName, String message) {}
    @Override public void onGroupListUpdated(List<ChatGroup> groups) {}
    @Override public void onGroupMessageReceived(String groupName, String sender, String role, String timestamp, String content) {}
    @Override public void onPublicMessageReceived(String s, String r, String t, String c) {}
    @Override public void onPrivateMessageReceived(String s, String tg, String t, String c) {}
    @Override public void onSystemMessage(String content) {}
    @Override public void onUserListUpdated(List<User> onlineUsers) {}
    @Override public void onHistoryReceived(String groupName, List<MessagePacket> history) {}
    @Override public void onKicked(String reason) {}
    @Override public void onMuted(int minutes, String reason) {}
    @Override public void onUnmuted() {}
    @Override public void onChangePasswordResult(boolean success, String message) {}
}
