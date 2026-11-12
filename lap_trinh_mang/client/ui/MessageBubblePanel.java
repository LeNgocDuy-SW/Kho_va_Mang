package client.ui;

import common.Protocol;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * MessageBubblePanel: Hiển thị từng tin nhắn dạng Bong bóng Chat (Chat Bubble)
 * chuẩn thiết kế Messenger / Dribbble với Avatar và canh lề thông minh.
 */
public class MessageBubblePanel extends JPanel {
    private final String sender;
    private final String role;
    private final String timestamp;
    private final String content;
    private final boolean isMine;
    private final boolean isPrivate;
    private final boolean isSystem;

    public MessageBubblePanel(String sender, String role, String timestamp, String content, boolean isMine, boolean isPrivate, boolean isSystem) {
        this.sender = sender;
        this.role = role;
        this.timestamp = timestamp;
        this.content = content;
        this.isMine = isMine;
        this.isPrivate = isPrivate;
        this.isSystem = isSystem;

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(6, 16, 6, 16));

        initComponents();
    }

    private void initComponents() {
        if (isSystem) {
            JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            centerPanel.setOpaque(false);

            JLabel lblSys = new JLabel("ℹ️ " + content);
            lblSys.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblSys.setForeground(UIUtils.TEXT_MUTED);
            centerPanel.add(lblSys);

            add(centerPanel, BorderLayout.CENTER);
            return;
        }

        // Hàng tin nhắn chính: Chiếm trọn bề ngang để đẩy trái/phải chuẩn xác
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);

        // Avatar bên trái (nếu của người khác)
        if (!isMine) {
            AvatarPanel av = new AvatarPanel(sender, 36);
            JPanel avWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            avWrap.setOpaque(false);
            avWrap.add(av);
            row.add(avWrap, BorderLayout.WEST);
        }

        // Cột trung tâm chứa Tên, Bong bóng chat và Thời gian
        JPanel contentCol = new JPanel();
        contentCol.setLayout(new BoxLayout(contentCol, BoxLayout.Y_AXIS));
        contentCol.setOpaque(false);

        // Hàng thông tin người gửi & Thời gian
        JPanel metaRow = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 0));
        metaRow.setOpaque(false);

        String displayName = isMine ? "Bạn" : sender;
        JLabel lblName = new JLabel(displayName);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(UIUtils.TEXT_DARK);
        metaRow.add(lblName);

        if (Protocol.ROLE_ADMIN.equalsIgnoreCase(role)) {
            JLabel lblRole = new JLabel("👑 ADMIN");
            lblRole.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lblRole.setForeground(UIUtils.GOLD_ADMIN);
            metaRow.add(lblRole);
        }

        if (isPrivate) {
            JLabel lblPriv = new JLabel("🔒 [MẬT]");
            lblPriv.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lblPriv.setForeground(new Color(124, 58, 237));
            metaRow.add(lblPriv);
        }

        JLabel lblTime = new JLabel(timestamp != null ? timestamp : "");
        lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblTime.setForeground(UIUtils.TEXT_MUTED);
        metaRow.add(lblTime);

        contentCol.add(metaRow);
        contentCol.add(Box.createVerticalStrut(3));

        // Bong bóng tin nhắn
        BubbleBox bubble = new BubbleBox(content, isMine, isPrivate);
        JPanel bubbleRow = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        bubbleRow.setOpaque(false);
        bubbleRow.add(bubble);

        contentCol.add(bubbleRow);
        row.add(contentCol, BorderLayout.CENTER);

        // Avatar bên phải (nếu của chính mình)
        if (isMine) {
            AvatarPanel av = new AvatarPanel(sender, 36);
            JPanel avWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            avWrap.setOpaque(false);
            avWrap.add(av);
            row.add(avWrap, BorderLayout.EAST);
        }

        add(row, BorderLayout.CENTER);
    }

    @Override
    public Dimension getMaximumSize() {
        // Cho phép giãn hết chiều ngang của JScrollPane
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    /**
     * BubbleBox: Khối bong bóng chat bo tròn mềm mại
     */
    private static class BubbleBox extends JComponent {
        private final JTextArea textArea;
        private final boolean isMine;
        private final boolean isPrivate;

        public BubbleBox(String text, boolean isMine, boolean isPrivate) {
            this.isMine = isMine;
            this.isPrivate = isPrivate;

            setLayout(new BorderLayout());
            setOpaque(false);

            textArea = new JTextArea(text);
            textArea.setOpaque(false);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            Color textColor;
            if (isMine) textColor = UIUtils.BUBBLE_MY_TEXT;
            else if (isPrivate) textColor = UIUtils.BUBBLE_PRIVATE_TEXT;
            else textColor = UIUtils.BUBBLE_OTHER_TEXT;

            textArea.setForeground(textColor);
            textArea.setBorder(new EmptyBorder(8, 14, 8, 14));

            add(textArea, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredSize() {
            // Giới hạn chiều rộng tối đa của bubble là 480px để tự động xuống dòng đẹp mắt
            int maxWidth = 480;
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            int textWidth = fm.stringWidth(textArea.getText());

            if (textWidth > maxWidth) {
                textArea.setSize(new Dimension(maxWidth, Short.MAX_VALUE));
                Dimension d = textArea.getPreferredSize();
                return new Dimension(maxWidth, d.height);
            } else {
                Dimension d = textArea.getPreferredSize();
                return new Dimension(Math.max(d.width, 40), d.height);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            UIUtils.enableAntialiasing(g2);

            int w = getWidth();
            int h = getHeight();
            int arc = 20;

            Color bg;
            Color border;
            if (isMine) {
                bg = UIUtils.BUBBLE_MY_BG;
                border = new Color(191, 219, 254);
            } else if (isPrivate) {
                bg = UIUtils.BUBBLE_PRIVATE_BG;
                border = new Color(216, 180, 254);
            } else {
                bg = UIUtils.BUBBLE_OTHER_BG;
                border = UIUtils.BORDER_LIGHT;
            }

            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
