import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * Ứng dụng Giao diện Đồ họa (GUI) Chat Client - Phong cách Messenger
 * Môn học: Lập trình mạng - Socket TCP
 *
 * Tính năng chính:
 * 1. Giao diện bong bóng chat Messenger (Tin của mình căn phải màu tím/xanh, tin người khác căn trái màu xám có avatar tròn)
 * 2. Màn hình Đăng nhập & Đăng ký tài khoản (Tabbed Sign In & Sign Up)
 * 3. Phân quyền người dùng (USER và ADMIN có vương miện 👑, các nút Kick, Mute, Xóa lịch sử, Thông báo)
 * 4. Cửa sổ Lịch sử chat (Chat History Viewer) có tìm kiếm, lọc theo người gửi và xuất file
 * 5. Danh sách người dùng Online trực quan bên cạnh với thao tác Nhấp đúp chuột để nhắn riêng 1-1
 */
public class ChatClientGUI extends JFrame {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Network connection
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private javax.swing.Timer syncTimer;

    // User info
    private String myUsername = "";
    private String myRole = "USER";

    // UI Header
    private JLabel lblRoomTitle;
    private JLabel lblRoomSubtitle;
    private JButton btnToggleSidebar;
    private JButton btnViewHistory;
    private JButton btnAdminMenu;
    private JButton btnLogout;

    // UI Chat Area (Messenger Bubbles)
    private JPanel messagesContainer;
    private JScrollPane chatScrollPane;

    // UI Bottom Bar
    private JComboBox<String> comboTarget;
    private JTextField txtInput;
    private JButton btnSend;
    private JButton btnQuickPublic;

    // UI Sidebar (Online List & Admin Actions)
    private JPanel pnlSidebar;
    private JLabel lblOnlineTitle;
    private DefaultListModel<OnlineUser> onlineListModel;
    private JList<OnlineUser> onlineList;
    private JButton btnPrivateChat;
    private JButton btnRefreshOnline;
    private JPanel pnlAdminControls;
    private JButton btnKick;
    private JButton btnMute;
    private JButton btnUnmute;
    private JButton btnClearHistory;
    private JButton btnAnnounce;

    // Color Palette cho Avatar tròn giống Messenger
    private static final Color[] AVATAR_PALETTE = {
            new Color(236, 72, 153),  // Pink
            new Color(139, 92, 246),  // Purple
            new Color(59, 130, 246),  // Blue
            new Color(16, 185, 129),  // Emerald
            new Color(245, 158, 11),  // Amber
            new Color(239, 68, 68),   // Red
            new Color(20, 184, 166),  // Teal
            new Color(99, 102, 241)   // Indigo
    };

    public static class OnlineUser {
        public final String username;
        public final String role;

        public OnlineUser(String username, String role) {
            this.username = username;
            this.role = role != null ? role.toUpperCase() : "USER";
        }

        @Override
        public String toString() {
            return username + " (" + role + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof OnlineUser)) return false;
            OnlineUser other = (OnlineUser) obj;
            return this.username.equalsIgnoreCase(other.username);
        }

        @Override
        public int hashCode() {
            return username.toLowerCase().hashCode();
        }
    }

    public ChatClientGUI() {
        initUI();
    }

    private void initUI() {
        setTitle("Tour Crew - Messenger Chat Room (Socket TCP)");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1020, 700);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmAndExit();
            }
        });

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.WHITE);

        // 1. Messenger Header
        root.add(createMessengerHeader(), BorderLayout.NORTH);

        // 2. Center Chat Stream (Messenger Bubbles)
        JPanel centerChatPanel = new JPanel(new BorderLayout(0, 0));
        centerChatPanel.setBackground(Color.WHITE);

        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(Color.WHITE);
        messagesContainer.setBorder(new EmptyBorder(12, 16, 12, 16));

        chatScrollPane = new JScrollPane(messagesContainer);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chatScrollPane.setBackground(Color.WHITE);
        chatScrollPane.getViewport().setBackground(Color.WHITE);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        centerChatPanel.add(chatScrollPane, BorderLayout.CENTER);
        centerChatPanel.add(createMessengerInputBar(), BorderLayout.SOUTH);

        root.add(centerChatPanel, BorderLayout.CENTER);

        // 3. Right Sidebar (Online List & Quick Actions)
        pnlSidebar = createSidebarPanel();
        root.add(pnlSidebar, BorderLayout.EAST);

        setContentPane(root);
    }

    // ================= 1. MESSENGER HEADER =================
    private JPanel createMessengerHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(229, 231, 235)),
                new EmptyBorder(10, 18, 10, 18)
        ));

        // Bên trái Header: Avatar nhóm và Tiêu đề
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        // Avatar nhóm tròn gradient
        JPanel groupAvatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(147, 51, 234), 44, 44, new Color(59, 130, 246));
                g2.setPaint(gp);
                g2.fillOval(0, 0, 44, 44);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
                FontMetrics fm = g2.getFontMetrics();
                String text = "💬";
                int x = (44 - fm.stringWidth(text)) / 2;
                int y = ((44 - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(text, x, y);
                g2.dispose();
            }
        };
        groupAvatar.setPreferredSize(new Dimension(44, 44));
        groupAvatar.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        lblRoomTitle = new JLabel("Tour Crew");
        lblRoomTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblRoomTitle.setForeground(new Color(15, 23, 42));

        lblRoomSubtitle = new JLabel("Đang kết nối...");
        lblRoomSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblRoomSubtitle.setForeground(new Color(100, 116, 139));

        titleBox.add(lblRoomTitle);
        titleBox.add(lblRoomSubtitle);

        left.add(groupAvatar);
        left.add(titleBox);

        // Bên phải Header: Nút Lịch sử, Sidebar, Đăng xuất
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        btnViewHistory = createPillButton("📜 Lịch sử chat", new Color(241, 245, 249), new Color(30, 41, 59));
        btnViewHistory.addActionListener(e -> openHistoryViewerDialog());

        btnToggleSidebar = createPillButton("👥 Danh sách", new Color(241, 245, 249), new Color(30, 41, 59));
        btnToggleSidebar.addActionListener(e -> {
            pnlSidebar.setVisible(!pnlSidebar.isVisible());
            pnlSidebar.revalidate();
            pnlSidebar.repaint();
        });

        btnAdminMenu = createPillButton("👑 Quản trị", new Color(254, 226, 226), new Color(185, 28, 28));
        btnAdminMenu.setVisible(false); // Hiện nếu là Admin
        btnAdminMenu.addActionListener(e -> showAdminActionPopup(btnAdminMenu));

        btnLogout = createPillButton("🚪 Thoát", new Color(254, 242, 242), new Color(220, 38, 38));
        btnLogout.addActionListener(e -> logout());

        right.add(btnViewHistory);
        right.add(btnToggleSidebar);
        right.add(btnAdminMenu);
        right.add(btnLogout);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ================= 2. MESSENGER INPUT BAR =================
    private JPanel createMessengerInputBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, new Color(229, 231, 235)),
                new EmptyBorder(10, 16, 12, 16)
        ));

        // Phía trên ô input: Hàng chọn chế độ gửi và biểu tượng cảm xúc nhanh
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 6, 0));

        JPanel targetBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        targetBox.setOpaque(false);

        JLabel lblTargetIcon = new JLabel("Gửi tới:");
        lblTargetIcon.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTargetIcon.setForeground(new Color(100, 116, 139));

        comboTarget = new JComboBox<>(new String[]{"🌐 Tất cả (Phòng chat)"});
        comboTarget.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboTarget.setBackground(new Color(248, 250, 252));
        comboTarget.setPreferredSize(new Dimension(170, 28));

        btnQuickPublic = new JButton("🌐 Chat chung");
        btnQuickPublic.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnQuickPublic.setMargin(new Insets(2, 6, 2, 6));
        btnQuickPublic.setFocusPainted(false);
        btnQuickPublic.addActionListener(e -> comboTarget.setSelectedIndex(0));

        targetBox.add(lblTargetIcon);
        targetBox.add(comboTarget);
        targetBox.add(btnQuickPublic);

        // Hàng Emoji phản ứng nhanh giống Messenger trong ảnh: 💯, 🍗, 🍔, 👍, 😂
        JPanel emojiBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        emojiBox.setOpaque(false);
        String[] quickEmojis = {"💯", "🍗", "🍔", "👍", "😂", "❤️", "🔥"};
        for (String emo : quickEmojis) {
            JButton btnEmo = new JButton(emo);
            btnEmo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            btnEmo.setMargin(new Insets(1, 4, 1, 4));
            btnEmo.setBorderPainted(false);
            btnEmo.setContentAreaFilled(false);
            btnEmo.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnEmo.addActionListener(e -> {
                txtInput.setText(txtInput.getText() + emo);
                txtInput.requestFocusInWindow();
            });
            emojiBox.add(btnEmo);
        }

        topRow.add(targetBox, BorderLayout.WEST);
        topRow.add(emojiBox, BorderLayout.EAST);

        // Hàng nhập văn bản bo tròn hình viên thuốc (Pill Capsule)
        JPanel inputPill = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(241, 245, 249)); // Light gray pill
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 36, 36);
                g2.setColor(new Color(226, 232, 240));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 36, 36);
                g2.dispose();
            }
        };
        inputPill.setOpaque(false);
        inputPill.setBorder(new EmptyBorder(4, 16, 4, 8));

        txtInput = new JTextField();
        txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtInput.setBorder(BorderFactory.createEmptyBorder());
        txtInput.setOpaque(false);
        txtInput.addActionListener(e -> sendMessage());

        btnSend = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(147, 51, 234), getWidth(), getHeight(), new Color(99, 102, 241));
                g2.setPaint(gp);
                g2.fillOval(0, 0, getWidth(), getHeight());

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                FontMetrics fm = g2.getFontMetrics();
                String icon = "✈";
                int x = (getWidth() - fm.stringWidth(icon)) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent() - 1;
                g2.drawString(icon, x, y);
                g2.dispose();
            }
        };
        btnSend.setPreferredSize(new Dimension(34, 34));
        btnSend.setBorderPainted(false);
        btnSend.setContentAreaFilled(false);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> sendMessage());

        inputPill.add(txtInput, BorderLayout.CENTER);
        inputPill.add(btnSend, BorderLayout.EAST);

        JPanel fullBottom = new JPanel(new BorderLayout(0, 4));
        fullBottom.setOpaque(false);
        fullBottom.add(topRow, BorderLayout.NORTH);
        fullBottom.add(inputPill, BorderLayout.SOUTH);

        bar.add(fullBottom, BorderLayout.CENTER);
        return bar;
    }

    // ================= 3. SIDEBAR ONLINE & ADMIN CONTROLS =================
    private JPanel createSidebarPanel() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 8));
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setBackground(new Color(248, 250, 252));
        sidebar.setBorder(new MatteBorder(0, 1, 0, 0, new Color(229, 231, 235)));

        // Header Sidebar
        JPanel sideHeader = new JPanel(new BorderLayout());
        sideHeader.setOpaque(false);
        sideHeader.setBorder(new EmptyBorder(12, 14, 8, 14));

        lblOnlineTitle = new JLabel("👥 Đang Online (0)");
        lblOnlineTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblOnlineTitle.setForeground(new Color(30, 41, 59));

        btnRefreshOnline = new JButton("🔄");
        btnRefreshOnline.setToolTipText("Làm mới danh sách");
        btnRefreshOnline.setMargin(new Insets(2, 6, 2, 6));
        btnRefreshOnline.addActionListener(e -> requestOnlineList());

        sideHeader.add(lblOnlineTitle, BorderLayout.WEST);
        sideHeader.add(btnRefreshOnline, BorderLayout.EAST);

        sidebar.add(sideHeader, BorderLayout.NORTH);

        // Danh sách Online JList
        onlineListModel = new DefaultListModel<>();
        onlineList = new JList<>(onlineListModel);
        onlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineList.setCellRenderer(new OnlineUserCellRenderer());
        onlineList.setBackground(new Color(248, 250, 252));
        onlineList.setFixedCellHeight(46);

        // Double click vào user để nhắn riêng ngay lập tức
        onlineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    switchToPrivateChatWithSelected();
                }
            }
        });
        onlineList.addListSelectionListener(e -> updateButtonStates());

        JScrollPane scrollList = new JScrollPane(onlineList);
        scrollList.setBorder(BorderFactory.createEmptyBorder());
        scrollList.getViewport().setBackground(new Color(248, 250, 252));
        sidebar.add(scrollList, BorderLayout.CENTER);

        // Tác vụ bên dưới Sidebar
        JPanel bottomActions = new JPanel();
        bottomActions.setLayout(new BoxLayout(bottomActions, BoxLayout.Y_AXIS));
        bottomActions.setOpaque(false);
        bottomActions.setBorder(new EmptyBorder(10, 12, 14, 12));

        btnPrivateChat = new JButton("💬 Nhắn riêng (1-1)");
        btnPrivateChat.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnPrivateChat.setBackground(new Color(139, 92, 246));
        btnPrivateChat.setForeground(Color.WHITE);
        btnPrivateChat.setFocusPainted(false);
        btnPrivateChat.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
        btnPrivateChat.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPrivateChat.addActionListener(e -> switchToPrivateChatWithSelected());

        bottomActions.add(btnPrivateChat);
        bottomActions.add(Box.createVerticalStrut(10));

        // Khung dành riêng cho ADMIN
        pnlAdminControls = new JPanel(new GridLayout(4, 1, 6, 6));
        pnlAdminControls.setOpaque(false);
        TitledBorder adminBorder = BorderFactory.createTitledBorder(
                new LineBorder(new Color(239, 68, 68), 1, true),
                "👑 QUẢN TRỊ VIÊN (ADMIN)",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(185, 28, 28)
        );
        pnlAdminControls.setBorder(adminBorder);

        btnKick = new JButton("🚫 Kick (Đuổi khỏi phòng)");
        btnKick.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnKick.setBackground(new Color(239, 68, 68));
        btnKick.setForeground(Color.WHITE);
        btnKick.setFocusPainted(false);
        btnKick.addActionListener(e -> handleAdminKick());

        btnMute = new JButton("🔇 Cấm chat (Mute)");
        btnMute.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnMute.setBackground(new Color(245, 158, 11));
        btnMute.setForeground(Color.WHITE);
        btnMute.setFocusPainted(false);
        btnMute.addActionListener(e -> handleAdminMute(true));

        btnUnmute = new JButton("🔊 Mở chat (Unmute)");
        btnUnmute.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnUnmute.setBackground(new Color(16, 185, 129));
        btnUnmute.setForeground(Color.WHITE);
        btnUnmute.setFocusPainted(false);
        btnUnmute.addActionListener(e -> handleAdminMute(false));

        btnClearHistory = new JButton("🗑 Xóa lịch sử phòng");
        btnClearHistory.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnClearHistory.setBackground(new Color(100, 116, 139));
        btnClearHistory.setForeground(Color.WHITE);
        btnClearHistory.setFocusPainted(false);
        btnClearHistory.addActionListener(e -> handleAdminClearHistory());

        pnlAdminControls.add(btnKick);
        pnlAdminControls.add(btnMute);
        pnlAdminControls.add(btnUnmute);
        pnlAdminControls.add(btnClearHistory);

        bottomActions.add(pnlAdminControls);
        sidebar.add(bottomActions, BorderLayout.SOUTH);

        return sidebar;
    }

    // ================= RENDERER DANH SÁCH ONLINE =================
    private class OnlineUserCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
            panel.setOpaque(true);

            if (value instanceof OnlineUser) {
                OnlineUser u = (OnlineUser) value;
                boolean isMe = u.username.equalsIgnoreCase(myUsername);
                boolean isAdmin = "ADMIN".equalsIgnoreCase(u.role);

                // Avatar tròn nhỏ
                AvatarCircle avatar = new AvatarCircle(u.username, isAdmin, 32);
                panel.add(avatar);

                JPanel textBox = new JPanel();
                textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));
                textBox.setOpaque(false);

                String displayName = u.username + (isMe ? " (Bạn)" : "");
                JLabel lblName = new JLabel(displayName);
                lblName.setFont(new Font("Segoe UI", (isMe || isAdmin) ? Font.BOLD : Font.PLAIN, 13));
                lblName.setForeground(isAdmin ? new Color(185, 28, 28) : new Color(15, 23, 42));

                String roleText = isAdmin ? "👑 Quản trị viên" : "Thành viên";
                JLabel lblRole = new JLabel(roleText);
                lblRole.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                lblRole.setForeground(new Color(100, 116, 139));

                textBox.add(lblName);
                textBox.add(lblRole);
                panel.add(textBox);

                if (isSelected) {
                    panel.setBackground(new Color(237, 233, 254)); // Soft purple
                } else {
                    panel.setBackground(index % 2 == 0 ? new Color(248, 250, 252) : Color.WHITE);
                }
            }
            return panel;
        }
    }

    // ================= 4. HIỂN THỊ BONG BÓNG CHAT MESSENGER (BUBBLES) =================
    public void addBubbleMessage(String sender, String role, String content, String time, boolean isMe, boolean isPrivate, boolean isSystem) {
        SwingUtilities.invokeLater(() -> {
            JPanel rowPanel = new JPanel();
            rowPanel.setOpaque(false);

            if (isSystem) {
                // Tin nhắn hệ thống căn giữa dạng viên nang (Pill)
                rowPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 4));
                JLabel sysLabel = new JLabel("📢 " + content);
                sysLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                sysLabel.setForeground(new Color(100, 116, 139));
                sysLabel.setBorder(BorderFactory.createCompoundBorder(
                        new RoundedBorder(new Color(226, 232, 240), 16),
                        new EmptyBorder(4, 12, 4, 12)
                ));
                rowPanel.add(sysLabel);
            } else if (isMe) {
                // Tin nhắn của tôi: Căn bên phải, bong bóng màu Tím/Xanh Messenger
                rowPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 4));

                JPanel bubbleCol = new JPanel();
                bubbleCol.setLayout(new BoxLayout(bubbleCol, BoxLayout.Y_AXIS));
                bubbleCol.setOpaque(false);

                if (isPrivate) {
                    JLabel lblLock = new JLabel("🔒 Nhắn riêng");
                    lblLock.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    lblLock.setForeground(new Color(139, 92, 246));
                    lblLock.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    bubbleCol.add(lblLock);
                }

                // Bong bóng chat màu tím/xanh Messenger
                BubbleBox bubble = new BubbleBox(content, true, isPrivate);
                bubble.setAlignmentX(Component.RIGHT_ALIGNMENT);
                bubbleCol.add(bubble);

                if (time != null && !time.isEmpty()) {
                    JLabel lblTime = new JLabel(time);
                    lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    lblTime.setForeground(new Color(148, 163, 184));
                    lblTime.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    lblTime.setBorder(new EmptyBorder(2, 0, 0, 4));
                    bubbleCol.add(lblTime);
                }

                rowPanel.add(bubbleCol);
            } else {
                // Tin nhắn người khác: Căn bên trái, Avatar tròn, Tên bên trên, Bong bóng xám
                rowPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));

                // Avatar tròn
                boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
                AvatarCircle avatar = new AvatarCircle(sender, isAdmin, 36);
                rowPanel.add(avatar);

                JPanel bubbleCol = new JPanel();
                bubbleCol.setLayout(new BoxLayout(bubbleCol, BoxLayout.Y_AXIS));
                bubbleCol.setOpaque(false);

                // Tên người gửi hiển thị phía trên bong bóng
                String senderHeader = sender;
                if (isAdmin) senderHeader = "👑 " + sender + " [ADMIN]";
                if (isPrivate) senderHeader += " 🔒 (Mật)";

                JLabel lblSender = new JLabel(senderHeader);
                lblSender.setFont(new Font("Segoe UI", Font.BOLD, 11));
                lblSender.setForeground(isAdmin ? new Color(220, 38, 38) : new Color(100, 116, 139));
                lblSender.setBorder(new EmptyBorder(0, 4, 2, 0));
                bubbleCol.add(lblSender);

                // Bong bóng màu xám Messenger
                BubbleBox bubble = new BubbleBox(content, false, isPrivate);
                bubbleCol.add(bubble);

                if (time != null && !time.isEmpty()) {
                    JLabel lblTime = new JLabel(time);
                    lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    lblTime.setForeground(new Color(148, 163, 184));
                    lblTime.setBorder(new EmptyBorder(2, 4, 0, 0));
                    bubbleCol.add(lblTime);
                }

                rowPanel.add(bubbleCol);
            }

            rowPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, rowPanel.getPreferredSize().height));
            messagesContainer.add(rowPanel);
            messagesContainer.revalidate();
            messagesContainer.repaint();

            // Tự động cuộn xuống dưới cùng
            SwingUtilities.invokeLater(() -> {
                JScrollBar vBar = chatScrollPane.getVerticalScrollBar();
                vBar.setValue(vBar.getMaximum());
            });
        });
    }

    // ================= LỚP VẼ BONG BÓNG CHAT (BUBBLE BOX) =================
    private static class BubbleBox extends JPanel {
        private final boolean isMe;
        private final boolean isPrivate;

        public BubbleBox(String text, boolean isMe, boolean isPrivate) {
            this.isMe = isMe;
            this.isPrivate = isPrivate;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(10, 14, 10, 14));

            Color textColor = isMe ? Color.WHITE : new Color(15, 23, 42);
            JLabel contentLabel = createWrappedLabel(text, textColor, 360);
            add(contentLabel, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isMe) {
                // Gradient tím-xanh chuẩn Messenger
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(147, 51, 234),
                        getWidth(), getHeight(), new Color(99, 102, 241)
                );
                g2.setPaint(gp);
            } else if (isPrivate) {
                // Nhắn riêng người khác gửi: màu tím nhạt
                g2.setColor(new Color(237, 233, 254));
            } else {
                // Tin người khác gửi: Màu xám nhạt chuẩn Messenger (#F0F2F5)
                g2.setColor(new Color(240, 242, 245));
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ================= LỚP VẼ AVATAR TRÒN CÓ KÝ TỰ =================
    private static class AvatarCircle extends JPanel {
        private final String letter;
        private final Color bgColor;
        private final boolean isAdmin;
        private final int size;

        public AvatarCircle(String username, boolean isAdmin, int size) {
            this.size = size;
            this.isAdmin = isAdmin;
            this.letter = (username != null && !username.isEmpty()) ? username.substring(0, 1).toUpperCase() : "?";
            this.bgColor = getAvatarColor(username);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(bgColor);
            g2.fillOval(0, 0, size, size);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.44)));
            FontMetrics fm = g2.getFontMetrics();
            int x = (size - fm.stringWidth(letter)) / 2;
            int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(letter, x, y);

            if (isAdmin) {
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, (int) (size * 0.35)));
                g2.drawString("👑", size - 14, 12);
            }
            g2.dispose();
        }
    }

    private static Color getAvatarColor(String username) {
        if (username == null || username.isEmpty()) return AVATAR_PALETTE[0];
        int hash = Math.abs(username.hashCode());
        return AVATAR_PALETTE[hash % AVATAR_PALETTE.length];
    }

    private static JLabel createWrappedLabel(String text, Color textColor, int maxWidth) {
        JLabel label = new JLabel();
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setForeground(textColor);

        FontMetrics fm = label.getFontMetrics(label.getFont());
        int textWidth = fm.stringWidth(text);

        if (textWidth > maxWidth || text.contains("\n")) {
            String safe = escapeHtml(text).replace("\n", "<br/>");
            label.setText("<html><div style='width: " + maxWidth + "px;'>" + safe + "</div></html>");
        } else {
            label.setText(text);
        }
        return label;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ================= XỬ LÝ GỬI TIN NHẮN =================
    private void sendMessage() {
        String content = txtInput.getText().trim();
        if (content.isEmpty()) return;

        String selectedTarget = (String) comboTarget.getSelectedItem();
        if (selectedTarget == null || selectedTarget.startsWith("🌐")) {
            sendPacket("MSG_ALL|" + content);
        } else {
            String targetUser = selectedTarget.replace("@", "").trim();
            sendPacket("MSG_PRIVATE|" + targetUser + "|" + content);
        }

        txtInput.setText("");
        txtInput.requestFocusInWindow();
    }

    private void switchToPrivateChatWithSelected() {
        OnlineUser selected = onlineList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 người dùng trong danh sách trước!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (selected.username.equalsIgnoreCase(myUsername)) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự nhắn riêng cho chính mình!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String targetTag = "@" + selected.username;
        boolean exists = false;
        for (int i = 0; i < comboTarget.getItemCount(); i++) {
            if (comboTarget.getItemAt(i).equalsIgnoreCase(targetTag)) {
                comboTarget.setSelectedIndex(i);
                exists = true;
                break;
            }
        }
        if (!exists) {
            comboTarget.addItem(targetTag);
            comboTarget.setSelectedItem(targetTag);
        }

        txtInput.requestFocusInWindow();
        addBubbleMessage("Hệ thống", "SYSTEM", "Đã chuyển sang chế độ chat mật 1-1 với [" + selected.username + "]", "", false, true, true);
    }

    // ================= TÁC VỤ QUẢN TRỊ ADMIN =================
    private void handleAdminKick() {
        OnlineUser target = onlineList.getSelectedValue();
        if (target == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người dùng cần Kick!", "Chưa chọn người dùng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target.username.equalsIgnoreCase(myUsername)) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự kick chính mình!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn KICK tài khoản [" + target.username + "] khỏi phòng?", "Xác nhận Kick", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            sendPacket("ADMIN_KICK|" + target.username);
        }
    }

    private void handleAdminMute(boolean isMute) {
        OnlineUser target = onlineList.getSelectedValue();
        if (target == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người dùng cần thao tác!", "Chưa chọn người dùng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target.username.equalsIgnoreCase(myUsername)) {
            JOptionPane.showMessageDialog(this, "Bạn không thể tự cấm chat chính mình!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        sendPacket("ADMIN_MUTE|" + target.username + "|" + isMute);
    }

    private void handleAdminClearHistory() {
        int choice = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn XÓA TOÀN BỘ lịch sử phòng chat trên server?", "Xác nhận xóa lịch sử", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            sendPacket("ADMIN_CLEAR_HISTORY");
        }
    }

    private void showAdminActionPopup(Component parent) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem itemAnnounce = new JMenuItem("📢 Phát thông báo toàn hệ thống");
        itemAnnounce.addActionListener(e -> {
            String msg = JOptionPane.showInputDialog(this, "Nhập nội dung thông báo khẩn từ Admin:", "Thông báo Admin", JOptionPane.INFORMATION_MESSAGE);
            if (msg != null && !msg.trim().isEmpty()) {
                sendPacket("ADMIN_ANNOUNCE|" + msg.trim());
            }
        });

        JMenuItem itemClear = new JMenuItem("🗑 Xóa sạch lịch sử phòng");
        itemClear.addActionListener(e -> handleAdminClearHistory());

        popup.add(itemAnnounce);
        popup.add(itemClear);
        popup.show(parent, 0, parent.getHeight());
    }

    private void updateButtonStates() {
        OnlineUser selected = onlineList.getSelectedValue();
        boolean hasSelection = selected != null;
        boolean isNotMe = hasSelection && !selected.username.equalsIgnoreCase(myUsername);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(myRole);

        btnPrivateChat.setEnabled(isNotMe);
        btnKick.setEnabled(isNotMe && isAdmin);
        btnMute.setEnabled(isNotMe && isAdmin);
        btnUnmute.setEnabled(isNotMe && isAdmin);
    }

    // ================= KẾT NỐI MẠNG & XỬ LÝ GÓI TIN =================
    public boolean connectAndLogin(String host, int port, String user, String pass) {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            out.println("LOGIN|" + user + "|" + pass);
            String response = in.readLine();
            if (response == null) {
                closeSocket();
                return false;
            }

            if (response.startsWith("LOGIN_OK|")) {
                String[] parts = response.split("\\|", 3);
                this.myUsername = user;
                this.myRole = parts.length > 1 ? parts[1].toUpperCase() : "USER";

                boolean isAdmin = "ADMIN".equalsIgnoreCase(myRole);
                lblRoomTitle.setText("Tour Crew " + (isAdmin ? "👑 [ADMIN]" : ""));
                lblRoomSubtitle.setText("🟢 Đang hoạt động • Bạn là " + myUsername);
                btnAdminMenu.setVisible(isAdmin);
                pnlAdminControls.setVisible(isAdmin);

                startListenerThread();

                syncTimer = new javax.swing.Timer(10000, e -> requestOnlineList());
                syncTimer.start();
                requestOnlineList();

                return true;
            } else {
                String errorMsg = response.replace("LOGIN_FAIL|", "");
                JOptionPane.showMessageDialog(this, errorMsg, "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                closeSocket();
                return false;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối Server (" + host + ":" + port + ")!\nHãy chắc chắn ChatServer đang chạy.", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            closeSocket();
            return false;
        }
    }

    private void startListenerThread() {
        listenerThread = new Thread(() -> {
            try {
                String rawPacket;
                while ((rawPacket = in.readLine()) != null) {
                    final String packet = rawPacket;
                    SwingUtilities.invokeLater(() -> processIncomingPacket(packet));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lblRoomSubtitle.setText("🔴 Mất kết nối khỏi máy chủ");
                    addBubbleMessage("Hệ thống", "SYSTEM", "Đã ngắt kết nối khỏi Server.", "", false, false, true);
                });
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void processIncomingPacket(String packet) {
        String[] parts = packet.split("\\|", 2);
        String type = parts[0].toUpperCase();
        String content = parts.length > 1 ? parts[1] : "";

        switch (type) {
            case "CHAT":
                handleChatPacket(content);
                break;
            case "PRIVATE":
                handlePrivatePacket(content);
                break;
            case "SERVER":
                addBubbleMessage("Hệ thống", "SYSTEM", content, "", false, false, true);
                if (content.contains("tham gia") || content.contains("thoát") || content.contains("kick")) {
                    requestOnlineList();
                }
                break;
            case "ANNOUNCEMENT":
                addBubbleMessage("Thông báo", "ADMIN", content, "", false, false, true);
                break;
            case "ONLINE_LIST":
                updateOnlineListFromPacket(content);
                break;
            case "HISTORY":
                handleHistoryLine(content);
                break;
            case "ERROR":
                addBubbleMessage("Lỗi", "ERROR", content, "", false, false, true);
                break;
            case "KICKED":
                JOptionPane.showMessageDialog(this, content, "Bị trục xuất", JOptionPane.WARNING_MESSAGE);
                logout();
                break;
        }
    }

    private void handleChatPacket(String content) {
        // [15:10:00] [ROLE-username]: message
        Pattern p = Pattern.compile("^\\[(.*?)\\] \\[(.*?)-(.*?)\\]: (.*)$");
        Matcher m = p.matcher(content);
        if (m.matches()) {
            String time = m.group(1);
            String role = m.group(2);
            String sender = m.group(3);
            String msg = m.group(4);
            boolean isMe = sender.equalsIgnoreCase(myUsername);
            addBubbleMessage(sender, role, msg, time, isMe, false, false);
        } else {
            addBubbleMessage("Phòng", "USER", content, "", false, false, false);
        }
    }

    private void handlePrivatePacket(String content) {
        // [15:10:00] [Mật từ duy]: alo
        Pattern p = Pattern.compile("^\\[(.*?)\\] \\[(Mật từ|Mật tới) (.*?)\\]: (.*)$");
        Matcher m = p.matcher(content);
        if (m.matches()) {
            String time = m.group(1);
            String direction = m.group(2);
            String otherUser = m.group(3);
            String msg = m.group(4);
            boolean isMe = direction.startsWith("Mật tới");
            addBubbleMessage(otherUser, "USER", msg, time, isMe, true, false);
        } else {
            addBubbleMessage("Mật", "USER", content, "", false, true, false);
        }
    }

    private void handleHistoryLine(String content) {
        if (content.startsWith("---") || content.contains("LỊCH SỬ")) {
            addBubbleMessage("Hệ thống", "SYSTEM", content, "", false, false, true);
            return;
        }
        handleChatPacket(content);
    }

    private void updateOnlineListFromPacket(String content) {
        onlineListModel.clear();
        Set<String> validUsers = new HashSet<>();

        if (!content.trim().isEmpty()) {
            String[] userTokens = content.split(",");
            for (String token : userTokens) {
                String[] pair = token.split(":", 2);
                String uName = pair[0].trim();
                String uRole = pair.length > 1 ? pair[1].trim() : "USER";
                if (!uName.isEmpty()) {
                    onlineListModel.addElement(new OnlineUser(uName, uRole));
                    validUsers.add(uName.toLowerCase());
                }
            }
        }

        lblOnlineTitle.setText("👥 Đang Online (" + onlineListModel.size() + ")");
        lblRoomSubtitle.setText("🟢 " + onlineListModel.size() + " thành viên trực tuyến • " + myUsername);

        String currentSelected = (String) comboTarget.getSelectedItem();
        comboTarget.removeAllItems();
        comboTarget.addItem("🌐 Tất cả (Phòng chat)");

        for (int i = 0; i < onlineListModel.size(); i++) {
            OnlineUser u = onlineListModel.get(i);
            if (!u.username.equalsIgnoreCase(myUsername)) {
                comboTarget.addItem("@" + u.username);
            }
        }

        if (currentSelected != null && !currentSelected.startsWith("🌐")) {
            String targetName = currentSelected.replace("@", "").toLowerCase();
            if (validUsers.contains(targetName)) {
                comboTarget.setSelectedItem(currentSelected);
            }
        }

        updateButtonStates();
    }

    private void requestOnlineList() {
        sendPacket("ONLINE");
    }

    private void sendPacket(String packet) {
        if (out != null) {
            out.println(packet);
        }
    }

    private void confirmAndExit() {
        int res = JOptionPane.showConfirmDialog(this, "Bạn có muốn thoát khỏi phòng chat không?", "Xác nhận thoát", JOptionPane.YES_NO_OPTION);
        if (res == JOptionPane.YES_OPTION) {
            closeSocket();
            System.exit(0);
        }
    }

    private void logout() {
        closeSocket();
        setVisible(false);
        dispose();
        SwingUtilities.invokeLater(() -> showAuthDialog());
    }

    private void closeSocket() {
        if (syncTimer != null) syncTimer.stop();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    // ================= 5. CỬA SỔ XEM LỊCH SỬ CHI TIẾT (HISTORY VIEWER) =================
    private void openHistoryViewerDialog() {
        JDialog historyDialog = new JDialog(this, "Lịch Sử Trò Chuyện Chi Tiết", true);
        historyDialog.setSize(680, 520);
        historyDialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        panel.setBackground(Color.WHITE);

        // Header bộ lọc
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);

        JLabel lblSearch = new JLabel("🔍 Tìm kiếm:");
        lblSearch.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JTextField txtSearch = new JTextField(18);

        JButton btnExport = new JButton("💾 Xuất file TXT");
        btnExport.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton btnAdminClear = new JButton("🗑 Xóa lịch sử");
        btnAdminClear.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAdminClear.setBackground(new Color(239, 68, 68));
        btnAdminClear.setForeground(Color.WHITE);
        btnAdminClear.setVisible("ADMIN".equalsIgnoreCase(myRole));

        filterBar.add(lblSearch);
        filterBar.add(txtSearch);
        filterBar.add(btnExport);
        filterBar.add(btnAdminClear);

        // Nội dung lịch sử
        DefaultListModel<String> historyModel = new DefaultListModel<>();
        JList<String> listHistory = new JList<>(historyModel);
        listHistory.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        List<String> allLines = new ArrayList<>();
        // Đọc từ file chat_history.txt
        File histFile = new File("chat_history.txt");
        if (histFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(histFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        allLines.add(line);
                        historyModel.addElement(line);
                    }
                }
            } catch (IOException e) {
                historyModel.addElement("Lỗi đọc file lịch sử: " + e.getMessage());
            }
        } else {
            historyModel.addElement("Chưa có lịch sử trò chuyện nào.");
        }

        // Tìm kiếm thời gian thực
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String query = txtSearch.getText().trim().toLowerCase();
                historyModel.clear();
                for (String line : allLines) {
                    if (query.isEmpty() || line.toLowerCase().contains(query)) {
                        historyModel.addElement(line);
                    }
                }
            }
        });

        // Xuất file
        btnExport.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("exported_chat_history.txt"));
            int res = chooser.showSaveDialog(historyDialog);
            if (res == JFileChooser.APPROVE_OPTION) {
                File target = chooser.getSelectedFile();
                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
                    for (String l : allLines) {
                        bw.write(l);
                        bw.newLine();
                    }
                    JOptionPane.showMessageDialog(historyDialog, "Đã xuất file thành công tại:\n" + target.getAbsolutePath(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(historyDialog, "Lỗi khi lưu file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Xóa lịch sử (Admin)
        btnAdminClear.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(historyDialog, "Bạn có chắc chắn muốn xóa toàn bộ lịch sử?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                sendPacket("ADMIN_CLEAR_HISTORY");
                allLines.clear();
                historyModel.clear();
                historyModel.addElement("Lịch sử đã được xóa sạch.");
            }
        });

        panel.add(filterBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(listHistory), BorderLayout.CENTER);

        historyDialog.setContentPane(panel);
        historyDialog.setVisible(true);
    }

    // ================= 6. MÀN HÌNH ĐĂNG NHẬP & ĐĂNG KÝ (AUTH DIALOG) =================
    public static void showAuthDialog() {
        JDialog authDialog = new JDialog((Frame) null, "Tour Crew - Đăng Nhập & Đăng Ký", true);
        authDialog.setSize(480, 480);
        authDialog.setLocationRelativeTo(null);
        authDialog.setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);

        // Banner Header
        JPanel banner = new JPanel();
        banner.setBackground(new Color(15, 23, 42)); // Slate 900
        banner.setBorder(new EmptyBorder(16, 20, 16, 20));
        banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));

        JLabel lblLogo = new JLabel("💬 TOUR CREW MESSENGER");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("Hệ thống Chat Socket TCP • Đăng nhập & Đăng ký phân quyền");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(148, 163, 184));
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        banner.add(lblLogo);
        banner.add(Box.createVerticalStrut(4));
        banner.add(lblSub);
        mainPanel.add(banner, BorderLayout.NORTH);

        // Tabbed Panel: Đăng nhập & Đăng ký
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // ----- TAB 1: ĐĂNG NHẬP -----
        JPanel pnlLogin = new JPanel();
        pnlLogin.setLayout(new BoxLayout(pnlLogin, BoxLayout.Y_AXIS));
        pnlLogin.setBorder(new EmptyBorder(18, 26, 18, 26));
        pnlLogin.setBackground(Color.WHITE);

        JPanel formLogin = new JPanel(new GridLayout(4, 2, 8, 10));
        formLogin.setOpaque(false);

        JTextField txtHost = new JTextField(DEFAULT_HOST);
        JTextField txtPort = new JTextField(String.valueOf(DEFAULT_PORT));
        String[] samples = {"admin", "duy", "hai", "long"};
        JComboBox<String> cmbUser = new JComboBox<>(samples);
        cmbUser.setEditable(true);
        JPasswordField txtPass = new JPasswordField("123");

        formLogin.add(new JLabel("Máy chủ (Host):"));
        formLogin.add(txtHost);
        formLogin.add(new JLabel("Cổng (Port):"));
        formLogin.add(txtPort);
        formLogin.add(new JLabel("Tài khoản:"));
        formLogin.add(cmbUser);
        formLogin.add(new JLabel("Mật khẩu:"));
        formLogin.add(txtPass);

        JButton btnDoLogin = new JButton("Đăng nhập vào phòng ➔");
        btnDoLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDoLogin.setBackground(new Color(139, 92, 246)); // Purple
        btnDoLogin.setForeground(Color.WHITE);
        btnDoLogin.setFocusPainted(false);
        btnDoLogin.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));
        btnDoLogin.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel lblLoginHint = new JLabel("<html><center>Mẹo: Đăng nhập bằng tài khoản <b>admin</b> (mật khẩu: 123) để trải nghiệm quyền Quản trị viên!</center></html>");
        lblLoginHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblLoginHint.setForeground(new Color(100, 116, 139));
        lblLoginHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        pnlLogin.add(formLogin);
        pnlLogin.add(Box.createVerticalStrut(12));
        pnlLogin.add(btnDoLogin);
        pnlLogin.add(Box.createVerticalStrut(8));
        pnlLogin.add(lblLoginHint);

        // ----- TAB 2: ĐĂNG KÝ TÀI KHOẢN MỚI -----
        JPanel pnlRegister = new JPanel();
        pnlRegister.setLayout(new BoxLayout(pnlRegister, BoxLayout.Y_AXIS));
        pnlRegister.setBorder(new EmptyBorder(18, 26, 18, 26));
        pnlRegister.setBackground(Color.WHITE);

        JPanel formReg = new JPanel(new GridLayout(5, 2, 8, 8));
        formReg.setOpaque(false);

        JTextField txtRegUser = new JTextField();
        JPasswordField txtRegPass = new JPasswordField();
        JPasswordField txtRegPassConfirm = new JPasswordField();
        JComboBox<String> cmbRegRole = new JComboBox<>(new String[]{"👤 USER (Thành viên)", "👑 ADMIN (Quản trị viên)"});
        JPasswordField txtAdminSecret = new JPasswordField();
        txtAdminSecret.setEnabled(false); // Chỉ bật nếu chọn ADMIN

        cmbRegRole.addActionListener(e -> {
            boolean isAdmin = cmbRegRole.getSelectedIndex() == 1;
            txtAdminSecret.setEnabled(isAdmin);
            if (isAdmin) {
                txtAdminSecret.requestFocusInWindow();
            }
        });

        formReg.add(new JLabel("Tài khoản mới:"));
        formReg.add(txtRegUser);
        formReg.add(new JLabel("Mật khẩu:"));
        formReg.add(txtRegPass);
        formReg.add(new JLabel("Xác nhận mật khẩu:"));
        formReg.add(txtRegPassConfirm);
        formReg.add(new JLabel("Phân quyền:"));
        formReg.add(cmbRegRole);
        formReg.add(new JLabel("Mã Admin (nếu chọn ADMIN):"));
        formReg.add(txtAdminSecret);

        JButton btnDoRegister = new JButton("Tạo tài khoản & Đăng nhập ➔");
        btnDoRegister.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDoRegister.setBackground(new Color(16, 185, 129)); // Green
        btnDoRegister.setForeground(Color.WHITE);
        btnDoRegister.setFocusPainted(false);
        btnDoRegister.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));
        btnDoRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel lblRegHint = new JLabel("<html><center>Mã bảo mật khi chọn quyền Admin là: <b>admin123</b></center></html>");
        lblRegHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblRegHint.setForeground(new Color(100, 116, 139));
        lblRegHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        pnlRegister.add(formReg);
        pnlRegister.add(Box.createVerticalStrut(10));
        pnlRegister.add(btnDoRegister);
        pnlRegister.add(Box.createVerticalStrut(6));
        pnlRegister.add(lblRegHint);

        tabbedPane.addTab("🔑 Đăng Nhập", pnlLogin);
        tabbedPane.addTab("📝 Đăng Ký Tài Khoản", pnlRegister);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Xử lý sự kiện Đăng nhập
        ActionListener doLoginAction = e -> {
            String host = txtHost.getText().trim();
            int port = parsePort(txtPort.getText().trim());
            Object uObj = cmbUser.getSelectedItem();
            String u = uObj != null ? uObj.toString().trim() : "";
            String p = new String(txtPass.getPassword()).trim();

            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(authDialog, "Vui lòng nhập tài khoản và mật khẩu!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }

            ChatClientGUI client = new ChatClientGUI();
            if (client.connectAndLogin(host, port, u, p)) {
                authDialog.dispose();
                client.setVisible(true);
            }
        };
        btnDoLogin.addActionListener(doLoginAction);
        txtPass.addActionListener(doLoginAction);

        // Xử lý sự kiện Đăng ký
        btnDoRegister.addActionListener(e -> {
            String host = txtHost.getText().trim();
            int port = parsePort(txtPort.getText().trim());
            String u = txtRegUser.getText().trim();
            String p = new String(txtRegPass.getPassword()).trim();
            String pConf = new String(txtRegPassConfirm.getPassword()).trim();
            boolean isAdmin = cmbRegRole.getSelectedIndex() == 1;
            String secret = new String(txtAdminSecret.getPassword()).trim();

            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(authDialog, "Tài khoản và mật khẩu không được để trống!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!p.equals(pConf)) {
                JOptionPane.showMessageDialog(authDialog, "Mật khẩu xác nhận không khớp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (isAdmin && !secret.equals("admin123")) {
                JOptionPane.showMessageDialog(authDialog, "Mã bí mật Admin không chính xác! (Mẹo: mã là 'admin123')", "Lỗi phân quyền", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String role = isAdmin ? "ADMIN" : "USER";

            // Kết nối socket tạm để gửi lệnh REGISTER|u|p|role
            try (Socket s = new Socket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

                writer.println("REGISTER|" + u + "|" + p + "|" + role);
                String resp = reader.readLine();

                if (resp != null && resp.startsWith("REGISTER_OK|")) {
                    JOptionPane.showMessageDialog(authDialog, resp.split("\\|", 2)[1] + "\nHệ thống sẽ tự động đăng nhập ngay bây giờ!", "Đăng ký thành công", JOptionPane.INFORMATION_MESSAGE);
                    // Tự động đăng nhập
                    ChatClientGUI client = new ChatClientGUI();
                    if (client.connectAndLogin(host, port, u, p)) {
                        authDialog.dispose();
                        client.setVisible(true);
                    }
                } else {
                    String err = (resp != null && resp.startsWith("REGISTER_FAIL|")) ? resp.split("\\|", 2)[1] : "Đăng ký thất bại.";
                    JOptionPane.showMessageDialog(authDialog, err, "Lỗi đăng ký", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(authDialog, "Không thể kết nối Server (" + host + ":" + port + ")!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        authDialog.setContentPane(mainPanel);
        authDialog.setVisible(true);
    }

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return DEFAULT_PORT;
        }
    }

    private static JButton createPillButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(226, 232, 240), 16),
                new EmptyBorder(6, 12, 6, 12)
        ));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        public RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }

        public boolean isBorderOpaque() {
            return false;
        }

        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> showAuthDialog());
    }
}
