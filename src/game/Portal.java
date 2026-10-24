package game;

/** A town portal: one end in the dungeon (toTown) and a matching one in town. */
public class Portal extends Entity {
    public final boolean toTown;

    public Portal(double x, double y, boolean toTown) {
        this.x = x;
        this.y = y;
        this.toTown = toTown;
    }
}
