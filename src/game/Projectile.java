package game;

public class Projectile extends Entity {
    public enum Kind { FIREBALL, ARROW, FROST }

    public final Kind kind;
    public final boolean fromPlayer;
    public final double vx, vy, damage;
    public double life, z = 26;

    public Projectile(Kind kind, double x, double y, double vx, double vy, double damage, boolean fromPlayer, double life) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.fromPlayer = fromPlayer;
        this.life = life;
    }
}
