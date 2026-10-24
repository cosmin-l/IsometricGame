package game;

import java.util.ArrayList;
import java.util.List;

/** A friendly townsperson who runs a shop. */
public class Npc extends Creature {
    public enum Role { SMITH, HEALER }

    public final Role role;
    public final String name, title;
    public final List<Item> stock = new ArrayList<>();

    public Npc(Role role, String name, String title, double x, double y) {
        this.role = role;
        this.name = name;
        this.title = title;
        this.x = x;
        this.y = y;
        facing = Math.PI / 4; // toward the camera
    }

    @Override
    public double maxHp() { return 1; }
}
