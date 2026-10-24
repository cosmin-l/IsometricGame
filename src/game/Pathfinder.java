package game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** 8-directional A* over the dungeon grid (no corner cutting). Returns tile-center waypoints. */
final class Pathfinder {
    private Pathfinder() {}

    static List<double[]> find(Dungeon d, int sx, int sy, int tx, int ty, int maxNodes) {
        if (!d.walkable(tx, ty)) return null;
        List<double[]> out = new ArrayList<>();
        if (sx == tx && sy == ty) return out;
        int w = d.w, goal = tx + ty * w, start = sx + sy * w;
        double[] g = new double[w * d.h];
        int[] parent = new int[w * d.h];
        boolean[] closed = new boolean[w * d.h];
        Arrays.fill(g, Double.MAX_VALUE);
        Arrays.fill(parent, -1);
        PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));
        g[start] = 0;
        open.add(new double[]{heuristic(sx, sy, tx, ty), start});
        int expanded = 0;
        boolean found = false;
        while (!open.isEmpty()) {
            int c = (int) open.poll()[1];
            if (closed[c]) continue;
            closed[c] = true;
            if (c == goal) {
                found = true;
                break;
            }
            if (++expanded > maxNodes) return null;
            int cx = c % w, cy = c / w;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx, ny = cy + dy;
                    if (!d.walkable(nx, ny)) continue;
                    boolean diag = dx != 0 && dy != 0;
                    if (diag && (!d.walkable(cx + dx, cy) || !d.walkable(cx, cy + dy))) continue;
                    int n = nx + ny * w;
                    if (closed[n]) continue;
                    double ng = g[c] + (diag ? 1.41421 : 1.0);
                    if (ng < g[n]) {
                        g[n] = ng;
                        parent[n] = c;
                        open.add(new double[]{ng + heuristic(nx, ny, tx, ty), n});
                    }
                }
            }
        }
        if (!found) return null;
        for (int c = goal; c != start; c = parent[c]) out.add(new double[]{c % w + 0.5, c / w + 0.5});
        Collections.reverse(out);
        return out;
    }

    private static double heuristic(int x, int y, int tx, int ty) {
        int dx = Math.abs(x - tx), dy = Math.abs(y - ty);
        return Math.max(dx, dy) + 0.41421 * Math.min(dx, dy);
    }
}
