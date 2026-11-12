package client.ui;

import common.User;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * MemberItemPanel: Mục hiển thị thành viên trong danh sách "Members" ở Sidebar phải.
 */
public class MemberItemPanel extends JPanel {
    private final User user;
    private final boolean isCurrentUser;
    private boolean isHover = false;
    private final Runnable onActionClick;

    public MemberItemPanel(User user, boolean isCurrentUser, Runnable onActionClick) {
        this.user = user;
        this.isCurrentUser = isCurrentUser;
        this.onActionClick = onActionClick;

        setOpaque(false);
        setPreferredSize(new Dimension(220, 52));
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
                if (onActionClick != null) onActionClick.run();
            }
        });
    }

    public User getUser() {
        return user;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        if (isHover) {
            g2.setColor(new Color(243, 244, 246));
            g2.fill(new RoundRectangle2D.Float(4, 2, w - 8, h - 4, 12, 12));
        }

        // 1. Avatar tròn 34px
        int avSize = 34;
        int avX = 10;
        int avY = (h - avSize) / 2;

        Color[] colors = UIUtils.getAvatarGradient(user.getUsername());
        g2.setPaint(new GradientPaint(avX, avY, colors[0], avX + avSize, avY + avSize, colors[1]));
        g2.fill(new Ellipse2D.Float(avX, avY, avSize, avSize));

        // Initial
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        String init = user.getUsername().isEmpty() ? "?" : user.getUsername().substring(0, 1).toUpperCase();
        FontMetrics fm = g2.getFontMetrics();
        int tx = avX + (avSize - fm.stringWidth(init)) / 2;
        int ty = avY + ((avSize - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(init, tx, ty);

        // 2. Tên thành viên
        int textX = avX + avSize + 10;
        g2.setColor(UIUtils.TEXT_DARK);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        String nameStr = user.getUsername() + (isCurrentUser ? " (Bạn)" : "");
        g2.drawString(nameStr, textX, avY + 14);

        // Vai trò / Trạng thái
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        if (user.isAdmin()) {
            g2.setColor(UIUtils.GOLD_ADMIN);
            g2.drawString("👑 Admin • Online", textX, avY + 28);
        } else if (user.isMuted()) {
            g2.setColor(UIUtils.BADGE_RED);
            g2.drawString("🔇 Cấm chat", textX, avY + 28);
        } else {
            g2.setColor(UIUtils.TEXT_MUTED);
            g2.drawString("Online", textX, avY + 28);
        }

        // 3. Chấm trạng thái tròn bên phải
        int dotSize = 8;
        int dotX = w - 18 - dotSize;
        int dotY = (h - dotSize) / 2;

        g2.setColor(user.isOnline() ? UIUtils.ONLINE_GREEN : UIUtils.OFFLINE_GRAY);
        g2.fill(new Ellipse2D.Float(dotX, dotY, dotSize, dotSize));

        g2.dispose();
    }
}

