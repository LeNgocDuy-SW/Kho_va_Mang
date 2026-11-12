package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * PillButton: Nút bấm bo tròn con nhộng hoặc tròn icon theo phong cách hiện đại.
 */
public class PillButton extends JButton {
    public enum Style {
        PRIMARY,      // Xanh dương nổi bật
        CIRCULAR_ICON,// Tròn hoàn toàn cho icon (+)
        SECONDARY,    // Xám trắng nhẹ
        DANGER        // Đỏ cảnh báo (Kick, Ban)
    }

    private final Style style;
    private boolean isHover = false;
    private boolean isPressed = false;

    public PillButton(String text, Style style) {
        super(text);
        this.style = style;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setFont(new Font("Segoe UI", Font.BOLD, 13));

        if (style == Style.PRIMARY) {
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(80, 36));
        } else if (style == Style.CIRCULAR_ICON) {
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(36, 36));
            setFont(new Font("Segoe UI", Font.BOLD, 16));
        } else if (style == Style.DANGER) {
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(80, 32));
        } else {
            setForeground(UIUtils.TEXT_DARK);
            setPreferredSize(new Dimension(80, 32));
        }

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
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        Color bg;
        if (style == Style.PRIMARY || style == Style.CIRCULAR_ICON) {
            if (isPressed) bg = UIUtils.PRIMARY_HOVER.darker();
            else if (isHover) bg = UIUtils.PRIMARY_HOVER;
            else bg = UIUtils.PRIMARY_BLUE;
        } else if (style == Style.DANGER) {
            if (isPressed) bg = UIUtils.BADGE_RED.darker();
            else if (isHover) bg = new Color(220, 38, 38);
            else bg = UIUtils.BADGE_RED;
        } else {
            if (isPressed) bg = new Color(229, 231, 235);
            else if (isHover) bg = new Color(243, 244, 246);
            else bg = Color.WHITE;
        }

        g2.setColor(bg);

        if (style == Style.CIRCULAR_ICON) {
            int d = Math.min(w, h);
            g2.fill(new Ellipse2D.Float((w - d) / 2f, (h - d) / 2f, d, d));
        } else {
            int arc = h;
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            if (style == Style.SECONDARY) {
                g2.setColor(UIUtils.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));
            }
        }

        // Vẽ nội dung text
        FontMetrics fm = g2.getFontMetrics(getFont());
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = ((h - fm.getHeight()) / 2) + fm.getAscent();

        g2.setColor(getForeground());
        g2.setFont(getFont());
        g2.drawString(getText(), tx, ty);

        g2.dispose();
    }
}

