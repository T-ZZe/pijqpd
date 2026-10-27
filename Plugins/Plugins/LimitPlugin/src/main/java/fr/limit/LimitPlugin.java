package fr.limit;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class LimitPlugin extends JavaPlugin implements Listener {

    // Niveaux max par enchantement (clé minecraft)
    private static final Map<String, Integer> MAX = new HashMap<>();
    static {
        MAX.put("protection", 3);
        MAX.put("feather_falling", 3);
        MAX.put("sharpness", 4);
        MAX.put("riptide", 2);   // Impulsion
        MAX.put("lunge", 2);     // Lunge (lance)
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        // Verification toutes les 10 ticks (0,5 s)
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) check(p);
        }, 20L, 10L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        check(e.getPlayer());
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result != null && sanitize(result)) e.setResult(result);
    }

    private void check(Player p) {
        PlayerInventory inv = p.getInventory();

        // 1) Nettoyage des enchantements
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && sanitize(it)) inv.setItem(i, it);
        }
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && sanitize(cursor)) p.setItemOnCursor(cursor);

        // 2) Une seule pièce d'armure en netherite
        ItemStack[] armor = inv.getArmorContents(); // [bottes, jambières, plastron, casque]
        boolean kept = false;
        boolean changed = false;
        for (int i = armor.length - 1; i >= 0; i--) { // casque -> bottes
            if (isNetheriteArmor(armor[i])) {
                if (!kept) {
                    kept = true;
                } else {
                    ItemStack removed = armor[i];
                    armor[i] = null;
                    changed = true;
                    Map<Integer, ItemStack> left = inv.addItem(removed);
                    left.values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                }
            }
        }
        if (changed) {
            inv.setArmorContents(armor);
            p.sendMessage("§cTu ne peux porter qu'une seule pièce d'armure en netherite !");
        }
    }

    private boolean isNetheriteArmor(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.startsWith("NETHERITE_") && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"));
    }

    /** Retourne true si l'item a été modifié. */
    private boolean sanitize(ItemStack item) {
        if (item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean book = meta instanceof EnchantmentStorageMeta;
        Map<Enchantment, Integer> ench = new HashMap<>(
                book ? ((EnchantmentStorageMeta) meta).getStoredEnchants() : meta.getEnchants());
        if (ench.isEmpty()) return false;

        boolean isSword = item.getType().name().endsWith("_SWORD");
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> en : ench.entrySet()) {
            Enchantment e = en.getKey();
            int lvl = en.getValue();
            String key = e.getKey().getKey();

            // Aura de feu interdite sur l'épée
            if (key.equals("fire_aspect") && isSword) {
                if (book) ((EnchantmentStorageMeta) meta).removeStoredEnchant(e);
                else meta.removeEnchant(e);
                changed = true;
                continue;
            }

            Integer max = MAX.get(key);
            if (max != null && lvl > max) {
                if (book) ((EnchantmentStorageMeta) meta).addStoredEnchant(e, max, true);
                else meta.addEnchant(e, max, true);
                changed = true;
            }
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }
}
