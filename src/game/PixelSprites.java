package game;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Hand-placed pixel-art characters. Each design is a letter grid (one letter per pixel, keyed to a color);
 * the front arm and weapon are plotted pixel by pixel in 45-degree steps so one design covers idle, walk,
 * wind-up and strike. Frames get a 1-pixel outline, are snapped to the retro palette and cached.
 */
final class PixelSprites {
    private PixelSprites() {}

    enum Weapon { NONE, SWORD, AXE, MACE, CLEAVER, SPEAR, CLAW, BOW, STAFF }

    private static final int REST = 0, WINDUP = 1, STRIKE = 2;
    private static final int PAD = 14; // room around the body for weapons
    // E, SE, S, SW, W, NW, N, NE (screen space, facing right)
    private static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int E = 0, SE = 1, S = 2, NW = 5, NE = 7;

    private static final Color OUTLINE = new Color(0x111D35), HOVER = new Color(0xFF004D), NPC_HOVER = new Color(0xFFEC27);
    private static final Color STEEL = new Color(205, 210, 220), STEEL_HI = new Color(245, 245, 255);
    private static final Color WOOD = new Color(125, 85, 45), GOLD = new Color(235, 190, 60), BLOOD = new Color(160, 20, 20);
    private static final Color NAIL = new Color(230, 225, 190), STRING = new Color(210, 210, 210), GEM = new Color(60, 220, 120);
    private static final Color TRAIL = new Color(230, 235, 255);
    private static final Color CHAMPION_TINT = new Color(70, 110, 255), FROST_TINT = new Color(150, 210, 255);

    private record Design(String[] body, String[] legsStand, String[] legsWalk, Map<Character, Color> colors,
                          int shoulderX, int shoulderY, int armLen, int armThick, char arm, char hand, Weapon weapon) {
        int width() {
            int w = 0;
            for (String s : body) w = Math.max(w, s.length());
            for (String s : legsStand) w = Math.max(w, s.length());
            return w;
        }

        int height() { return body.length + legsStand.length; }
    }

    private static Map<Character, Color> colors(Object... kv) {
        Map<Character, Color> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], new Color((Integer) kv[i + 1]));
        return m;
    }

    // ------------------------------------------------------------------ designs (all facing right)

    private static final String[] LEGS_STAND = {
            ".....llllll.....",
            ".....llllll.....",
            ".....ll..ll.....",
            ".....ll..ll.....",
            ".....ll..ll.....",
            ".....ll..ll.....",
            ".....LL..LL.....",
            ".....LLL.LLL....",
    };
    private static final String[] LEGS_WALK = {
            ".....llllll.....",
            "....llllllll....",
            "....lll..lll....",
            "...lll....lll...",
            "...ll......ll...",
            "..lll......ll...",
            "..LL.......LLL..",
            ".LLL........LLL.",
    };

    private static final Design PLAYER = new Design(new String[]{
            "......hhhh......",
            ".....hhhhhh.....",
            ".....hhkkkk.....",
            ".....hkkkek.....",
            ".....hkkkkk.....",
            "......kkkK......",
            "....ccAaaaa.....",
            "...cccAaaaaa....",
            "...ccAAaaaaa....",
            "...ccAaaaaaa....",
            "...ccAaaaaa.....",
            "...ccbbbgbb.....",
            "...ccAaaaaa.....",
            "...cCAAaaaa.....",
            "....C.AAaa......",
    }, LEGS_STAND, LEGS_WALK, colors(
            'h', 0x5F3C23, 'k', 0xEBB991, 'K', 0xBE8769, 'e', 0x19141E, 'a', 0x6E553C, 'A', 0x503C2D,
            'c', 0xA51E23, 'C', 0x6E1419, 'b', 0x5A3C1E, 'g', 0xF0C850, 'l', 0x4B3C32, 'L', 0x37281E),
            10, 7, 4, 1, 'a', 'k', Weapon.SWORD);

    private static final String[] HELM = {
            ".....HHHHH......",
            "....HHHHHHH.....",
            "....HHHHHHHH....",
            ".....H....H.....",
    };
    private static final String[] SHIELD = {
            "", "", "", "", "", "", "",
            "..SSS...........",
            ".SSsSS..........",
            ".SsssS..........",
            ".SSsSS..........",
            ".SSSSS..........",
            "..SSS...........",
            "...S............",
    };

    private static final Design ZOMBIE = new Design(new String[]{
            "................",
            "................",
            ".......kkkk.....",
            "......kkkkkk....",
            "......kkkkek....",
            "......KkkkKk....",
            ".....rrKkkK.....",
            "....rrrrrrr.....",
            "...rrrrrrrrr....",
            "...Rrrrrrrrr....",
            "...Rrrrrrrr.....",
            "...RRrrrrrr.....",
            "....Rrrrrr......",
            "....RrRrRr......",
            ".....R.R.r......",
    }, LEGS_STAND, LEGS_WALK, colors(
            'k', 0x87A564, 'K', 0x5F7846, 'e', 0xE6E650, 'r', 0x645F48, 'R', 0x443E32, 'l', 0x6E8755, 'L', 0x465037),
            10, 8, 4, 1, 'K', 'k', Weapon.CLAW);

    private static final String[] BONE_LEGS_STAND = {
            "......w..w......",
            "......w..w......",
            "......W..W......",
            "......w..w......",
            "......w..w......",
            "......W..W......",
            "......w..w......",
            "......ww.ww.....",
    };
    private static final String[] BONE_LEGS_WALK = {
            "......w..w......",
            ".....w....w.....",
            ".....W....W.....",
            "....w......w....",
            "....w......w....",
            "...W........W...",
            "...w........w...",
            "..ww........ww..",
    };
    private static final Map<Character, Color> BONE_COLORS = colors(
            'w', 0xE8E2C8, 'W', 0xAAA591, 'd', 0x3C322D, 'e', 0xFF4628, 'q', 0x78502D, 'f', 0xDCDCDC);

    private static final Design SKELETON = new Design(new String[]{
            "................",
            "......wwww......",
            ".....wwwwww.....",
            ".....wwwewe.....",
            ".....wwwwww.....",
            "......wdwd......",
            ".......ww.......",
            ".....wwwwww.....",
            "......wdwdw.....",
            "......wwwww.....",
            "......wdwdw.....",
            ".......www......",
            ".......dw.......",
            "......wwww......",
            "......w..w......",
    }, BONE_LEGS_STAND, BONE_LEGS_WALK, BONE_COLORS, 10, 7, 4, 1, 'w', 'w', Weapon.SWORD);

    private static final Design ARCHER = new Design(new String[]{
            "................",
            "......wwww......",
            ".....wwwwww.....",
            ".....wwwewe.....",
            ".....wwwwww.....",
            "......wdwd......",
            "...f...ww.......",
            "...qqwwwwww.....",
            "...qq.wdwdw.....",
            "...qq.wwwww.....",
            "...qq.wdwdw.....",
            "...q...www......",
            ".......dw.......",
            "......wwww......",
            "......w..w......",
    }, BONE_LEGS_STAND, BONE_LEGS_WALK, BONE_COLORS, 10, 7, 3, 1, 'w', 'w', Weapon.BOW);

    private static final Design FALLEN = new Design(new String[]{
            "...n....n...",
            "...nn..nn...",
            "....rrrr....",
            "...rrrrer...",
            "...rrrrrr...",
            "....rRrr....",
            "...rrrrrr...",
            "..Rrrrrrr...",
            "..Rrrrrrr...",
            "...ttttt....",
            "...tttt.....",
    }, new String[]{
            "....r..r....",
            "...rr..rr...",
            "...rr..rr...",
            "...RR..RR...",
            "..RRR..RRR..",
    }, new String[]{
            "...rr..rr...",
            "..rr....rr..",
            "..rr....rr..",
            ".RR......RR.",
            "RRR......RRR",
    }, colors('n', 0xEBE1BE, 'r', 0xC33C30, 'R', 0x872620, 'e', 0xFFE63C, 't', 0x735232),
            8, 6, 3, 1, 'r', 'r', Weapon.SPEAR);

    private static final Design BUTCHER = new Design(new String[]{
            "........kkkkkk..........",
            ".......kkkkkkkk.........",
            ".......kkkkkkekk........",
            ".......kkkkkkkkk........",
            ".......Kkkkkmmkk........",
            "........Kkkkkkk.........",
            ".....kkkKKkkkkkkk.......",
            "....kkkkkkkkkkkkkk......",
            "...kkkkkkkppppppkkk.....",
            "...kkkkkkppppppppkk.....",
            "...Kkkkkkpppppppppk.....",
            "...Kkkkkppppppppppk.....",
            "...KkkkkppppBpppppk.....",
            "...KKkkkpppBBppppppk....",
            "...KKkkkppppBppBpppk....",
            "....KKkkpppppppBBppk....",
            "....KKkkppppppppppk.....",
            ".....KKkPpppppBppp......",
            ".....KKkPPpppppppp......",
            "......KkPPPpppppp.......",
            "........PPPPppp.........",
    }, new String[]{
            "........llllllll........",
            "........lll..lll........",
            "........lll..lll........",
            "........lll..lll........",
            "........lll..lll........",
            "........lll..lll........",
            "........LLL..LLL........",
            "........LLLL.LLLL.......",
    }, new String[]{
            "........llllllll........",
            ".......llll..llll.......",
            "......lll......lll......",
            "......lll......lll......",
            ".....lll........lll.....",
            ".....lll........lll.....",
            "....LLL.........LLLL....",
            "...LLLL..........LLLL...",
    }, colors('k', 0xE1A58C, 'K', 0xAA6E5F, 'e', 0x3C0A0A, 'm', 0x6E1919, 'p', 0xE4DAC6, 'P', 0xB4AA96,
            'B', 0xAA1414, 'l', 0x5F483C, 'L', 0x3C2D23),
            17, 7, 5, 2, 'k', 'k', Weapon.CLEAVER);

    private static final Design SMITH = new Design(new String[]{
            "................",
            "......kkkk......",
            ".....kkkkkk.....",
            ".....kkkkek.....",
            ".....khhhhh.....",
            "......hhhh......",
            "....sssshss.....",
            "...sssspppps....",
            "...ssspppppp....",
            "...Ksspppppp....",
            "...Kkspppppp....",
            "....Kpppppp.....",
            "....Ppppppp.....",
            "....PPppppp.....",
            ".....PPpppp.....",
    }, LEGS_STAND, LEGS_WALK, colors('k', 0xE1AF87, 'K', 0xAF7D5F, 'e', 0x1E1414, 'h', 0x784B28, 's', 0xA0967D,
            'p', 0x825A37, 'P', 0x5A3E28, 'l', 0x463C32, 'L', 0x32281E),
            10, 7, 4, 1, 'k', 'k', Weapon.MACE);

    private static final String[] ROBE = {
            "...OOOoooooo....",
            "...OOOoooooo....",
            "..OOOOooooooo...",
            "..OOOOooooooo...",
            "..OOOOOoooooo...",
            "..OOOOOooooooo..",
            "..gggggggggggg..",
    };
    private static final Design HEALER = new Design(new String[]{
            "......oooo......",
            ".....oooooo.....",
            ".....ookkkk.....",
            ".....okkkek.....",
            ".....okkkkk.....",
            ".....ooKkkk.....",
            "....ooooooo.....",
            "...Oooooooo.....",
            "...Oooooooo.....",
            "...Ooogoooo.....",
            "...Ooooggoo.....",
            "...Ooooooog.....",
            "...OOoooooo.....",
            "...OOoooooo.....",
            "...OOOooooo.....",
    }, ROBE, ROBE, colors('o', 0xD7D2BE, 'O', 0xA09B8C, 'g', 0x328250, 'k', 0xE6B991, 'K', 0xB98769, 'e', 0x281E1E),
            10, 7, 4, 1, 'o', 'k', Weapon.STAFF);

    // ------------------------------------------------------------------ frame cache

    private static final Map<String, BufferedImage> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) { return size() > 4000; }
    };

    private static final class Frame {
        final int w, h;
        final int[] px;

        Frame(int w, int h) {
            this.w = w;
            this.h = h;
            px = new int[w * h];
        }

        void set(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = c;
        }

        boolean solid(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h && px[y * w + x] != 0; }
    }

    private static int lightLevel(double light) { return (int) Math.round(Math.max(0, Math.min(1.5, light)) * 8); }

    private static ToIntFunction<Color> shader(int lvl, Color tint, boolean flash) {
        return c -> {
            if (tint != null) c = Shade.mix(c, tint, 0.35);
            c = Shade.of(c, lvl / 8.0);
            if (flash) c = Shade.mix(c, Color.WHITE, 0.7);
            return Retro.nearest(c.getRGB()) | 0xFF000000;
        };
    }

    private static void paint(Frame f, String[] rows, int y0, Map<Character, Color> base, Map<Character, Color> over,
                              ToIntFunction<Color> sh) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int x = 0; x < row.length(); x++) {
                char ch = row.charAt(x);
                if (ch == '.') continue;
                Color c = over != null && over.containsKey(ch) ? over.get(ch) : base.get(ch);
                if (c != null) f.set(PAD + x, PAD + y0 + r, sh.applyAsInt(c));
            }
        }
    }

    private static BufferedImage compose(Design d, Map<Character, Color> over, Weapon weapon, boolean helm,
                                         boolean shield, boolean walk, int pose, int lvl, Color tint, boolean flash,
                                         Color outline) {
        Frame f = new Frame(d.width() + PAD * 2, d.height() + PAD * 2);
        ToIntFunction<Color> sh = shader(lvl, tint, flash);
        paint(f, d.body(), 0, d.colors(), over, sh);
        paint(f, walk ? d.legsWalk() : d.legsStand(), d.body().length, d.colors(), over, sh);
        if (shield) paint(f, SHIELD, 0, d.colors(), over, sh);
        if (helm) paint(f, HELM, 0, d.colors(), over, sh);
        Color armColor = over != null && over.containsKey(d.arm()) ? over.get(d.arm()) : d.colors().get(d.arm());
        int[] hand = arm(f, d, weapon, pose, sh.applyAsInt(armColor), sh.applyAsInt(d.colors().get(d.hand())));
        weapon(f, weapon, pose, hand, sh);

        outline(f, outline);
        boolean melee = weapon == Weapon.SWORD || weapon == Weapon.AXE || weapon == Weapon.MACE || weapon == Weapon.CLEAVER;
        if (pose == STRIKE && melee) {
            int sx = PAD + d.shoulderX(), sy = PAD + d.shoulderY(), r = d.armLen() + 7;
            int trail = Retro.nearest(TRAIL.getRGB()) | 0xFF000000;
            for (int deg = -100; deg <= 35; deg += 9) {
                double a = Math.toRadians(deg);
                int x = sx + (int) Math.round(Math.cos(a) * r), y = sy + (int) Math.round(Math.sin(a) * r);
                if (!f.solid(x, y)) f.set(x, y, trail);
            }
        }
        return toImage(f);
    }

    /** Rings every opaque shape with a 1-pixel border. */
    private static void outline(Frame f, Color outline) {
        int ol = Retro.nearest(outline.getRGB()) | 0xFF000000;
        int[] copy = f.px.clone();
        for (int y = 0; y < f.h; y++) {
            for (int x = 0; x < f.w; x++) {
                if (copy[y * f.w + x] != 0) continue;
                boolean edge = (x > 0 && copy[y * f.w + x - 1] != 0) || (x < f.w - 1 && copy[y * f.w + x + 1] != 0)
                        || (y > 0 && copy[(y - 1) * f.w + x] != 0) || (y < f.h - 1 && copy[(y + 1) * f.w + x] != 0);
                if (edge) f.px[y * f.w + x] = ol;
            }
        }
    }

    private static BufferedImage toImage(Frame f) {
        BufferedImage img = new BufferedImage(f.w, f.h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, f.w, f.h, f.px, 0, f.w);
        return img;
    }

    /** Bakes a letter grid into a shaded, palette-snapped image, with an optional 1-pixel outline. */
    static BufferedImage bake(String[] rows, Map<Character, Color> colors, double light, Color outline) {
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        Frame f = new Frame(w + 2, rows.length + 2);
        ToIntFunction<Color> sh = shader(lightLevel(light), null, false);
        for (int r = 0; r < rows.length; r++) {
            for (int x = 0; x < rows[r].length(); x++) {
                Color c = colors.get(rows[r].charAt(x));
                if (c != null) f.set(x + 1, r + 1, sh.applyAsInt(c));
            }
        }
        if (outline != null) outline(f, outline);
        return toImage(f);
    }

    /** Draws an image centered on (cx, cy), in the panel's full-resolution coordinates, at an integer zoom. */
    static void blitCentered(Graphics2D g, BufferedImage img, double cx, double cy, int zoom) {
        int s = GamePanel.PX * zoom, w = img.getWidth() * s, h = img.getHeight() * s;
        int x = (int) Math.round((cx - w / 2.0) / GamePanel.PX) * GamePanel.PX;
        int y = (int) Math.round((cy - h / 2.0) / GamePanel.PX) * GamePanel.PX;
        g.drawImage(img, x, y, w, h, null);
    }

    private static int[] step(int[] p, int dir, int n) {
        return new int[]{p[0] + DIRS[dir][0] * n, p[1] + DIRS[dir][1] * n};
    }

    /** Plots the front arm; returns the hand position. */
    private static int[] arm(Frame f, Design d, Weapon w, int pose, int armRgb, int handRgb) {
        int dir;
        int len = d.armLen();
        switch (w) {
            case SPEAR -> {
                dir = pose == WINDUP ? S : pose == STRIKE ? E : SE;
                if (pose == STRIKE) len += 2;
            }
            case CLAW -> dir = pose == WINDUP ? NE : pose == STRIKE ? SE : E;
            case BOW -> dir = E;
            case STAFF -> dir = E;
            case NONE -> dir = pose == STRIKE ? E : pose == WINDUP ? NE : S;
            default -> dir = pose == WINDUP ? NE : pose == STRIKE ? E : SE;
        }
        int[] p = {PAD + d.shoulderX(), PAD + d.shoulderY()};
        for (int i = 1; i <= len; i++) {
            p = step(p, dir, 1);
            int c = i == len ? handRgb : armRgb;
            f.set(p[0], p[1], c);
            if (d.armThick() > 1) f.set(p[0], p[1] + 1, c);
        }
        return new int[]{p[0], p[1], dir};
    }

    private static void line(Frame f, int[] from, int dir, int a, int b, int rgb) {
        for (int i = a; i <= b; i++) {
            int[] p = step(from, dir, i);
            f.set(p[0], p[1], rgb);
        }
    }

    private static void weapon(Frame f, Weapon w, int pose, int[] hand, ToIntFunction<Color> sh) {
        int steel = sh.applyAsInt(STEEL), hi = sh.applyAsInt(STEEL_HI), wood = sh.applyAsInt(WOOD);
        int bladeDir = switch (pose) {
            case WINDUP -> NW;
            case STRIKE -> SE;
            default -> NE;
        };
        int[] dv = DIRS[bladeDir];
        int[] perp = {-dv[1], dv[0]};
        switch (w) {
            case SWORD -> {
                int[] g = step(hand, bladeDir, 1);
                f.set(g[0] + perp[0], g[1] + perp[1], sh.applyAsInt(GOLD));
                f.set(g[0] - perp[0], g[1] - perp[1], sh.applyAsInt(GOLD));
                line(f, hand, bladeDir, 2, 7, steel);
                int[] tip = step(hand, bladeDir, 8);
                f.set(tip[0], tip[1], hi);
            }
            case AXE -> {
                line(f, hand, bladeDir, 1, 7, wood);
                for (int k = 0; k < 3; k++) {
                    int[] p = step(hand, bladeDir, 7 - k);
                    for (int m = 1; m <= 2; m++) f.set(p[0] + perp[0] * m, p[1] + perp[1] * m, k == 1 && m == 2 ? hi : steel);
                }
            }
            case MACE -> {
                line(f, hand, bladeDir, 1, 5, wood);
                int[] c = step(hand, bladeDir, 7);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++) f.set(c[0] + dx, c[1] + dy, dx == 0 && dy == 0 ? hi : steel);
            }
            case CLEAVER -> {
                line(f, hand, bladeDir, 1, 2, wood);
                for (int k = 3; k <= 8; k++) {
                    int[] p = step(hand, bladeDir, k);
                    for (int m = 0; m <= 2; m++) f.set(p[0] + perp[0] * m, p[1] + perp[1] * m, steel);
                }
                int[] b = step(hand, bladeDir, 6);
                f.set(b[0] + perp[0], b[1] + perp[1], sh.applyAsInt(BLOOD));
            }
            case SPEAR -> {
                int back = pose == WINDUP ? 5 : 3;
                line(f, hand, E, -back, 7, wood);
                f.set(hand[0] + 8, hand[1], steel);
                f.set(hand[0] + 9, hand[1], hi);
                f.set(hand[0] + 7, hand[1] - 1, steel);
                f.set(hand[0] + 7, hand[1] + 1, steel);
            }
            case CLAW -> {
                int[] dir = DIRS[hand[2]];
                f.set(hand[0] + dir[0], hand[1] + dir[1], sh.applyAsInt(NAIL));
                f.set(hand[0] + dir[0] - dir[1], hand[1] + dir[1] + dir[0], sh.applyAsInt(NAIL));
            }
            case BOW -> {
                int x = hand[0], y = hand[1];
                f.set(x + 1, y - 4, wood);
                f.set(x + 1, y + 4, wood);
                for (int k = -3; k <= 3; k++) f.set(x + 2, y + k, wood);
                int str = sh.applyAsInt(STRING);
                if (pose == WINDUP) {
                    int[][] s = {{1, -3}, {0, -2}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 2}, {1, 3}};
                    for (int[] o : s) f.set(x + o[0], y + o[1], str);
                    for (int k = 0; k <= 6; k++) f.set(x + k, y, wood);
                    f.set(x + 7, y, hi);
                } else {
                    for (int k = -3; k <= 3; k++) f.set(x + 1, y + k, str);
                }
            }
            case STAFF -> {
                for (int k = -9; k <= 8; k++) f.set(hand[0], hand[1] + k, wood);
                f.set(hand[0], hand[1] - 10, sh.applyAsInt(GEM));
                f.set(hand[0] - 1, hand[1] - 9, sh.applyAsInt(GEM));
                f.set(hand[0] + 1, hand[1] - 9, sh.applyAsInt(GEM));
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ drawing

    private static int pose(Creature c) {
        if (c.swing <= 0) return REST;
        return 1 - c.swing / c.swingTime < 0.45 ? WINDUP : STRIKE;
    }

    private static boolean walking(Creature c) { return c.moving && ((int) (c.animTime * 7) & 1) == 0; }

    private static boolean facingLeft(Creature c) {
        double fx = Math.cos(c.facing), fy = Math.sin(c.facing);
        return fx - fy < 0; // screen-space x of the facing direction
    }

    /** Draws a frame with its feet at (sx, sy), in the panel's full-resolution coordinates. */
    private static void blit(Graphics2D g, BufferedImage img, double sx, double sy, int ax, int ay, boolean flip) {
        int s = GamePanel.PX;
        int x = (int) Math.round(sx / s) * s, y = (int) Math.round(sy / s) * s;
        int w = img.getWidth() * s, h = img.getHeight() * s, top = y - ay * s, left = x - ax * s;
        if (flip) g.drawImage(img, x + ax * s, top, x + ax * s - w, top + h, 0, 0, img.getWidth(), img.getHeight(), null);
        else g.drawImage(img, left, top, left + w, top + h, 0, 0, img.getWidth(), img.getHeight(), null);
    }

    private static void draw(Graphics2D g, String id, Design d, Map<Character, Color> over, Weapon weapon, boolean helm,
                             boolean shield, Creature c, double sx, double sy, double light, Color tint, Color outline) {
        boolean walk = walking(c), flash = c.hitFlash > 0;
        int pose = pose(c), lvl = lightLevel(light);
        String key = id + '|' + walk + pose + weapon.ordinal() + helm + shield + '|' + lvl + flash
                + (tint == null ? 0 : tint.getRGB()) + '|' + outline.getRGB();
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = compose(d, over, weapon, helm, shield, walk, pose, lvl, tint, flash, outline);
            CACHE.put(key, img);
        }
        Sprites.shadow(g, sx, sy, d.width() * GamePanel.PX * 0.9);
        blit(g, img, sx, sy, PAD + d.width() / 2, PAD + d.height(), facingLeft(c));
    }

    static void player(Graphics2D g, Player p, double sx, double sy, double light) {
        Item armor = p.equipment.get(Item.Slot.ARMOR), weapon = p.equipment.get(Item.Slot.WEAPON);
        Item helm = p.equipment.get(Item.Slot.HELM), shield = p.equipment.get(Item.Slot.SHIELD);
        Map<Character, Color> over = new HashMap<>();
        Color steel = new Color(150, 155, 170);
        if (armor != null && !armor.baseName.equals("Rags")) {
            Color a = Shade.mix(steel, armor.rarity.color, 0.3);
            over.put('a', a);
            over.put('A', a.darker());
        }
        if (helm != null) over.put('H', Shade.mix(steel, helm.rarity.color, 0.3));
        if (shield != null) {
            over.put('S', Shade.mix(new Color(125, 90, 60), shield.rarity.color, 0.3));
            over.put('s', shield.rarity.color);
        }
        Weapon w = Weapon.NONE;
        if (weapon != null) {
            String n = weapon.baseName;
            w = n.contains("Axe") ? Weapon.AXE : n.contains("Hammer") ? Weapon.MACE : Weapon.SWORD;
        }
        String id = "P" + (armor == null ? 0 : over.getOrDefault('a', Color.BLACK).getRGB())
                + (helm == null ? "" : over.get('H').getRGB()) + (shield == null ? "" : over.get('S').getRGB());
        draw(g, id, PLAYER, over, w, helm != null, shield != null, p, sx, sy, light, null, OUTLINE);
    }

    private static Design design(Monster.Type t) {
        return switch (t) {
            case ZOMBIE -> ZOMBIE;
            case FALLEN -> FALLEN;
            case SKELETON -> SKELETON;
            case ARCHER -> ARCHER;
            case BUTCHER -> BUTCHER;
        };
    }

    static void monster(Graphics2D g, Monster m, double sx, double sy, double light, boolean hovered) {
        Design d = design(m.type);
        Color tint = m.slowTimer > 0 ? FROST_TINT : m.champion ? CHAMPION_TINT : null;
        draw(g, "M" + m.type.ordinal(), d, null, d.weapon(), false, false, m, sx, sy, Math.max(light, 0.15),
                tint, hovered ? HOVER : OUTLINE);
    }

    static void npc(Graphics2D g, Npc n, double sx, double sy, double light, boolean hovered) {
        Design d = n.role == Npc.Role.SMITH ? SMITH : HEALER;
        draw(g, "N" + n.role.ordinal(), d, null, d.weapon(), false, false, n, sx, sy, light, null,
                hovered ? NPC_HOVER : OUTLINE);
    }

    /** Sprite size in the panel's full-resolution coordinates: {width, height}. */
    static int[] size(Monster m) {
        Design d = design(m.type);
        return new int[]{d.width() * GamePanel.PX, d.height() * GamePanel.PX};
    }

    /** A dead monster: its own idle frame, knocked onto its side, over a pool of blood. */
    static void corpse(Graphics2D g, Decal dc, double sx, double sy, double light) {
        Sprites.pool(g, sx, sy, dc.size, light, System.identityHashCode(dc));
        if (dc.type == null) return;
        Design d = design(dc.type);
        int lvl = lightLevel(light * 0.7);
        boolean left = dc.rot < 0;
        String key = "C" + dc.type.ordinal() + '|' + lvl + left;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            BufferedImage up = compose(d, null, d.weapon(), false, false, false, REST, lvl, null, false, OUTLINE);
            int w = up.getWidth(), h = up.getHeight();
            img = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int rgb = up.getRGB(x, y);
                    if (left) img.setRGB(y, w - 1 - x, rgb);
                    else img.setRGB(h - 1 - y, x, rgb);
                }
            CACHE.put(key, img);
        }
        blit(g, img, sx, sy, img.getWidth() / 2, img.getHeight() / 2 + 3, false);
    }
}
