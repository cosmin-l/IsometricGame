package game;

import java.awt.*;
import java.awt.geom.*;

/** Portals, shadows and blood, drawn with Java2D shapes (characters: PixelSprites; items and projectiles: PixelArt). */
final class Sprites {
    private Sprites() {}

    private static final Color SHADOW = new Color(0, 0, 0, 110);

    private static BasicStroke stroke(double w) {
        return new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    static void shadow(Graphics2D g, double sx, double sy, double w) {
        g.setColor(SHADOW);
        g.fill(new Ellipse2D.Double(sx - w / 2, sy - w / 4, w, w / 2));
    }

    static void portal(Graphics2D g, double sx, double sy, double time, boolean hovered) {
        double h = 66, w = 32, cy = sy - h / 2 - 4;
        g.setColor(new Color(60, 120, 255, 70));
        g.fill(new Ellipse2D.Double(sx - 26, sy - 10, 52, 20));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(sx, cy), 44f, new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(200, 230, 255, 200), new Color(60, 120, 255, 120), new Color(20, 40, 160, 0)}));
        g.fill(new Ellipse2D.Double(sx - 44, cy - 44, 88, 88));
        Ellipse2D oval = new Ellipse2D.Double(sx - w / 2, cy - h / 2, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(sx, cy), (float) (h / 2), new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(230, 245, 255), new Color(70, 130, 255), new Color(20, 40, 170)}));
        g.fill(oval);
        Shape oldClip = g.getClip();
        g.clip(oval);
        g.setStroke(stroke(2.5));
        for (int k = 0; k < 4; k++) {
            double r = 8 + k * 7;
            g.setColor(new Color(255, 255, 255, 150 - k * 25));
            g.draw(new Arc2D.Double(sx - r * 0.6, cy - r, r * 1.2, r * 2, time * 240 * (k % 2 == 0 ? 1 : -1) + k * 70, 110, Arc2D.OPEN));
        }
        g.setClip(oldClip);
        g.setStroke(stroke(hovered ? 3 : 2));
        g.setColor(hovered ? new Color(255, 255, 255) : new Color(150, 200, 255));
        g.draw(oval);
    }

    static void blood(Graphics2D g, Decal d, double sx, double sy, double light) {
        g.setColor(Shade.of(new Color(110, 0, 0, 170), light));
        double s = d.size;
        g.fill(new Ellipse2D.Double(sx - 5 * s, sy - 2 * s, 10 * s, 4 * s));
    }
}
