package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * AvatarPanel: Component hiển thị Avatar tròn hiện đại với Gradient đa sắc và chấm trạng thái Online.
 */
public class AvatarPanel extends JComponent {
    private final String name;
    private final int size;
    private boolean isOnline;
    private boolean showStatus;

    public AvatarPanel(String name, int size) {
        this(name, size, false, false);
    }

    public AvatarPanel(String name, int size, boolean isOnline, boolean showStatus) {
        this.name = name != null ? name : "?";
        this.size = size;
        this.isOnline = isOnline;
        this.showStatus = showStatus;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int d = size;
        // 1. Vẽ vòng tròn Gradient nền
        Color[] colors = UIUtils.getAvatarGradient(name);
        GradientPaint gp = new GradientPaint(0, 0, colors[0], d, d, colors[1]);
        g2.setPaint(gp);
        g2.fill(new Ellipse2D.Float(0, 0, d, d));

        // 2. Vẽ chữ cái đại diện (Initial)
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        g2.setColor(Color.WHITE);
        int fontSize = Math.max(11, size / 2);
        g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));

        FontMetrics fm = g2.getFontMetrics();
        int tx = (d - fm.stringWidth(initial)) / 2;
        int ty = ((d - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(initial, tx, ty);

        // 3. Vẽ chấm trạng thái Online / Offline (nếu bật)
        if (showStatus) {
            int dotSize = Math.max(8, size / 4);
            int dotX = d - dotSize;
            int dotY = d - dotSize;

            // Viền trắng bảo vệ chấm
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Float(dotX - 1, dotY - 1, dotSize + 2, dotSize + 2));

            // Màu trạng thái
            g2.setColor(isOnline ? UIUtils.ONLINE_GREEN : UIUtils.OFFLINE_GRAY);
            g2.fill(new Ellipse2D.Float(dotX, dotY, dotSize, dotSize));
        }

        g2.dispose();
    }
}

