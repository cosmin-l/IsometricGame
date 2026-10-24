package game;

import java.util.ArrayList;
import java.util.List;

/** Everything that persists on one map while the player is away from it (e.g. through a town portal). */
final class Level {
    final Dungeon dungeon;
    final int depth; // 0 = town
    final List<Monster> monsters = new ArrayList<>();
    final List<ItemDrop> drops = new ArrayList<>();
    final List<Decal> decals = new ArrayList<>();
    final List<Npc> npcs = new ArrayList<>();
    Portal portal;
    boolean bossAnnounced;

    Level(Dungeon dungeon, int depth) {
        this.dungeon = dungeon;
        this.depth = depth;
    }
}
