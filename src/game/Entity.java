package game;

/** Anything positioned in the world. Coordinates are in tile units. */
public abstract class Entity {
    public double x, y;
    public boolean dead;

    /** Painter's-algorithm sort key for isometric drawing. */
    public double depth() { return x + y; }
}
