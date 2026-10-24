package game;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Pixel-art drop shadows and blood (characters live in PixelSprites; items, projectiles and portals in PixelArt). */
final class Sprites {
    private Sprites() {}

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Map<Character, Color> BLOOD = Map.of('r', new Color(150, 22, 22), 'd', new Color(92, 12, 12));

    private static int lightLevel(double light) { return (int) Math.round(Math.max(0, Math.min(1.5, light)) * 8); }

    /** An oval of checkerboard black pixels: the classic see-through shadow. 'w' is the width in panel pixels. */
    static void shadow(Graphics2D g, double sx, double sy, double w) {
        int cw = Math.max(4, (int) Math.round(w / GamePanel.PX)) & ~1, ch = Math.max(2, cw / 2);
        BufferedImage img = CACHE.computeIfAbsent("s" + cw, k -> {
            BufferedImage im = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            double rx = cw / 2.0, ry = ch / 2.0;
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    double nx = (x + 0.5 - rx) / rx, ny = (y + 0.5 - ry) / ry;
                    if (nx * nx + ny * ny <= 1 && ((x + y) & 1) == 0) im.setRGB(x, y, 0xFF000000);
                }
            }
            return im;
        });
        PixelSprites.blitCentered(g, img, sx, sy, 1);
    }

    /** A ragged blood splat: dark center, red rim, a few stray droplets. Deterministic for a given seed. */
    private static String[] splat(int rx, int ry, long seed, int drops) {
        Random r = new Random(seed);
        double[] jitter = new double[12];
        for (int i = 0; i < jitter.length; i++) jitter[i] = r.nextDouble() * 0.4 - 0.12;
        int w = rx * 2 + 7, h = ry * 2 + 5;
        double cx = w / 2.0, cy = h / 2.0;
        char[][] grid = new char[h][w];
        for (char[] row : grid) Arrays.fill(row, '.');
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry, d = Math.hypot(nx, ny);
                int sector = (int) ((Math.atan2(ny, nx) + Math.PI) / (Math.PI * 2) * 12) % 12;
                if (d < 1 + jitter[sector]) grid[y][x] = d < 0.55 ? 'd' : 'r';
            }
        }
        for (int k = 0; k < drops; k++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 1.3 + r.nextDouble() * 0.5;
            int x = (int) Math.floor(cx + Math.cos(a) * rx * dist), y = (int) Math.floor(cy + Math.sin(a) * ry * dist);
            if (x >= 0 && y >= 0 && x < w && y < h) grid[y][x] = 'r';
        }
        String[] rows = new String[h];
        for (int y = 0; y < h; y++) rows[y] = new String(grid[y]);
        return rows;
    }

    private static void drawSplat(Graphics2D g, double sx, double sy, int rx, int ry, int variant, int drops, double light) {
        int lvl = lightLevel(light);
        String key = "b" + rx + 'x' + ry + '|' + variant + '|' + lvl;
        BufferedImage img = CACHE.computeIfAbsent(key,
                k -> PixelSprites.bake(splat(rx, ry, variant * 7919L + rx * 31L + ry, drops), BLOOD, lvl / 8.0, null));
        PixelSprites.blitCentered(g, img, sx, sy, 1);
    }

    static void blood(Graphics2D g, Decal d, double sx, double sy, double light) {
        int big = d.size >= 1.1 ? 1 : 0;
        drawSplat(g, sx, sy, 1 + big, 1, System.identityHashCode(d) & 3, 2 + big, light);
    }

    /** The larger pool left under a corpse. */
    static void pool(Graphics2D g, double sx, double sy, double scale, double light, int variant) {
        drawSplat(g, sx, sy, (int) Math.round(6 * scale), (int) Math.round(2.5 * scale), variant & 3, 4, light);
    }
}
