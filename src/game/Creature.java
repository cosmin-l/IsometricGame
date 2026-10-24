package game;

import java.util.List;

/** Shared movement / animation state for the player and monsters. */
public abstract class Creature extends Entity {
    public double hp, radius = 0.3, speed = 3;
    public double facing;            // world-space angle
    public double animTime;
    public boolean moving;
    public double hitFlash;
    public double swing, swingTime = 0.4; // attack animation timer
    public boolean hitPending;
    public double cooldown;
    public double repathTimer;
    public List<double[]> path;
    public int pathIdx;

    public abstract double maxHp();

    public void setPath(List<double[]> p) {
        path = p;
        pathIdx = 0;
    }

    public void stop() { path = null; }

    public boolean hasPath() { return path != null && pathIdx < path.size(); }

    public double distTo(Entity e) { return Math.hypot(e.x - x, e.y - y); }

    public void followPath(double dt, Dungeon d, double speedMul) {
        if (!hasPath()) {
            path = null;
            return;
        }
        double[] t = path.get(pathIdx);
        double dx = t[0] - x, dy = t[1] - y, dist = Math.hypot(dx, dy);
        double step = speed * speedMul * dt;
        if (dist > 1e-3) facing = Math.atan2(dy, dx);
        double ox = x, oy = y;
        if (dist <= step) {
            tryMove(d, dx, dy);
            pathIdx++;
        } else {
            tryMove(d, dx / dist * step, dy / dist * step);
            if (Math.hypot(x - ox, y - oy) < step * 0.2) pathIdx++; // blocked: give up on this waypoint
        }
        moving = true;
        if (!hasPath()) path = null;
    }

    public void moveDirect(double tx, double ty, double dt, Dungeon d, double speedMul) {
        double dx = tx - x, dy = ty - y, dist = Math.hypot(dx, dy);
        if (dist < 1e-3) return;
        double step = Math.min(dist, speed * speedMul * dt);
        tryMove(d, dx / dist * step, dy / dist * step);
        facing = Math.atan2(dy, dx);
        moving = true;
    }

    public void tryMove(Dungeon d, double mx, double my) {
        if (canStand(d, x + mx, y)) x += mx;
        if (canStand(d, x, y + my)) y += my;
    }

    public boolean canStand(Dungeon d, double nx, double ny) {
        double r = radius * 0.8;
        return d.walkable(nx - r, ny - r) && d.walkable(nx + r, ny - r)
                && d.walkable(nx - r, ny + r) && d.walkable(nx + r, ny + r);
    }
}
