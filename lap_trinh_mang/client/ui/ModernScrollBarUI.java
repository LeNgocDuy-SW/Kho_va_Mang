package client.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * ModernScrollBarUI: Thanh cuộn tối giản hiện đại (Minimalist Smooth ScrollBar).
 */
public class ModernScrollBarUI extends BasicScrollBarUI {
    private final int THUMB_SIZE = 6;

    @Override
    protected void configureScrollBarColors() {
        this.thumbColor = new Color(203, 213, 225);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private JButton createZeroButton() {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(0, 0));
        btn.setMinimumSize(new Dimension(0, 0));
        btn.setMaximumSize(new Dimension(0, 0));
        return btn;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        // Track trong suốt
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        UIUtils.enableAntialiasing(g2);

        int arc = THUMB_SIZE;
        int x = thumbBounds.x + (thumbBounds.width - THUMB_SIZE) / 2;
        int y = thumbBounds.y;
        int w = THUMB_SIZE;
        int h = thumbBounds.height;

        if (isThumbRollover()) {
            g2.setColor(new Color(148, 163, 184));
        } else {
            g2.setColor(new Color(203, 213, 225));
        }

        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.dispose();
    }
}

