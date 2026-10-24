package game;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Snaps a frame to a small fixed palette, optionally with 4x4 ordered (Bayer) dithering. */
final class Retro {
    /** The 32 PICO-8 colors (base + extended) plus a few earthy tones the dungeon and village need. */
    static final int[] PALETTE = {
            0x000000, 0x1D2B53, 0x7E2553, 0x008751, 0xAB5236, 0x5F574F, 0xC2C3C7, 0xFFF1E8,
            0xFF004D, 0xFFA300, 0xFFEC27, 0x00E436, 0x29ADFF, 0x83769C, 0xFF77A8, 0xFFCCAA,
            0x291814, 0x111D35, 0x422136, 0x125359, 0x742F29, 0x49333B, 0xA28879, 0xF3EF7D,
            0xBE1250, 0xFF6C24, 0xA8E72E, 0x00B543, 0x065AB5, 0x754665, 0xFF6E59, 0xFF9D81,
            0x34522A, 0x4C6C30, 0x6E8C3C,           // grass
            0x7C7464, 0x3A3632,                     // stone
            0x961818, 0x5A0C0C,                     // blood
            0xCEBC96, 0x8C3E2D,                     // plaster, roof
    };
    private static final int SPREAD = 30;
    private static final int[] LUT = new int[1 << 15];
    private static final int[] BAYER = new int[16];

    static {
        int[] m = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        for (int i = 0; i < 16; i++) BAYER[i] = (int) Math.round((m[i] - 7.5) * SPREAD / 16.0);
        for (int i = 0; i < LUT.length; i++) {
            int r = ((i >> 10) & 31) << 3 | 4, g = ((i >> 5) & 31) << 3 | 4, b = (i & 31) << 3 | 4;
            int best = 0;
            long bestD = Long.MAX_VALUE;
            for (int p : PALETTE) {
                int dr = r - (p >> 16 & 255), dg = g - (p >> 8 & 255), db = b - (p & 255);
                long d = 2L * dr * dr + 4L * dg * dg + 3L * db * db;
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            LUT[i] = best;
        }
    }

    private Retro() {}

    static void quantize(BufferedImage img, boolean dither) {
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            int row = y * w, by = (y & 3) << 2;
            for (int x = 0; x < w; x++) {
                int c = px[row + x], r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                if (dither) {
                    int o = BAYER[by | (x & 3)];
                    r = clamp(r + o);
                    g = clamp(g + o);
                    b = clamp(b + o);
                }
                px[row + x] = LUT[(r >> 3) << 10 | (g >> 3) << 5 | (b >> 3)];
            }
        }
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    /** The palette color closest to an RGB value. */
    static int nearest(int rgb) {
        return LUT[(rgb >> 19 & 31) << 10 | (rgb >> 11 & 31) << 5 | (rgb >> 3 & 31)];
    }
}
