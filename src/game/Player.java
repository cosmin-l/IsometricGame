package game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ToIntFunction;

public class Player extends Creature {
    public static final int INV_SIZE = 21;

    public int level = 1;
    public long xp;
    public double mana;
    public int baseMaxHp = 60, baseMaxMana = 30, baseMin = 1, baseMax = 3;
    public int gold, healthPotions = 3, manaPotions = 2, portalScrolls = 1;
    public final EnumMap<Item.Slot, Item> equipment = new EnumMap<>(Item.Slot.class);
    public final List<Item> inventory = new ArrayList<>();
    public Monster attackTarget;
    public ItemDrop pickupTarget;
    public Entity useTarget; // NPC or portal we are walking toward
    public double castCooldown, novaCooldown, potionCooldown;

    public Player() {
        speed = 4.2;
        radius = 0.3;
        swingTime = 0.36;
    }

    private int sum(ToIntFunction<Item> f) {
        int s = 0;
        for (Item it : equipment.values()) s += f.applyAsInt(it);
        return s;
    }

    @Override
    public double maxHp() { return baseMaxHp + sum(i -> i.life); }

    public double maxMana() { return baseMaxMana + sum(i -> i.mana); }

    public int minDmg() { return baseMin + sum(i -> i.minDmg); }

    public int maxDmg() { return Math.max(minDmg(), baseMax + sum(i -> i.maxDmg)); }

    public int armor() { return sum(i -> i.armor) + level; }

    public int spellBonus() { return sum(i -> i.spell); }

    public int leech() { return sum(i -> i.leech); }

    /** Experience needed to advance from {@code lvl} to {@code lvl + 1}. */
    public static long xpForLevel(int lvl) { return (long) (60 * Math.pow(lvl, 1.7)); }
}
