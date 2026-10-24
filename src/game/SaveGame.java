package game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Saves the hero (stats, gold, gear, inventory, waypoint progress) to a properties file in the user's home. */
final class SaveGame {
    static final Path FILE = System.getProperty("tristfall.save") != null
            ? Path.of(System.getProperty("tristfall.save"))
            : Path.of(System.getProperty("user.home"), ".tristfall", "save.properties");

    record Loaded(Player player, int maxDepth) {}

    private SaveGame() {}

    static boolean exists() { return Files.isRegularFile(FILE); }

    static void save(Player p, int maxDepth) throws IOException {
        Properties pr = new Properties();
        pr.setProperty("version", "1");
        put(pr, "level", p.level);
        pr.setProperty("xp", String.valueOf(p.xp));
        put(pr, "baseMaxHp", p.baseMaxHp);
        put(pr, "baseMaxMana", p.baseMaxMana);
        put(pr, "baseMin", p.baseMin);
        put(pr, "baseMax", p.baseMax);
        put(pr, "gold", p.gold);
        put(pr, "healthPotions", p.healthPotions);
        put(pr, "manaPotions", p.manaPotions);
        put(pr, "portalScrolls", p.portalScrolls);
        put(pr, "maxDepth", maxDepth);
        int k = 0;
        for (Item it : p.equipment.values()) pr.setProperty("equip." + k++, it.serialize());
        put(pr, "equip.count", k);
        for (int i = 0; i < p.inventory.size(); i++) pr.setProperty("inv." + i, p.inventory.get(i).serialize());
        put(pr, "inv.count", p.inventory.size());

        Files.createDirectories(FILE.getParent());
        Path tmp = FILE.resolveSibling("save.tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            pr.store(w, "Depths of Tristfall save");
        }
        Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
    }

    static Loaded load() throws IOException {
        Properties pr = new Properties();
        try (Reader r = Files.newBufferedReader(FILE)) {
            pr.load(r);
        }
        Player p = new Player();
        p.level = get(pr, "level", 1);
        p.xp = Long.parseLong(pr.getProperty("xp", "0").trim());
        p.baseMaxHp = get(pr, "baseMaxHp", p.baseMaxHp);
        p.baseMaxMana = get(pr, "baseMaxMana", p.baseMaxMana);
        p.baseMin = get(pr, "baseMin", p.baseMin);
        p.baseMax = get(pr, "baseMax", p.baseMax);
        p.gold = get(pr, "gold", 0);
        p.healthPotions = get(pr, "healthPotions", 0);
        p.manaPotions = get(pr, "manaPotions", 0);
        p.portalScrolls = get(pr, "portalScrolls", 0);
        for (int i = 0, n = get(pr, "equip.count", 0); i < n; i++) {
            Item it = Item.deserialize(pr.getProperty("equip." + i));
            p.equipment.put(it.slot, it);
        }
        for (int i = 0, n = get(pr, "inv.count", 0); i < n && i < Player.INV_SIZE; i++) {
            p.inventory.add(Item.deserialize(pr.getProperty("inv." + i)));
        }
        p.hp = p.maxHp();
        p.mana = p.maxMana();
        return new Loaded(p, get(pr, "maxDepth", 0));
    }

    private static void put(Properties pr, String key, int v) { pr.setProperty(key, String.valueOf(v)); }

    private static int get(Properties pr, String key, int def) {
        String v = pr.getProperty(key);
        return v == null ? def : Integer.parseInt(v.trim());
    }
}
