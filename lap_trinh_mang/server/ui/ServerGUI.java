package server.ui;

import client.ui.AvatarPanel;
import client.ui.ModernScrollBarUI;
import client.ui.PillButton;
import client.ui.PillTextField;
import client.ui.UIUtils;
import common.Protocol;
import common.User;
import server.ChatServer;
import server.ServerEventListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ServerGUI: Giao diện Trung tâm Quản trị Máy chủ (Server Control Dashboard)
 * chuẩn phong cách Cloud Admin hiện đại với Thẻ thống kê thời gian thực và Bảng điều khiển.
 */
public class ServerGUI extends JFrame implements ServerEventListener {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private PillTextField txtPort;
    private PillButton btnStart;
    private PillButton btnStop;

    // Stat Cards
    private JLabel lblStatStatus;
    private JLabel lblStatOnline;
    private JLabel lblStatMessages;
    private JLabel lblStatUptime;

    private JTable tblUsers;
    private DefaultTableModel userTableModel;
    private JTextArea txtLogs;
    private PillTextField txtBroadcast;
    private PillButton btnBroadcast;

    private ChatServer chatServer;
    private int messageCount = 0;
    private long serverStartTime = 0;
    private Timer uptimeTimer;

    public ServerGUI() {
        super("Chat Server Control Dashboard - Port 5000 (SQLite WAL)");
        initComponents();
        setupEvents();
        // Tự động kích hoạt Server ngay khi khởi động
        SwingUtilities.invokeLater(this::startServer);
    }

    private void initComponents() {
        setSize(1020, 680);
        setMinimumSize(new Dimension(880, 540));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(UIUtils.BG_MAIN);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        // 1. THANH ĐIỀU KHIỂN & CÁC THẺ THỐNG KÊ (TOP STAT CARDS)
        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setOpaque(false);

        // Hàng điều khiển Server (Port, Start, Stop)
        JPanel controlCard = createControlCard();
        topContainer.add(controlCard, BorderLayout.NORTH);

        // Hàng 4 thẻ thống kê trực quan (Stats Grid)
        JPanel statsGrid = createStatsGrid();
        topContainer.add(statsGrid, BorderLayout.SOUTH);

        root.add(topContainer, BorderLayout.NORTH);

        // 2. KHU VỰC CHÍNH: CHIA ĐÔI BẢNG CLIENT ONLINE & KHUNG TERMINAL LOGS
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.48);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);

        // Cột trái: Bảng Client Online
        JPanel userTableCard = createUserTableCard();
        // Cột phải: Terminal Logs
        JPanel logsCard = createLogsCard();

        splitPane.setLeftComponent(userTableCard);
        splitPane.setRightComponent(logsCard);
        root.add(splitPane, BorderLayout.CENTER);

        // 3. THANH THÔNG BÁO KHẨN CẤP PHÍA DƯỚI (BROADCAST ALERT BAR)
        JPanel bottomBar = createBottomAlertBar();
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel createControlCard() {
        JPanel card = new JPanel(new BorderLayout(14, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 16, 10, 16));

        JPanel leftCol = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftCol.setOpaque(false);

        JLabel lblTitle = new JLabel("⚡ Host Server Control:");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(UIUtils.TEXT_DARK);
        leftCol.add(lblTitle);

        leftCol.add(new JLabel("Port:"));
        txtPort = new PillTextField("5000", null);
        txtPort.setText("5000");
        txtPort.setPreferredSize(new Dimension(80, 32));
        leftCol.add(txtPort);

        btnStart = new PillButton("▶ Bật Server", PillButton.Style.PRIMARY);
        btnStart.setPreferredSize(new Dimension(120, 34));
        leftCol.add(btnStart);

        btnStop = new PillButton("⏹ Tắt Server", PillButton.Style.DANGER);
        btnStop.setPreferredSize(new Dimension(110, 34));
        btnStop.setEnabled(false);
        leftCol.add(btnStop);

        PillButton btnOpenWeb = new PillButton("🌐 Mở Web Chat", PillButton.Style.SECONDARY);
        btnOpenWeb.setPreferredSize(new Dimension(130, 34));
        btnOpenWeb.setToolTipText("Mở giao diện Web trong trình duyệt mặc định");
        btnOpenWeb.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI("http://localhost:8080"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Hãy mở trình duyệt và truy cập: http://localhost:8080", "Web Chat", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        leftCol.add(btnOpenWeb);

        card.add(leftCol, BorderLayout.WEST);

        // Hiển thị IP LAN để các máy tính khác trong cùng Wi-Fi / Mạng kết nối
        String lanIp = "127.0.0.1";
        try {
            lanIp = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        JLabel lblLan = new JLabel("🌐 IP Mạng LAN: " + lanIp + "  (Cho máy khác kết nối)");
        lblLan.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLan.setForeground(new Color(37, 117, 252));
        card.add(lblLan, BorderLayout.EAST);

        return card;
    }

    private JPanel createStatsGrid() {
        JPanel grid = new JPanel(new GridLayout(1, 4, 12, 0));
        grid.setOpaque(false);

        lblStatStatus = new JLabel("ĐÃ DỪNG");
        lblStatOnline = new JLabel("0 Users");
        lblStatMessages = new JLabel("0 Messages");
        lblStatUptime = new JLabel("00:00:00");

        grid.add(createSingleStatCard("Trạng Thái Host", lblStatStatus, "🔴", new Color(254, 242, 242), UIUtils.BADGE_RED));
        grid.add(createSingleStatCard("Kết Nối Trực Tuyến", lblStatOnline, "👥", new Color(239, 246, 255), UIUtils.PRIMARY_BLUE));
        grid.add(createSingleStatCard("Tin Nhắn Đã Xử Lý", lblStatMessages, "💬", new Color(243, 232, 255), new Color(124, 58, 237)));
        grid.add(createSingleStatCard("Thời Gian Hoạt Động", lblStatUptime, "⏱", new Color(254, 243, 199), new Color(217, 119, 6)));

        return grid;
    }

    private JPanel createSingleStatCard(String title, JLabel valLabel, String icon, Color iconBg, Color iconColor) {
        JPanel card = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Icon tròn Pastel bên trái
        JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(iconBg);
                g2.fillOval(0, 0, 38, 38);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconWrap.setOpaque(false);
        iconWrap.setPreferredSize(new Dimension(38, 38));

        JLabel lblIcon = new JLabel(icon);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        lblIcon.setForeground(iconColor);
        iconWrap.add(lblIcon);
        card.add(iconWrap, BorderLayout.WEST);

        // Cột chữ (Title & Value)
        JPanel textCol = new JPanel(new GridLayout(2, 1, 0, 2));
        textCol.setOpaque(false);

        JLabel lblT = new JLabel(title);
        lblT.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblT.setForeground(UIUtils.TEXT_MUTED);

        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valLabel.setForeground(UIUtils.TEXT_DARK);

        textCol.add(lblT);
        textCol.add(valLabel);
        card.add(textCol, BorderLayout.CENTER);

        return card;
    }

    private JPanel createUserTableCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel lblHeader = new JLabel("👥 Danh Sách Client Kết Nối (Real-Time)");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblHeader.setForeground(UIUtils.TEXT_DARK);
        card.add(lblHeader, BorderLayout.NORTH);

        String[] cols = {"Tài khoản", "Vai trò", "IP Address", "Trạng thái"};
        userTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblUsers = new JTable(userTableModel);
        tblUsers.setRowHeight(30);
        tblUsers.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblUsers.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tblUsers.getTableHeader().setBackground(new Color(248, 249, 251));
        tblUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblUsers.setShowVerticalLines(false);
        tblUsers.setGridColor(new Color(241, 245, 249));

        DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
        centerRender.setHorizontalAlignment(JLabel.CENTER);
        tblUsers.getColumnModel().getColumn(1).setCellRenderer(centerRender);
        tblUsers.getColumnModel().getColumn(3).setCellRenderer(centerRender);

        JScrollPane scroll = new JScrollPane(tblUsers);
        scroll.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_LIGHT));
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        card.add(scroll, BorderLayout.CENTER);

        // Nút Kick dưới đáy bảng
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        actionRow.setOpaque(false);
        PillButton btnKick = new PillButton("👢 Kick Client Đã Chọn", PillButton.Style.SECONDARY);
        btnKick.setPreferredSize(new Dimension(160, 32));
        btnKick.addActionListener(e -> kickSelectedClient());
        actionRow.add(btnKick);
        card.add(actionRow, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createLogsCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        JLabel lblHeader = new JLabel("💻 Nhật Ký Máy Chủ (Server Activity Terminal)");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblHeader.setForeground(UIUtils.TEXT_DARK);

        JButton btnClear = new JButton("🧹 Xóa Log");
        btnClear.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClear.setBorder(null);
        btnClear.setContentAreaFilled(false);
        btnClear.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClear.addActionListener(e -> txtLogs.setText(""));

        headerRow.add(lblHeader, BorderLayout.WEST);
        headerRow.add(btnClear, BorderLayout.EAST);
        card.add(headerRow, BorderLayout.NORTH);

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(15, 23, 42)); // Dark Slate
        txtLogs.setForeground(new Color(226, 232, 240));
        txtLogs.setCaretColor(Color.WHITE);
        txtLogs.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane scrollLogs = new JScrollPane(txtLogs);
        scrollLogs.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        scrollLogs.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        card.add(scrollLogs, BorderLayout.CENTER);

        return card;
    }

    private JPanel createBottomAlertBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIUtils.enableAntialiasing(g2);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JLabel lblTag = new JLabel("📢 Phát thông báo Host:");
        lblTag.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTag.setForeground(UIUtils.TEXT_DARK);
        bar.add(lblTag, BorderLayout.WEST);

        txtBroadcast = new PillTextField("Nhập thông báo gửi đến toàn bộ các phòng chat...", null);
        bar.add(txtBroadcast, BorderLayout.CENTER);

        btnBroadcast = new PillButton("Gửi Toàn Phòng", PillButton.Style.PRIMARY);
        btnBroadcast.setPreferredSize(new Dimension(140, 36));
        bar.add(btnBroadcast, BorderLayout.EAST);

        return bar;
    }

    private void setupEvents() {
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());

        btnBroadcast.addActionListener(e -> sendServerAlert());
        txtBroadcast.addActionListener(e -> sendServerAlert());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (chatServer != null && chatServer.isRunning()) {
                    int confirm = JOptionPane.showConfirmDialog(
                            ServerGUI.this,
                            "Server đang hoạt động. Bạn có chắc muốn dừng Server và thoát?",
                            "Xác nhận đóng",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (confirm == JOptionPane.YES_OPTION) {
                        stopServer();
                        dispose();
                        System.exit(0);
                    }
                } else {
                    dispose();
                    System.exit(0);
                }
            }
        });
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1024 || port > 65535) throw new Exception();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập cổng hợp lệ (1024 - 65535)!", "Lỗi cổng", JOptionPane.ERROR_MESSAGE);
            return;
        }

        chatServer = new ChatServer(port);
        chatServer.setListener(this);

        boolean success = chatServer.startServer();
        if (success) {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            txtPort.setEnabled(false);

            lblStatStatus.setText("PORT " + port);
            lblStatStatus.setForeground(UIUtils.ONLINE_GREEN);

            serverStartTime = System.currentTimeMillis();
            uptimeTimer = new Timer(1000, e -> updateUptime());
            uptimeTimer.start();
        } else {
            JOptionPane.showMessageDialog(this, "Không thể mở cổng " + port + ". Cổng có thể đang bị chiếm dụng!", "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        if (chatServer != null) {
            chatServer.stopServer();
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        txtPort.setEnabled(true);

        lblStatStatus.setText("ĐÃ DỪNG");
        lblStatStatus.setForeground(UIUtils.BADGE_RED);

        if (uptimeTimer != null) {
            uptimeTimer.stop();
        }
        lblStatUptime.setText("00:00:00");
        lblStatOnline.setText("0 Users");
        userTableModel.setRowCount(0);
    }

    private void updateUptime() {
        if (serverStartTime == 0) return;
        long elapsedSec = (System.currentTimeMillis() - serverStartTime) / 1000;
        long hours = elapsedSec / 3600;
        long mins = (elapsedSec % 3600) / 60;
        long secs = elapsedSec % 60;
        lblStatUptime.setText(String.format("%02d:%02d:%02d", hours, mins, secs));
    }

    private void sendServerAlert() {
        String alert = txtBroadcast.getText().trim();
        if (alert.isEmpty()) return;
        if (chatServer == null || !chatServer.isRunning()) {
            JOptionPane.showMessageDialog(this, "Server chưa được bật!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        chatServer.getRoomManager().broadcast(Protocol.RES_SYSTEM_MSG + Protocol.DELIMITER + "📢 [SERVER HOST]: " + alert);
        txtBroadcast.setText("");
        onLog("[SERVER ALERT] " + alert);
    }

    private void kickSelectedClient() {
        int row = tblUsers.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 client trong bảng để Kick!", "Chưa chọn", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String username = (String) userTableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn kick @" + username + " khỏi phòng?", "Xác nhận Kick", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION && chatServer != null) {
            chatServer.getRoomManager().kickUser(username, "Bị trục xuất trực tiếp bởi Server Host");
        }
    }

    // --- Callbacks từ ServerEventListener ---
    @Override
    public void onLog(String logMessage) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(TIME_FMT);
            txtLogs.append("[" + time + "] " + logMessage + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    @Override
    public void onClientConnected(String username, String role, String ipAddress) {}

    @Override
    public void onClientDisconnected(String username) {}

    @Override
    public void onMessageCountIncremented() {
        SwingUtilities.invokeLater(() -> {
            messageCount++;
            lblStatMessages.setText(messageCount + " Messages");
        });
    }

    @Override
    public void onOnlineListUpdated(List<User> onlineList) {
        SwingUtilities.invokeLater(() -> {
            userTableModel.setRowCount(0);
            for (User u : onlineList) {
                var handler = chatServer != null ? chatServer.getRoomManager().getClient(u.getUsername()) : null;
                String ip = handler != null ? handler.getRemoteAddress() : "N/A";
                userTableModel.addRow(new Object[]{
                        u.getUsername(),
                        u.getRole(),
                        ip,
                        u.getStatus()
                });
            }
            lblStatOnline.setText(onlineList.size() + " Users");
        });
    }
}
