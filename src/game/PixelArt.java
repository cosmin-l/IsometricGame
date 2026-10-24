package game;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hand-placed pixel art for item icons and projectiles. Letters map to colors per icon; see legend below. */
final class PixelArt {
    private PixelArt() {}

    // Legend: m/h/M = material (main/highlight/shadow), a = accent (rarity), A = accent highlight,
    // w/W = wood, g/G/d = gold, k = dark detail, b = belt, l/L = liquid, q = glass, p/P = parchment.

    private static final String[] SWORD = {
            "..............h.",
            ".............hm.",
            "............hmM.",
            "...........hmM..",
            "..........hmM...",
            ".........hmM....",
            "........hmM.....",
            ".......hmM......",
            "...a..hmM.......",
            "....a.mM........",
            ".....aa.........",
            "....wwaa........",
            "...ww...a.......",
            "..ww............",
            ".aw.............",
            ".aa.............",
    };
    private static final String[] AXE = {
            "................",
            "......hhm.......",
            ".....hmmmm......",
            "....hmmmmMww....",
            "....hmmmMwWw....",
            ".....mmMwW......",
            "......MwW.......",
            ".....wwW........",
            "....wwW.........",
            "...wwW..........",
            "..wwW...........",
            ".awW............",
            ".aa.............",
    };
    private static final String[] HAMMER = {
            "hmmmmmmM",
            "hmmmmmmM",
            "hmmmmmmM",
            "MMMMMMMM",
            "...wW...",
            "...wW...",
            "...wW...",
            "...wW...",
            "...wW...",
            "..aaaa..",
            "...wW...",
            "...wW...",
            "...aa...",
    };
    private static final String[] SHIELD = {
            "mmmmmaammmmm",
            "mhmmmaammmmM",
            "mhmmmaammmmM",
            "aaaaaaaaaaaa",
            "aaaaaaaaaaaa",
            "mhmmmaammmmM",
            "mhmmmaammmmM",
            ".mmmmaammmM.",
            ".mmmmaammmM.",
            "..mmmaammM..",
            "...mmaamM...",
            "....maaM....",
            ".....aa.....",
    };
    private static final String[] HELM = {
            ".....hhhh.....",
            "...hhmmmmmm...",
            "..hmmmmmmmmM..",
            ".hmmmmmmmmmmM.",
            ".hmmmmmmmmmmM.",
            ".aaaaaaaaaaaa.",
            ".mmkkkkkkkkmM.",
            ".mmmmmkkmmmmM.",
            ".mmm.mkkM.mmM.",
            ".mM...mM...MM.",
    };
    private static final String[] ARMOR = {
            "..mmm....mmm..",
            ".hmmmm..mmmmM.",
            "hmmmmmaammmmmM",
            "hmmmmmmmmmmmmM",
            "hmmmhmmmmmmmmM",
            ".MmmhmmmmmmmM.",
            "..mmhmmmmmmM..",
            "..mmhmmmmmmM..",
            "..bbbbgbbbbb..",
            "..mmhmmmmmmM..",
            "..mmmmmmmmmM..",
            "..MMMMMMMMMM..",
    };
    private static final String[] BOOTS = {
            "..hmmmM.......",
            "..hmmmM.......",
            "..hmmmM.......",
            "..aaaaa.......",
            "..hmmmM.......",
            "..hmmmM.......",
            "..hmmmmmmM....",
            ".hmmmmmmmmM...",
            ".kkkkkkkkkk...",
    };
    private static final String[] RING = {
            "....aa....",
            "...aAAa...",
            "....aa....",
            "...gggg...",
            "..gG..gg..",
            ".gG....gg.",
            ".gG....Gg.",
            ".gG....Gg.",
            "..gG..Gg..",
            "...gGGg...",
    };
    private static final String[] GOLD = {
            ".....gGGGg.....",
            "....gGGGGGg....",
            "....gGGGGGg....",
            "....dgggggd....",
            "..gGGGgdgGGGg..",
            ".gGGGGGggGGGGg.",
            ".gGGGGGggGGGGg.",
            ".dgggggddgggggd",
            "..ddddd..ddddd.",
    };
    private static final String[] POTION = {
            "....ww....",
            "....kk....",
            "....qq....",
            "....qq....",
            "...qllq...",
            "..qllllq..",
            ".qlhllllq.",
            ".qlhllllq.",
            ".qllllllq.",
            ".qllllllLq",
            "..qLLLLq..",
            "...qqqq...",
    };
    private static final String[] SCROLL = {
            ".PPPPPPPPPP.",
            "PppppppppppP",
            ".pppppppppp.",
            ".pkkkkkkkkp.",
            ".pppppppppp.",
            ".pkkkkkkkpp.",
            ".pppppppppp.",
            ".pkkkkkkkkp.",
            ".ppppaapppp.",
            ".pppaaaappp.",
            "PppppppppppP",
            ".PPPPPPPPPP.",
    };

    private static final String[][] FIREBALL = {{
            "...ooo...",
            "..oyyyo..",
            ".oyYYYyo.",
            "oyYWWWYyo",
            "oyYWWWYyo",
            "oyYWWWYyo",
            ".oyYYYyo.",
            "..oyyyo..",
            "...ooo...",
    }, {
            "....o....",
            "..ooyoo..",
            ".oyyYyyo.",
            "oyYWWWYyo",
            "yYWWWWWYy",
            "oyYWWWYyo",
            ".oyyYyyo.",
            "..ooyoo..",
            "....o....",
    }};
    private static final String[] FROST = {
            "..W..",
            ".cWc.",
            "WWWWW",
            ".cWc.",
            "..W..",
    };

    private static final Color OUTLINE = new Color(0x111D35);
    private static final Map<Character, Color> GLOW = Map.of(
            'W', new Color(255, 250, 220), 'Y', new Color(255, 220, 80), 'y', new Color(255, 140, 30),
            'o', new Color(200, 50, 20), 'c', new Color(140, 210, 255));

    private static final Map<String, BufferedImage> CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) { return size() > 1500; }
    };

    private static Color[] material(Item it) {
        int tier = Loot.tier(it);
        if (it.slot != Item.Slot.WEAPON && "Rags".equals(it.baseName)) {
            return new Color[]{new Color(120, 105, 85), new Color(150, 135, 110), new Color(85, 72, 58)};
        }
        boolean leather = it.slot != Item.Slot.WEAPON && tier <= 1;
        if (leather) return new Color[]{new Color(140, 95, 55), new Color(185, 135, 85), new Color(95, 62, 35)};
        if (tier <= 2) return new Color[]{new Color(150, 150, 160), new Color(200, 200, 210), new Color(95, 95, 105)};
        return new Color[]{new Color(190, 195, 210), new Color(245, 245, 255), new Color(120, 125, 140)};
    }

    private static String[] design(Item it) {
        return switch (it.slot) {
            case WEAPON -> it.baseName.contains("Axe") ? AXE : it.baseName.contains("Hammer") ? HAMMER : SWORD;
            case SHIELD -> SHIELD;
            case HELM -> HELM;
            case ARMOR -> ARMOR;
            case BOOTS -> BOOTS;
            case RING -> RING;
            case GOLD -> GOLD;
            case HEALTH_POTION, MANA_POTION -> POTION;
            case TOWN_PORTAL -> SCROLL;
        };
    }

    private static Map<Character, Color> palette(Item it) {
        Map<Character, Color> c = new HashMap<>();
        Color[] mat = material(it);
        c.put('m', mat[0]);
        c.put('h', mat[1]);
        c.put('M', mat[2]);
        Color accent = it.rarity == Item.Rarity.NORMAL ? new Color(160, 125, 70) : it.rarity.color;
        c.put('a', accent);
        c.put('A', Shade.mix(accent, Color.WHITE, 0.6));
        c.put('w', new Color(125, 85, 45));
        c.put('W', new Color(85, 55, 30));
        c.put('g', new Color(235, 185, 50));
        c.put('G', new Color(255, 235, 140));
        c.put('d', new Color(150, 105, 25));
        c.put('k', new Color(40, 35, 40));
        c.put('b', new Color(90, 60, 35));
        c.put('q', new Color(200, 215, 230));
        boolean mana = it.slot == Item.Slot.MANA_POTION;
        c.put('l', mana ? new Color(60, 110, 240) : new Color(220, 40, 50));
        c.put('L', mana ? new Color(30, 60, 160) : new Color(140, 20, 30));
        c.put('p', new Color(230, 210, 165));
        c.put('P', new Color(185, 160, 115));
        if (it.slot == Item.Slot.TOWN_PORTAL) {
            c.put('a', new Color(70, 120, 230));
            c.put('k', new Color(120, 90, 60));
        }
        return c;
    }

    /** Item icon centered at (cx, cy); 'size' is the space available in full-resolution pixels. */
    static void icon(Graphics2D g, Item it, double cx, double cy, double size, double light) {
        int lvl = (int) Math.round(Math.max(0, Math.min(1.5, light)) * 8);
        String key = it.slot.name() + '|' + it.baseName + '|' + it.rarity + '|' + lvl;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = PixelSprites.bake(design(it), palette(it), lvl / 8.0, OUTLINE);
            CACHE.put(key, img);
        }
        PixelSprites.blitCentered(g, img, cx, cy, size >= 64 ? 2 : 1);
    }

    private static BufferedImage arrow(int dir) {
        String key = "arrow" + dir;
        BufferedImage img = CACHE.get(key);
        if (img != null) return img;
        char[][] grid = new char[15][15];
        for (char[] row : grid) java.util.Arrays.fill(row, '.');
        double a = dir * Math.PI * 2 / 16, cx = Math.cos(a), cy = Math.sin(a);
        for (int t = -6; t <= 6; t++) {
            int x = 7 + (int) Math.round(cx * t), y = 7 + (int) Math.round(cy * t);
            grid[y][x] = t >= 5 ? 'm' : 'w';
        }
        for (int t = -6; t <= -5; t++) {
            for (int side = -1; side <= 1; side += 2) {
                int x = 7 + (int) Math.round(cx * t - cy * side), y = 7 + (int) Math.round(cy * t + cx * side);
                if (x >= 0 && y >= 0 && x < 15 && y < 15) grid[y][x] = 'f';
            }
        }
        String[] rows = new String[15];
        for (int r = 0; r < 15; r++) rows[r] = new String(grid[r]);
        img = PixelSprites.bake(rows, Map.of('w', new Color(140, 100, 60), 'm', new Color(210, 215, 225),
                'f', new Color(235, 235, 235)), 1, OUTLINE);
        CACHE.put(key, img);
        return img;
    }

    private static final int PORTAL_FRAMES = 8;
    private static final Map<Character, Color> PORTAL_COLORS = Map.of(
            'W', new Color(250, 250, 255), 'C', new Color(140, 220, 255), 'B', new Color(60, 130, 255),
            'D', new Color(30, 50, 170), 'R', new Color(150, 215, 255), 'r', new Color(80, 150, 255));

    /** One frame of the swirling portal oval: spiral bands that rotate as the frame advances. */
    private static String[] portalFrame(int frame) {
        int w = 17, h = 33;
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0, rx = w / 2.0, ry = h / 2.0;
        String[] rows = new String[h];
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < w; x++) {
                double nx = (x - cx) / rx, ny = (y - cy) / ry, d = Math.hypot(nx, ny);
                double ang = Math.atan2(ny, nx);
                char c;
                if (d > 1) {
                    c = '.';
                } else if (d > 0.84) {
                    int spot = (int) Math.floor((ang + Math.PI) / (Math.PI * 2) * 24);
                    c = (spot + frame * 3) % 8 == 0 ? 'W' : 'R';
                } else if (d < 0.16) {
                    c = 'W';
                } else {
                    double v = d * 2.2 + ang / (Math.PI * 2) * 2 - frame / (double) PORTAL_FRAMES;
                    int band = Math.floorMod((int) Math.floor(v * 3), 4);
                    c = switch (band) {
                        case 0 -> 'D';
                        case 1, 3 -> 'B';
                        default -> d < 0.45 ? 'W' : 'C';
                    };
                }
                sb.append(c);
            }
            rows[y] = sb.toString();
        }
        return rows;
    }

    /** A flat ring of glowing runes on the floor beneath the portal. */
    private static String[] portalBase(int frame) {
        int w = 27, h = 9;
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
        String[] rows = new String[h];
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < w; x++) {
                double nx = (x - cx) / (w / 2.0), ny = (y - cy) / (h / 2.0), d = Math.hypot(nx, ny);
                int spot = (int) Math.floor((Math.atan2(ny, nx) + Math.PI) / (Math.PI * 2) * 16);
                if (d <= 1 && d > 0.78) sb.append((spot + frame) % 4 == 0 ? 'R' : 'r');
                else if (d <= 0.45 && (x + y + frame) % 3 == 0) sb.append('r');
                else sb.append('.');
            }
            rows[y] = sb.toString();
        }
        return rows;
    }

    static void portal(Graphics2D g, double sx, double sy, double time, boolean hovered) {
        int frame = (int) (time * 10) % PORTAL_FRAMES;
        String baseKey = "portalBase" + frame, key = "portal" + frame + hovered;
        BufferedImage base = CACHE.get(baseKey), img = CACHE.get(key);
        if (base == null) {
            base = PixelSprites.bake(portalBase(frame), PORTAL_COLORS, 1, null);
            CACHE.put(baseKey, base);
        }
        if (img == null) {
            img = PixelSprites.bake(portalFrame(frame), PORTAL_COLORS, 1, hovered ? Color.WHITE : new Color(20, 30, 120));
            CACHE.put(key, img);
        }
        PixelSprites.blitCentered(g, base, sx, sy, 1);
        double bob = Math.sin(time * 3) * GamePanel.PX; // hovers gently above the runes
        PixelSprites.blitCentered(g, img, sx, sy - img.getHeight() * GamePanel.PX / 2.0 - 6 + bob, 1);
    }

    static void projectile(Graphics2D g, Projectile p, double sx, double sy, double time) {
        double y = sy - p.z;
        switch (p.kind) {
            case FIREBALL -> {
                Sprites.shadow(g, sx, sy, 14);
                int frame = (int) (time * 12) & 1;
                String key = "fire" + frame;
                BufferedImage img = CACHE.get(key);
                if (img == null) {
                    img = PixelSprites.bake(FIREBALL[frame], GLOW, 1, null);
                    CACHE.put(key, img);
                }
                PixelSprites.blitCentered(g, img, sx, y, 1);
            }
            case FROST -> {
                BufferedImage img = CACHE.get("frost");
                if (img == null) {
                    img = PixelSprites.bake(FROST, GLOW, 1, new Color(40, 90, 200));
                    CACHE.put("frost", img);
                }
                PixelSprites.blitCentered(g, img, sx, y, 1);
            }
            case ARROW -> {
                Sprites.shadow(g, sx, sy, 8);
                double dx = p.vx - p.vy, dy = (p.vx + p.vy) * 0.5; // screen-space direction
                int dir = Math.floorMod((int) Math.round(Math.atan2(dy, dx) / (Math.PI * 2 / 16)), 16);
                PixelSprites.blitCentered(g, arrow(dir), sx, y, 1);
            }
        }
    }
}
