package game;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Color helpers; lit colors are quantized and cached so lighting doesn't allocate every frame. */
final class Shade {
    private static final Map<Long, Color> CACHE = new HashMap<>();

    private Shade() {}

    static Color of(Color c, double brightness) {
        // Few brightness steps: light falls off in visible bands, like old hardware palettes.
        int lvl = (int) Math.round(Math.max(0, Math.min(1.5, brightness)) * 8);
        long key = ((c.getRGB() & 0xffffffffL) << 8) | lvl;
        Color r = CACHE.get(key);
        if (r == null) {
            double f = lvl / 8.0;
            r = new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f), c.getAlpha());
            CACHE.put(key, r);
        }
        return r;
    }

    static Color mix(Color a, Color b, double t) {
        return new Color(clamp(a.getRed() + (b.getRed() - a.getRed()) * t),
                clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * t), a.getAlpha());
    }

    static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp(a * 255));
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }
}
