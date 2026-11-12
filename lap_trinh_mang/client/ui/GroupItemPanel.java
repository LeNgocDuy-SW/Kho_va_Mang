package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * GroupItemPanel: Mục hiển thị phòng chat / nhóm trong danh sách "Groups" ở Sidebar trái.
 */
public class GroupItemPanel extends JPanel {
    private final String groupName;
    private final String subtitle;
    private final int badgeCount;
    private boolean isSelected = false;
    private boolean isHover = false;
    private final Runnable onClick;

    public GroupItemPanel(String groupName, String subtitle, int badgeCount, Runnable onClick) {
        this.groupName = groupName;
        this.subtitle = subtitle;
        this.badgeCount = badgeCount;
        this.onClick = onClick;

        setOpaque(false);
        setPreferredSize(new Dimension(240, 64));
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

    public void setSelected(boolean sel) {
        this.isSelected = sel;
        repaint();
    }

    public String getGroupName() {
        return groupName;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        // Nền hover hoặc selected
        if (isSelected) {
            g2.setColor(new Color(239, 246, 255)); // Xanh siêu nhạt
            g2.fill(new RoundRectangle2D.Float(4, 4, w - 8, h - 8, 14, 14));
            g2.setColor(new Color(191, 219, 254));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(4, 4, w - 8, h - 8, 14, 14));
        } else if (isHover) {
            g2.setColor(new Color(243, 244, 246));
            g2.fill(new RoundRectangle2D.Float(4, 4, w - 8, h - 8, 14, 14));
        }

        // 1. Avatar tròn Gradient đa sắc
        int avSize = 42;
        int avX = 14;
        int avY = (h - avSize) / 2;

        Color[] colors = UIUtils.getAvatarGradient(groupName);
        g2.setPaint(new GradientPaint(avX, avY, colors[0], avX + avSize, avY + avSize, colors[1]));
        g2.fill(new Ellipse2D.Float(avX, avY, avSize, avSize));

        // Initial
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        String init = groupName.isEmpty() ? "?" : groupName.substring(0, 1).toUpperCase();
        FontMetrics fm = g2.getFontMetrics();
        int tx = avX + (avSize - fm.stringWidth(init)) / 2;
        int ty = avY + ((avSize - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(init, tx, ty);

        // 2. Tên nhóm
        int textX = avX + avSize + 12;
        g2.setColor(UIUtils.TEXT_DARK);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g2.drawString(groupName, textX, avY + 18);

        // Subtitle
        g2.setColor(UIUtils.TEXT_MUTED);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.drawString(subtitle, textX, avY + 34);

        // 3. Huy hiệu đỏ số tin nhắn mới
        if (badgeCount > 0) {
            int bw = 20;
            int bh = 20;
            int bx = w - 24 - bw;
            int by = (h - bh) / 2;

            g2.setColor(UIUtils.BADGE_RED);
            g2.fill(new Ellipse2D.Float(bx, by, bw, bh));

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String bt = String.valueOf(badgeCount);
            FontMetrics bfm = g2.getFontMetrics();
            g2.drawString(bt, bx + (bw - bfm.stringWidth(bt)) / 2, by + 14);
        }

        g2.dispose();
    }
}

