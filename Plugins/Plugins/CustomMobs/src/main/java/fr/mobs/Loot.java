package fr.mobs;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/** Un objet pouvant tomber d'un boss (ou sortir de son vault), avec son % de chance. */
final class Loot {
    final Material material;
    final int min, max;
    final double chance;     // en pourcentage
    final String name;       // nom custom (null = normal)
    final String[] ench;     // "sharpness:4", ...

    Loot(Material m, int min, int max, double chance) {
        this(m, min, max, chance, null);
    }

    Loot(Material m, int min, int max, double chance, String name, String... ench) {
        this.material = m; this.min = min; this.max = max; this.chance = chance;
        this.name = name; this.ench = ench;
    }

    String displayName() {
        if (name != null) return name;
        String s = material.name().toLowerCase().replace('_', ' ');
        return "§f" + Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    ItemStack create(Random r) {
        int n = min + r.nextInt(max - min + 1);
        ItemStack it = new ItemStack(material, n);
        if (name != null || ench.length > 0) {
            ItemMeta meta = it.getItemMeta();
            if (name != null) {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name)
                        .decoration(TextDecoration.ITALIC, false));
            }
            for (String e : ench) {
                String[] p = e.split(":");
                Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0]));
                if (en != null) meta.addEnchant(en, Integer.parseInt(p[1]), true);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Butin de chaque boss : objets spécifiques, taux différents. */
    static List<Loot> of(Type t) {
        return switch (t) {
            case FLAME -> List.of(
                    new Loot(Material.BLAZE_ROD, 3, 6, 90),
                    new Loot(Material.DIAMOND, 2, 5, 60),
                    new Loot(Material.GOLDEN_APPLE, 1, 2, 40),
                    new Loot(Material.NETHERITE_SCRAP, 1, 2, 30),
                    new Loot(Material.NETHERITE_SWORD, 1, 1, 6, "§6Lame du Gardien",
                            "sharpness:4", "looting:3", "unbreaking:3", "mending:1"));
            case STORM -> List.of(
                    new Loot(Material.LIGHTNING_ROD, 2, 4, 70),
                    new Loot(Material.DIAMOND, 2, 5, 60),
                    new Loot(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 3),
                    new Loot(Material.TRIDENT, 1, 1, 8, "§bFoudroyant",
                            "channeling:1", "loyalty:3", "impaling:5", "unbreaking:3"));
            case SHADOW -> List.of(
                    new Loot(Material.ENDER_PEARL, 4, 8, 80),
                    new Loot(Material.DIAMOND, 2, 5, 55),
                    new Loot(Material.NETHERITE_SCRAP, 1, 2, 25),
                    new Loot(Material.WITHER_SKELETON_SKULL, 1, 1, 10),
                    new Loot(Material.NETHERITE_SWORD, 1, 1, 5, "§5Lame d'Ombre",
                            "sharpness:4", "looting:3", "sweeping_edge:3", "mending:1"));
            case FROST -> List.of(
                    new Loot(Material.BLUE_ICE, 8, 16, 80),
                    new Loot(Material.DIAMOND, 2, 5, 55),
                    new Loot(Material.NETHERITE_SCRAP, 1, 2, 25),
                    new Loot(Material.BOW, 1, 1, 6, "§3Arc du Givre",
                            "power:5", "punch:2", "infinity:1", "unbreaking:3"));
            case VENOM -> List.of(
                    new Loot(Material.SPIDER_EYE, 8, 16, 90),
                    new Loot(Material.EMERALD, 4, 8, 60),
                    new Loot(Material.FERMENTED_SPIDER_EYE, 3, 6, 40),
                    new Loot(Material.DIAMOND_SWORD, 1, 1, 8, "§2Dague Venimeuse",
                            "bane_of_arthropods:5", "sharpness:4", "looting:3", "unbreaking:3"));
            case BERSERK -> List.of(
                    new Loot(Material.DIAMOND, 3, 6, 60),
                    new Loot(Material.NETHERITE_SCRAP, 1, 3, 35),
                    new Loot(Material.TOTEM_OF_UNDYING, 1, 1, 8),
                    new Loot(Material.NETHERITE_AXE, 1, 1, 5, "§cHache du Berserker",
                            "sharpness:4", "efficiency:5", "unbreaking:3", "mending:1"));
            case ABYSS -> List.of(
                    new Loot(Material.PRISMARINE_SHARD, 6, 12, 70),
                    new Loot(Material.DIAMOND, 2, 5, 55),
                    new Loot(Material.NAUTILUS_SHELL, 2, 4, 50),
                    new Loot(Material.HEART_OF_THE_SEA, 1, 1, 25),
                    new Loot(Material.TRIDENT, 1, 1, 6, "§9Trident Abyssal",
                            "impaling:5", "loyalty:3", "unbreaking:3", "mending:1"));
            case BRUTE -> List.of(
                    new Loot(Material.GOLD_BLOCK, 2, 4, 60),
                    new Loot(Material.DIAMOND, 3, 6, 60),
                    new Loot(Material.NETHERITE_INGOT, 1, 1, 15),
                    new Loot(Material.NETHERITE_CHESTPLATE, 1, 1, 5, "§4Plastron de la Brute",
                            "protection:3", "thorns:3", "unbreaking:3", "mending:1"));
            case NECRO -> List.of(
                    new Loot(Material.DIAMOND_BLOCK, 2, 5, 70),
                    new Loot(Material.NETHERITE_INGOT, 2, 4, 50),
                    new Loot(Material.NETHER_STAR, 1, 1, 30),
                    new Loot(Material.TOTEM_OF_UNDYING, 1, 1, 20),
                    new Loot(Material.ENCHANTED_GOLDEN_APPLE, 1, 2, 15),
                    new Loot(Material.ELYTRA, 1, 1, 6, "§8Ailes du Nécromancien",
                            "unbreaking:3", "mending:1"),
                    new Loot(Material.NETHERITE_SWORD, 1, 1, 4, "§8Faux du Nécromancien",
                            "sharpness:4", "looting:3", "sweeping_edge:3", "unbreaking:3", "mending:1"),
                    new Loot(Material.NETHERITE_HELMET, 1, 1, 4, "§8Couronne du Nécromancien",
                            "protection:3", "respiration:3", "aqua_affinity:1", "unbreaking:3", "mending:1"));
        };
    }
}
