package client.ui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * UIUtils: Cung cấp màu sắc, font chữ và các hàm hỗ trợ vẽ Graphics2D hiện đại.
 */
public class UIUtils {
    // Bảng màu hiện đại theo phong cách thiết kế Dribbble / Figma
    public static final Color BG_MAIN = new Color(244, 245, 249);
    public static final Color BG_CARD = Color.WHITE;
    public static final Color BG_SIDEBAR = new Color(248, 249, 251);

    public static final Color PRIMARY_BLUE = new Color(29, 114, 254);
    public static final Color PRIMARY_HOVER = new Color(21, 94, 212);

    // Màu bong bóng chat
    public static final Color BUBBLE_MY_BG = new Color(214, 228, 255); // Soft blue pastel
    public static final Color BUBBLE_MY_TEXT = new Color(26, 46, 80);
    public static final Color BUBBLE_OTHER_BG = Color.WHITE;
    public static final Color BUBBLE_OTHER_TEXT = new Color(31, 41, 55);
    public static final Color BUBBLE_PRIVATE_BG = new Color(237, 233, 254); // Lavender pastel
    public static final Color BUBBLE_PRIVATE_TEXT = new Color(91, 33, 182);

    public static final Color BORDER_LIGHT = new Color(229, 231, 235);
    public static final Color TEXT_DARK = new Color(17, 24, 39);
    public static final Color TEXT_MUTED = new Color(156, 163, 175);
    public static final Color TEXT_SECONDARY = new Color(107, 114, 128);

    public static final Color ONLINE_GREEN = new Color(16, 185, 129);
    public static final Color OFFLINE_GRAY = new Color(156, 163, 175);
    public static final Color BADGE_RED = new Color(239, 68, 68);
    public static final Color GOLD_ADMIN = new Color(217, 119, 6);

    // Mảng các dải màu Gradient sang trọng cho Avatar
    public static final Color[][] AVATAR_GRADIENTS = {
            {new Color(255, 107, 107), new Color(255, 159, 67)},   // Sunset orange
            {new Color(72, 52, 212), new Color(104, 109, 224)},   // Deep purple
            {new Color(34, 166, 179), new Color(112, 161, 255)},  // Ocean cyan
            {new Color(235, 77, 75), new Color(240, 147, 43)},    // Warm red
            {new Color(106, 176, 76), new Color(186, 220, 88)},   // Emerald lime
            {new Color(224, 86, 253), new Color(104, 109, 224)},  // Neon pink-purple
            {new Color(30, 144, 255), new Color(0, 210, 211)}     // Sky blue
    };

    public static void enableAntialiasing(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    public static Color[] getAvatarGradient(String name) {
        if (name == null || name.isEmpty()) return AVATAR_GRADIENTS[0];
        int hash = Math.abs(name.hashCode());
        return AVATAR_GRADIENTS[hash % AVATAR_GRADIENTS.length];
    }

    public static void drawRoundedCard(Graphics2D g2, int x, int y, int width, int height, int radius, Color bg, Color border) {
        enableAntialiasing(g2);
        if (bg != null) {
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(x, y, width, height, radius, radius));
        }
        if (border != null) {
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
        }
    }
}

