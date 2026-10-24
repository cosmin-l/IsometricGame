package game;

import java.awt.Color;

public class Monster extends Creature {
    public enum Type {
        //       name               hp min max  speed reach  cd   xp  ranged scale
        ZOMBIE("Zombie",            26, 4,  8,  1.1,  0.45, 1.6, 16, false, 1.0),
        FALLEN("Fallen One",        11, 2,  5,  2.8,  0.40, 0.9,  8, false, 0.8),
        SKELETON("Skeleton",        19, 3,  7,  2.2,  0.45, 1.1, 12, false, 1.0),
        ARCHER("Skeleton Archer",   15, 3,  6,  2.0,  7.00, 2.0, 15, true,  1.0),
        BUTCHER("The Butcher",     220, 8, 16,  2.5,  0.55, 1.2, 400, false, 1.7);

        final String name;
        final int hp, minDmg, maxDmg, xp;
        final double speed, reach, attackCd, scale;
        final boolean ranged;

        Type(String name, int hp, int minDmg, int maxDmg, double speed, double reach, double attackCd,
             int xp, boolean ranged, double scale) {
            this.name = name;
            this.hp = hp;
            this.minDmg = minDmg;
            this.maxDmg = maxDmg;
            this.speed = speed;
            this.reach = reach;
            this.attackCd = attackCd;
            this.xp = xp;
            this.ranged = ranged;
            this.scale = scale;
        }
    }

    public final Type type;
    public final boolean champion;
    public final String name;
    public final double maxLife;
    public final int minDmg, maxDmg, xp;
    public boolean aggro;
    public double wanderTimer, slowTimer;

    public Monster(Type type, int depth, boolean champion, double x, double y) {
        this.type = type;
        this.champion = champion;
        this.x = x;
        this.y = y;
        double hpMul = 1 + 0.4 * (depth - 1), dmgMul = 1 + 0.28 * (depth - 1);
        if (champion) {
            hpMul *= 2.2;
            dmgMul *= 1.5;
        }
        maxLife = Math.round(type.hp * hpMul);
        hp = maxLife;
        minDmg = (int) Math.round(type.minDmg * dmgMul);
        maxDmg = (int) Math.round(type.maxDmg * dmgMul);
        xp = (int) (type.xp * (1 + 0.35 * (depth - 1)) * (champion ? 3 : 1));
        speed = type.speed * (champion ? 1.15 : 1);
        radius = 0.28 * scale();
        swingTime = type == Type.ARCHER ? 0.55 : 0.45;
        name = (champion ? "Champion " : "") + type.name;
        cooldown = Math.random();
        wanderTimer = Math.random() * 3;
        facing = Math.random() * Math.PI * 2;
    }

    @Override
    public double maxHp() { return maxLife; }

    public double scale() { return type.scale * (champion ? 1.2 : 1); }

    public double reach(Player p) { return type.reach + radius + p.radius; }

    public boolean skeletal() { return type == Type.SKELETON || type == Type.ARCHER; }

    public Color corpseColor() {
        return switch (type) {
            case ZOMBIE -> new Color(80, 100, 65);
            case FALLEN -> new Color(150, 40, 30);
            case SKELETON, ARCHER -> new Color(200, 195, 175);
            case BUTCHER -> new Color(170, 150, 130);
        };
    }
}
