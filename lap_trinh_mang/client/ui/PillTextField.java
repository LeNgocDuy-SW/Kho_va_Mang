package client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * PillTextField: Ô nhập liệu bo tròn con nhộng hiện đại (Pill Search/Input).
 */
public class PillTextField extends JTextField {
    private final String placeholder;
    private final String iconText;
    private boolean isFocused = false;

    public PillTextField(String placeholder, String iconText) {
        this.placeholder = placeholder;
        this.iconText = iconText;
        setOpaque(false);
        setFont(new Font("Segoe UI", Font.PLAIN, 13));
        setForeground(UIUtils.TEXT_DARK);
        setCaretColor(UIUtils.PRIMARY_BLUE);

        int leftPadding = (iconText != null && !iconText.isEmpty()) ? 32 : 14;
        setBorder(new EmptyBorder(7, leftPadding, 7, 14));

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                isFocused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                isFocused = false;
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
        int arc = h;

        // Vẽ nền trắng bo cong
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

        // Vẽ viền (sáng xanh khi focus, xám nhạt khi thường)
        if (isFocused) {
            g2.setColor(UIUtils.PRIMARY_BLUE);
            g2.setStroke(new BasicStroke(1.5f));
        } else {
            g2.setColor(UIUtils.BORDER_LIGHT);
            g2.setStroke(new BasicStroke(1f));
        }
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));

        // Vẽ Icon nếu có
        if (iconText != null && !iconText.isEmpty()) {
            g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            g2.setColor(UIUtils.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            int iy = ((h - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(iconText, 12, iy);
        }

        // Vẽ Placeholder khi ô trống
        if (getText().isEmpty() && !isFocused && placeholder != null) {
            g2.setFont(getFont());
            g2.setColor(UIUtils.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            int leftPadding = (iconText != null && !iconText.isEmpty()) ? 32 : 14;
            int ty = ((h - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(placeholder, leftPadding, ty);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}

