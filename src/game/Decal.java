package game;

import java.awt.Color;

/** Corpses and blood splats left on the floor. */
class Decal {
    double x, y, size, rot;
    Color color;
    boolean corpse;
    Monster.Type type; // whose corpse
}
