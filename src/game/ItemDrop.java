package game;

import java.awt.Rectangle;

/** An item lying on the dungeon floor. */
public class ItemDrop extends Entity {
    public final Item item;
    public Rectangle labelRect;
    public double bounce = 1; // drop animation, counts down to 0

    public ItemDrop(Item item, double x, double y) {
        this.item = item;
        this.x = x;
        this.y = y;
    }
}
