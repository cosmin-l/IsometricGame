package game;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Item {
    public enum Slot {
        WEAPON("Weapon"), SHIELD("Shield"), HELM("Helm"), ARMOR("Body Armor"), BOOTS("Boots"), RING("Ring"),
        GOLD("Gold"), HEALTH_POTION("Potion"), MANA_POTION("Potion"), TOWN_PORTAL("Scroll");

        public final String label;

        Slot(String label) { this.label = label; }

        public boolean equippable() { return ordinal() <= RING.ordinal(); }
    }

    public enum Rarity {
        NORMAL(new Color(225, 225, 225)), MAGIC(new Color(120, 130, 255)),
        RARE(new Color(255, 240, 90)), UNIQUE(new Color(210, 172, 95));

        public final Color color;

        Rarity(Color c) { color = c; }
    }

    public String name, baseName;
    public Slot slot;
    public Rarity rarity = Rarity.NORMAL;
    public int minDmg, maxDmg, armor, life, mana, spell, leech, amount;

    public Color color() {
        return switch (slot) {
            case GOLD -> new Color(255, 215, 0);
            case HEALTH_POTION -> new Color(230, 50, 50);
            case MANA_POTION -> new Color(70, 110, 255);
            case TOWN_PORTAL -> new Color(150, 190, 255);
            default -> rarity.color;
        };
    }

    public List<String> describe() {
        List<String> l = new ArrayList<>();
        switch (slot) {
            case GOLD -> l.add("Currency");
            case HEALTH_POTION -> l.add("Restores 60% Life");
            case MANA_POTION -> l.add("Restores 60% Mana");
            case TOWN_PORTAL -> l.add("Opens a portal back to town");
            default -> {
                if (rarity == Rarity.RARE || rarity == Rarity.UNIQUE) l.add(baseName);
                if (slot == Slot.WEAPON) l.add("Damage: " + minDmg + " - " + maxDmg);
                else if (maxDmg > 0) l.add("+" + minDmg + " - " + maxDmg + " Damage");
                if (armor > 0) l.add(slot == Slot.RING ? "+" + armor + " Armor" : "Armor: " + armor);
                if (life > 0) l.add("+" + life + " to Life");
                if (mana > 0) l.add("+" + mana + " to Mana");
                if (spell > 0) l.add("+" + spell + "% Spell Damage");
                if (leech > 0) l.add(leech + "% Life Stolen per Hit");
            }
        }
        return l;
    }

    /** Shop price. */
    public int value() {
        return switch (slot) {
            case GOLD -> amount;
            case HEALTH_POTION, MANA_POTION -> 30;
            case TOWN_PORTAL -> 80;
            default -> {
                double v = 15 + (minDmg + maxDmg) * 6 + armor * 5 + life * 4 + mana * 3 + spell * 4 + leech * 40;
                double mul = switch (rarity) {
                    case NORMAL -> 1;
                    case MAGIC -> 2;
                    case RARE -> 3.5;
                    case UNIQUE -> 6;
                };
                yield (int) (v * mul);
            }
        };
    }

    public int sellValue() { return Math.max(1, value() / 4); }

    public String serialize() {
        return String.join("|", slot.name(), rarity.name(), name, baseName,
                String.valueOf(minDmg), String.valueOf(maxDmg), String.valueOf(armor), String.valueOf(life),
                String.valueOf(mana), String.valueOf(spell), String.valueOf(leech), String.valueOf(amount));
    }

    public static Item deserialize(String s) {
        String[] p = s.split("\\|", -1);
        Item it = new Item();
        it.slot = Slot.valueOf(p[0]);
        it.rarity = Rarity.valueOf(p[1]);
        it.name = p[2];
        it.baseName = p[3];
        it.minDmg = Integer.parseInt(p[4]);
        it.maxDmg = Integer.parseInt(p[5]);
        it.armor = Integer.parseInt(p[6]);
        it.life = Integer.parseInt(p[7]);
        it.mana = Integer.parseInt(p[8]);
        it.spell = Integer.parseInt(p[9]);
        it.leech = Integer.parseInt(p[10]);
        it.amount = Integer.parseInt(p[11]);
        return it;
    }
}
