package client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * RoomCard: Thẻ hiển thị phòng chat nổi bật (Favourites Card) với ngăn xếp Avatar như ảnh thiết kế.
 */
public class RoomCard extends JPanel {
    private final String title;
    private final String dateText;
    private int onlineCount;
    private int unreadCount;
    private boolean isHover = false;
    private boolean isSelected = false;

    public RoomCard(String title, String dateText, int onlineCount, int unreadCount, Runnable onClick) {
        this.title = title;
        this.dateText = dateText;
        this.onlineCount = onlineCount;
        this.unreadCount = unreadCount;

        setOpaque(false);
        setPreferredSize(new Dimension(240, 130));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null) onClick.run();
            }
        });
    }

    public void setOnlineCount(int count) {
        this.onlineCount = count;
        repaint();
    }

    public void setSelected(boolean sel) {
        this.isSelected = sel;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();
        int arc = 20;

        // 1. Nền Card trắng bo cong với viền nổi bật nếu được chọn
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

        if (isSelected) {
            g2.setColor(UIUtils.PRIMARY_BLUE);
            g2.setStroke(new BasicStroke(2f));
        } else if (isHover) {
            g2.setColor(new Color(203, 213, 225));
            g2.setStroke(new BasicStroke(1.2f));
        } else {
            g2.setColor(UIUtils.BORDER_LIGHT);
            g2.setStroke(new BasicStroke(1f));
        }
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc));

        // 2. Tiêu đề phòng chat
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(UIUtils.TEXT_DARK);
        g2.drawString(title, 14, 28);

        // Huy hiệu tin nhắn mới (chấm đỏ hoặc số)
        if (unreadCount > 0) {
            g2.setColor(UIUtils.BADGE_RED);
            int badgeSize = 18;
            int bx = w - 14 - badgeSize;
            int by = 14;
            g2.fill(new Ellipse2D.Float(bx, by, badgeSize, badgeSize));
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            String bText = String.valueOf(unreadCount);
            FontMetrics bfm = g2.getFontMetrics();
            g2.drawString(bText, bx + (badgeSize - bfm.stringWidth(bText)) / 2, by + 13);
        }

        // 3. Hàng ngày tháng & Tag Online
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(UIUtils.TEXT_MUTED);
        g2.drawString("📅 " + dateText, 14, 52);

        // Tag Online xanh lá bo tròn (Green Pill Tag)
        String tagText = "🟢 " + onlineCount + " Online";
        int tagW = 80;
        int tagH = 20;
        int tagX = 14 + g2.getFontMetrics().stringWidth("📅 " + dateText) + 10;
        int tagY = 38;

        g2.setColor(new Color(236, 253, 245)); // Xanh nhạt
        g2.fill(new RoundRectangle2D.Float(tagX, tagY, tagW, tagH, tagH, tagH));
        g2.setColor(new Color(5, 150, 105)); // Xanh đậm
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString(tagText, tagX + 8, tagY + 14);

        // 4. Ngăn xếp Avatar (Avatar Stack) + Thẻ +5
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(UIUtils.TEXT_SECONDARY);
        g2.drawString("Members", 14, 82);

        int startX = 14;
        int avatarY = 92;
        int avSize = 24;
        int overlap = 18;

        String[] sampleNames = {"Alice", "Bob", "Charlie", "David", "Eve"};
        for (int i = 0; i < Math.min(4, sampleNames.length); i++) {
            int curX = startX + (i * overlap);
            Color[] colors = UIUtils.getAvatarGradient(sampleNames[i]);
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Float(curX - 1, avatarY - 1, avSize + 2, avSize + 2)); // Viền trắng
            g2.setPaint(new GradientPaint(curX, avatarY, colors[0], curX + avSize, avatarY + avSize, colors[1]));
            g2.fill(new Ellipse2D.Float(curX, avatarY, avSize, avSize));
        }

        // Pill "+5"
        int plusX = startX + (4 * overlap);
        int plusW = 28;
        int plusH = avSize;
        g2.setColor(new Color(241, 245, 249));
        g2.fill(new RoundRectangle2D.Float(plusX, avatarY, plusW, plusH, plusH, plusH));
        g2.setColor(UIUtils.PRIMARY_BLUE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString("+5", plusX + 6, avatarY + 16);

        g2.dispose();
    }
}

