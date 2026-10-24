package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Random item generation with Diablo-style rarities and affixes. */
final class Loot {
    private Loot() {}

    private static final String[][] BASES = {
            {"Short Sword", "Falchion", "Broad Sword", "Battle Axe", "War Hammer", "Great Sword", "Executioner Sword"},
            {"Buckler", "Small Shield", "Kite Shield", "Tower Shield", "Gothic Shield"},
            {"Cap", "Skull Cap", "Helm", "Full Helm", "Great Helm"},
            {"Rags", "Leather Armor", "Ring Mail", "Chain Mail", "Plate Mail", "Full Plate Mail"},
            {"Sandals", "Leather Boots", "Heavy Boots", "Chain Boots", "Plated Greaves"},
            {"Ring"}
    };
    private static final String[] RARE_A = {"Grim", "Doom", "Blood", "Storm", "Rune", "Death", "Shadow", "Bone",
            "Wraith", "Dread", "Soul", "Gloom", "Ash", "Hate"};
    private static final String[] RARE_B = {"Bite", "Song", "Ward", "Shroud", "Spiral", "Grasp", "Fang", "Mark",
            "Veil", "Coil", "Brand", "Wish", "Thirst", "Knell"};
    private static final String[][] UNIQUES = {
            {"Soulrender", "Grimfang", "Emberbrand", "Widowmaker"},
            {"Bulwark of Ages", "Ironheart", "Bonewall"},
            {"Crown of Embers", "Visage of Dread", "Thornhelm"},
            {"Shroud of Night", "Ashen Carapace", "Mantle of the Deep"},
            {"Wanderstride", "Emberwalk", "Grave Treads"},
            {"Band of Ruin", "Eye of the Abyss", "Coil of Thorns"}
    };

    static Item randomEquipment(int depth, Random r, int boost) {
        Item.Slot slot = Item.Slot.values()[r.nextInt(6)];
        String[] bases = BASES[slot.ordinal()];
        int tier = Math.max(0, Math.min(bases.length - 1, (depth - 1) / 2 + r.nextInt(2) - (r.nextInt(3) == 0 ? 1 : 0)));
        Item it = new Item();
        it.slot = slot;
        it.baseName = bases[tier];
        switch (slot) {
            case WEAPON -> {
                it.minDmg = 2 + tier * 3 + r.nextInt(2);
                it.maxDmg = it.minDmg + 3 + tier * 3 + r.nextInt(3);
            }
            case SHIELD -> it.armor = 4 + tier * 4 + r.nextInt(3);
            case HELM -> it.armor = 2 + tier * 3 + r.nextInt(3);
            case ARMOR -> it.armor = 4 + tier * 6 + r.nextInt(5);
            case BOOTS -> it.armor = 1 + tier * 2 + r.nextInt(2);
            default -> { }
        }

        int roll = r.nextInt(100) + boost;
        it.rarity = roll >= 98 ? Item.Rarity.UNIQUE : roll >= 88 ? Item.Rarity.RARE
                : roll >= 58 ? Item.Rarity.MAGIC : Item.Rarity.NORMAL;
        if (slot == Item.Slot.RING && it.rarity == Item.Rarity.NORMAL) it.rarity = Item.Rarity.MAGIC;
        int affixes = switch (it.rarity) {
            case NORMAL -> 0;
            case MAGIC -> 1 + r.nextInt(2);
            case RARE -> 3 + r.nextInt(2);
            case UNIQUE -> 5;
        };

        List<Integer> pool = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        Collections.shuffle(pool, r);
        String prefix = null, suffix = null;
        int p = 2 + depth;
        double mul = it.rarity == Item.Rarity.UNIQUE ? 1.5 : 1;
        for (int k = 0; k < affixes; k++) {
            switch (pool.get(k)) {
                case 0 -> {
                    int v = (int) ((1 + r.nextInt(p)) * mul);
                    it.minDmg += Math.max(1, v / 2);
                    it.maxDmg += v;
                    if (prefix == null) prefix = v > 6 ? "Cruel" : v > 3 ? "Jagged" : "Sharp";
                }
                case 1 -> {
                    int v = (int) ((5 + r.nextInt(p * 4)) * mul);
                    it.life += v;
                    if (suffix == null) suffix = v > 30 ? "of the Whale" : v > 15 ? "of Vigor" : "of Health";
                }
                case 2 -> {
                    int v = (int) ((4 + r.nextInt(p * 3)) * mul);
                    it.mana += v;
                    if (suffix == null) suffix = v > 25 ? "of Wizardry" : "of the Mind";
                }
                case 3 -> {
                    int v = (int) ((2 + r.nextInt(p * 2)) * mul);
                    it.armor += v;
                    if (prefix == null) prefix = v > 15 ? "Blessed" : "Sturdy";
                }
                case 4 -> {
                    int v = (int) ((5 + r.nextInt(p * 4)) * mul);
                    it.spell += v;
                    if (prefix == null) prefix = v > 30 ? "Infernal" : "Arcane";
                }
                default -> {
                    int v = (int) ((1 + r.nextInt(Math.min(6, 1 + depth / 2))) * mul);
                    it.leech += v;
                    if (suffix == null) suffix = "of the Leech";
                }
            }
        }
        it.name = switch (it.rarity) {
            case NORMAL -> it.baseName;
            case MAGIC -> (prefix != null ? prefix + " " : "") + it.baseName + (suffix != null ? " " + suffix : "");
            case RARE -> RARE_A[r.nextInt(RARE_A.length)] + " " + RARE_B[r.nextInt(RARE_B.length)];
            case UNIQUE -> {
                String[] names = UNIQUES[slot.ordinal()];
                yield names[r.nextInt(names.length)];
            }
        };
        return it;
    }

    /** Index of the item's base type within its slot (0 = weakest); drives the icon's material. */
    static int tier(Item it) {
        if (!it.slot.equippable()) return 0;
        String[] bases = BASES[it.slot.ordinal()];
        for (int i = 0; i < bases.length; i++) if (bases[i].equals(it.baseName)) return i;
        return 0;
    }

    static Item gold(int amount) {
        Item it = new Item();
        it.slot = Item.Slot.GOLD;
        it.amount = amount;
        it.name = it.baseName = amount + " Gold";
        return it;
    }

    static Item potion(boolean health) {
        Item it = new Item();
        it.slot = health ? Item.Slot.HEALTH_POTION : Item.Slot.MANA_POTION;
        it.name = it.baseName = health ? "Healing Potion" : "Mana Potion";
        return it;
    }

    static Item scroll() {
        Item it = new Item();
        it.slot = Item.Slot.TOWN_PORTAL;
        it.name = it.baseName = "Scroll of Town Portal";
        return it;
    }

    static List<Item> smithStock(int depth, Random r) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < 14; i++) out.add(randomEquipment(Math.max(1, depth), r, 10));
        out.sort((a, b) -> a.slot.compareTo(b.slot));
        return out;
    }

    static Item starterWeapon() {
        Item it = new Item();
        it.slot = Item.Slot.WEAPON;
        it.name = it.baseName = "Short Sword";
        it.minDmg = 2;
        it.maxDmg = 5;
        return it;
    }

    static Item starterArmor() {
        Item it = new Item();
        it.slot = Item.Slot.ARMOR;
        it.name = it.baseName = "Rags";
        it.armor = 4;
        return it;
    }

    static List<Item> dropsFor(Monster m, int depth, Random r) {
        List<Item> out = new ArrayList<>();
        boolean boss = m.type == Monster.Type.BUTCHER;
        int rolls = boss ? 4 : m.champion ? 2 : 1;
        int boost = boss ? 30 : m.champion ? 12 : 0;
        for (int i = 0; i < rolls; i++) {
            if (boss || r.nextDouble() < (m.champion ? 0.55 : 0.12)) out.add(randomEquipment(depth, r, boost));
        }
        if (boss || r.nextDouble() < 0.45) out.add(gold((5 + r.nextInt(15)) * depth * (boss ? 10 : 1)));
        if (r.nextDouble() < 0.2) out.add(potion(r.nextDouble() < 0.6));
        if (r.nextDouble() < 0.04) out.add(scroll());
        return out;
    }
}
