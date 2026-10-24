package game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A map: either a procedurally generated dungeon level or the town, plus lighting state. */
public class Dungeon {
    public static final int WALL = 0, FLOOR = 1, STAIRS = 2, STAIRS_UP = 3, ROAD = 4, TREE = 5, HOUSE = 6;

    public final int w, h;
    public final boolean town;
    public final int[][] tiles, variant, torchFace, doorFace;
    public final boolean[][] explored;
    public final float[][] light;
    public final List<int[]> rooms = new ArrayList<>();   // x, y, w, h
    public final List<int[]> torches = new ArrayList<>(); // wall x, wall y, face (0 = faces +y, 1 = faces +x)
    public int startX, startY, stairsX, stairsY;
    public int[] stairsRoom;
    // town landmarks
    public int townPortalX, townPortalY, smithX, smithY, healerX, healerY, entranceX, entranceY;

    public Dungeon(int w, int h, Random rnd) {
        this(w, h, rnd, false);
        generate(rnd);
    }

    private Dungeon(int w, int h, Random rnd, boolean town) {
        this.w = w;
        this.h = h;
        this.town = town;
        tiles = new int[w][h];
        variant = new int[w][h];
        torchFace = new int[w][h];
        doorFace = new int[w][h];
        explored = new boolean[w][h];
        light = new float[w][h];
        for (int i = 0; i < w; i++)
            for (int j = 0; j < h; j++) variant[i][j] = rnd.nextInt(10000);
    }

    public static Dungeon town(Random rnd) {
        Dungeon d = new Dungeon(48, 48, rnd, true);
        d.buildTown(rnd);
        return d;
    }

    // ------------------------------------------------------------------ town

    private void buildTown(Random rnd) {
        for (int i = 0; i < w; i++)
            for (int j = 0; j < h; j++) {
                tiles[i][j] = (i == 0 || j == 0 || i == w - 1 || j == h - 1) ? WALL : FLOOR;
                explored[i][j] = true;
            }
        fill(19, 19, 10, 10, ROAD);      // plaza
        fill(23, 6, 2, 37, ROAD);        // north-south road
        fill(6, 23, 37, 2, ROAD);        // east-west road
        fill(11, 15, 13, 2, ROAD);       // path to the smithy
        fill(25, 15, 11, 2, ROAD);       // path to the healer

        house(9, 9, 7, 5, 12);           // smithy
        house(31, 9, 7, 5, 34);          // healer's cottage
        house(8, 30, 6, 5, 10);
        house(33, 31, 6, 5, 35);
        house(30, 38, 5, 4, 32);
        house(9, 39, 6, 4, 11);
        smithX = 12;
        smithY = 15;
        healerX = 34;
        healerY = 15;

        entranceX = 24;
        entranceY = 42;
        tiles[entranceX][entranceY] = STAIRS;
        tiles[entranceX - 1][entranceY] = WALL;
        tiles[entranceX + 1][entranceY] = WALL;
        tiles[entranceX][entranceY + 1] = WALL;
        tiles[entranceX - 1][entranceY + 1] = WALL;
        tiles[entranceX + 1][entranceY + 1] = WALL;

        startX = 24;
        startY = 25;
        townPortalX = 27;
        townPortalY = 21;

        for (int k = 0; k < 160; k++) {
            int x = 2 + rnd.nextInt(w - 4), y = 2 + rnd.nextInt(h - 4);
            boolean ok = true;
            for (int dx = -1; dx <= 1 && ok; dx++)
                for (int dy = -1; dy <= 1 && ok; dy++) if (tiles[x + dx][y + dy] != FLOOR) ok = false;
            if (Math.hypot(x - smithX, y - smithY) < 3 || Math.hypot(x - healerX, y - healerY) < 3) ok = false;
            if (ok) tiles[x][y] = TREE;
        }
    }

    private void fill(int x, int y, int fw, int fh, int t) {
        for (int i = x; i < x + fw; i++)
            for (int j = y; j < y + fh; j++) tiles[i][j] = t;
    }

    private void house(int x, int y, int hw, int hh, int doorX) {
        fill(x, y, hw, hh, HOUSE);
        doorFace[doorX][y + hh - 1] = 1;
    }

    // ------------------------------------------------------------------ dungeon

    private void generate(Random rnd) {
        for (int attempt = 0; attempt < 400 && rooms.size() < 18; attempt++) {
            int rw = 4 + rnd.nextInt(7), rh = 4 + rnd.nextInt(7);
            int rx = 2 + rnd.nextInt(w - rw - 4), ry = 2 + rnd.nextInt(h - rh - 4);
            boolean ok = true;
            for (int[] r : rooms) {
                if (rx - 2 < r[0] + r[2] && rx + rw + 2 > r[0] && ry - 2 < r[1] + r[3] && ry + rh + 2 > r[1]) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            rooms.add(new int[]{rx, ry, rw, rh});
            fill(rx, ry, rw, rh, FLOOR);
        }

        // Connect every room to its nearest already-connected room, then add a few loops.
        List<int[]> connected = new ArrayList<>();
        connected.add(rooms.get(0));
        for (int k = 1; k < rooms.size(); k++) {
            int[] r = rooms.get(k), best = null;
            double bd = Double.MAX_VALUE;
            for (int[] c : connected) {
                double d = Math.hypot(cx(r) - cx(c), cy(r) - cy(c));
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
            corridor(cx(r), cy(r), cx(best), cy(best), rnd);
            connected.add(r);
        }
        for (int k = 0; k < 3 && rooms.size() > 3; k++) {
            int[] a = rooms.get(rnd.nextInt(rooms.size())), b = rooms.get(rnd.nextInt(rooms.size()));
            if (a != b) corridor(cx(a), cy(a), cx(b), cy(b), rnd);
        }

        int[] first = rooms.get(0);
        startX = cx(first);
        startY = cy(first);
        tiles[startX][startY] = STAIRS_UP;
        double far = -1;
        for (int[] r : rooms) {
            double d = Math.hypot(cx(r) - startX, cy(r) - startY);
            if (d > far) {
                far = d;
                stairsRoom = r;
            }
        }
        stairsX = cx(stairsRoom);
        stairsY = cy(stairsRoom);
        tiles[stairsX][stairsY] = STAIRS;

        for (int[] r : rooms) {
            if (rnd.nextDouble() < 0.75) addTorch(r[0] + rnd.nextInt(r[2]), r[1] - 1, 0);
            if (rnd.nextDouble() < 0.5) addTorch(r[0] - 1, r[1] + rnd.nextInt(r[3]), 1);
        }
    }

    private void addTorch(int x, int y, int face) {
        int fx = face == 1 ? x + 1 : x, fy = face == 0 ? y + 1 : y;
        if (tiles[x][y] != WALL || tiles[fx][fy] == WALL || torchFace[x][y] != 0) return;
        torchFace[x][y] = face + 1;
        torches.add(new int[]{x, y, face});
    }

    private static int cx(int[] r) { return r[0] + r[2] / 2; }

    private static int cy(int[] r) { return r[1] + r[3] / 2; }

    private void corridor(int x0, int y0, int x1, int y1, Random rnd) {
        boolean wide = rnd.nextDouble() < 0.4;
        if (rnd.nextBoolean()) {
            hLine(x0, x1, y0, wide);
            vLine(y0, y1, x1, wide);
        } else {
            vLine(y0, y1, x0, wide);
            hLine(x0, x1, y1, wide);
        }
    }

    private void hLine(int x0, int x1, int y, boolean wide) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            carve(x, y);
            if (wide) carve(x, y + 1);
        }
    }

    private void vLine(int y0, int y1, int x, boolean wide) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            carve(x, y);
            if (wide) carve(x + 1, y);
        }
    }

    private void carve(int x, int y) {
        if (tiles[x][y] == WALL) tiles[x][y] = FLOOR;
    }

    // ------------------------------------------------------------------ queries

    /** Walls and houses: drawn as raised blocks. */
    public boolean isBlock(int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h || tiles[x][y] == WALL || tiles[x][y] == HOUSE;
    }

    public boolean walkable(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        int t = tiles[x][y];
        return t != WALL && t != TREE && t != HOUSE;
    }

    public boolean walkable(double x, double y) {
        return walkable((int) Math.floor(x), (int) Math.floor(y));
    }

    /** True if nothing blocks the line; the target tile itself may be a wall (so walls can be seen). */
    public boolean lineOfSight(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        int steps = (int) (Math.hypot(dx, dy) * 3) + 1;
        int ex = (int) Math.floor(x1), ey = (int) Math.floor(y1);
        for (int s = 1; s < steps; s++) {
            double t = s / (double) steps;
            int tx = (int) Math.floor(x0 + dx * t), ty = (int) Math.floor(y0 + dy * t);
            if (tx == ex && ty == ey) return true;
            if (!walkable(tx, ty)) return false;
        }
        return true;
    }

    public void clearLight() {
        float v = town ? 0.95f : 0f;
        for (float[] col : light) java.util.Arrays.fill(col, v);
    }

    public void addLight(double lx, double ly, double radius, float intensity, boolean reveal) {
        int r = (int) Math.ceil(radius);
        int ci = (int) Math.floor(lx), cj = (int) Math.floor(ly);
        for (int i = ci - r; i <= ci + r; i++) {
            for (int j = cj - r; j <= cj + r; j++) {
                if (i < 0 || j < 0 || i >= w || j >= h) continue;
                double d = Math.hypot(i + 0.5 - lx, j + 0.5 - ly);
                if (d > radius || !lineOfSight(lx, ly, i + 0.5, j + 0.5)) continue;
                float b = (float) (intensity * Math.pow(1 - d / radius, 0.6));
                if (b > light[i][j]) light[i][j] = b;
                if (reveal) explored[i][j] = true;
            }
        }
    }
}
