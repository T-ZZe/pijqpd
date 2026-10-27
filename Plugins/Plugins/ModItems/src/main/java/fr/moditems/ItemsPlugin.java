package fr.moditems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    // ====== REGLAGES ======
    static final int SHOVEL_COOLDOWN = 60;        // pelle : 1 minute
    static final int ICE_COOLDOWN = 120;          // épée de glace : 2 minutes
    static final int ICE_SECONDS = 5;             // durée dans la glace
    static final int WARDEN_COOLDOWN = 60;        // épée du Warden : 1 minute
    static final double WARDEN_DAMAGE = 16;       // dégâts de l'onde sonique
    static final int BOW_COOLDOWN = 15;           // arc de la tempête
    static final int GLIDER_BASE_USES = 5;        // planeur : 5 utilisations (15 avec Solidité III)
    static final double GLIDE_SPEED = 0.5;        // vitesse horizontale du planeur
    static final double GLIDE_FALL = 0.2;         // vitesse de descente du planeur
    static final int TREE_MAX_LOGS = 300;         // taille max d'un arbre abattu

    static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS,
            Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    // ====== LES 9 OBJETS ======
    enum Tool {
        HAMMER("hammer", "§6Marteau du Titan", Material.NETHERITE_PICKAXE,
                new String[]{"NNN", " S ", " S "}, "N=NETHERITE_BLOCK,S=STICK",
                "Mine en 3x3. Shift : minage normal."),
        AXE("axe", "§aHache du Bûcheron", Material.DIAMOND_AXE,
                new String[]{"DD", "DS", " S"}, "D=DIAMOND_BLOCK,S=STICK",
                "Abat l'arbre entier d'un coup. Shift : normal."),
        SHOVEL("shovel", "§eÉcraseur de Terre", Material.DIAMOND_SHOVEL,
                new String[]{"D", "S", "S"}, "D=DIAMOND_BLOCK,S=STICK",
                "Clic droit : creuse 8x8 autour de toi (recharge 1 min)."),
        WARDEN("warden", "§3Épée du Warden", Material.NETHERITE_SWORD,
                new String[]{"E", "N", "S"}, "E=ECHO_SHARD,N=NETHERITE_INGOT,S=STICK",
                "Clic droit : onde sonique (recharge 1 min)."),
        ICE("ice", "§bÉpée du Givre", Material.DIAMOND_SWORD,
                new String[]{"B", "D", "S"}, "B=BLUE_ICE,D=DIAMOND_BLOCK,S=STICK",
                "Frapper : emprisonne l'ennemi dans la glace 5 s (recharge 2 min)."),
        HOE("hoe", "§2Houe du Fermier", Material.DIAMOND_HOE,
                new String[]{"EE", " S", " S"}, "E=EMERALD_BLOCK,S=STICK",
                "Clic droit : récolte et replante en 5x5."),
        BOW("bow", "§9Arc de la Tempête", Material.BOW,
                new String[]{" LS", "L S", " LS"}, "L=LIGHTNING_ROD,S=STRING",
                "Toutes les 15 s, ta flèche invoque la foudre."),
        GLIDER("glider", "§fPlaneur", Material.CARROT_ON_A_STICK,
                new String[]{"MMM", "FSF", " S "}, "M=PHANTOM_MEMBRANE,F=FEATHER,S=STICK",
                "Clic droit en l'air : plane vers le bas, sans dégâts de chute.");

        final String id, name, ingredients, desc;
        final Material base;
        final String[] shape;

        Tool(String id, String name, Material base, String[] shape, String ingredients, String desc) {
            this.id = id; this.name = name; this.base = base;
            this.shape = shape; this.ingredients = ingredients; this.desc = desc;
        }

        static Tool byId(String id) {
            if (id == null) return null;
            for (Tool t : values()) if (t.id.equalsIgnoreCase(id)) return t;
            return null;
        }
    }

    private NamespacedKey toolKey, stormKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final Map<UUID, Map<Tool, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>(); // évite de re-déclencher nos propres casses multiples
    private final Set<UUID> gliding = new HashSet<>();
    private final Map<UUID, Long> glideEnd = new HashMap<>();

    @Override
    public void onEnable() {
        toolKey = new NamespacedKey(this, "tool");
        stormKey = new NamespacedKey(this, "storm_arrow");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("moditems").setExecutor(this);
        getCommand("recettes").setExecutor(this);
        registerRecipes();
        for (Player p : getServer().getOnlinePlayers()) p.discoverRecipes(recipeKeys);
    }

    private static Component c(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    // ====== OBJETS + RECETTES ======
    private ItemStack createItem(Tool t) {
        ItemStack it = new ItemStack(t.base);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(t.name));
        meta.lore(List.of(c("§7" + t.desc), c("§8Objet spécial")));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, t.id);
        it.setItemMeta(meta);
        if (t == Tool.GLIDER) syncGlider(it);
        return it;
    }

    private Tool toolOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return Tool.byId(it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING));
    }

    private void registerRecipes() {
        for (Tool t : Tool.values()) {
            NamespacedKey key = new NamespacedKey(this, "recipe_" + t.id);
            Bukkit.removeRecipe(key);
            ShapedRecipe r = new ShapedRecipe(key, createItem(t));
            r.shape(t.shape);
            for (String pair : t.ingredients.split(",")) {
                String[] kv = pair.split("=");
                r.setIngredient(kv[0].charAt(0), Material.valueOf(kv[1]));
            }
            Bukkit.addRecipe(r);
            recipeKeys.add(key);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(recipeKeys);
    }

    // ====== OUTILS ======
    private String frName(Material m) {
        return switch (m) {
            case NETHERITE_BLOCK -> "Bloc de netherite";
            case NETHERITE_INGOT -> "Lingot de netherite";
            case DIAMOND_BLOCK -> "Bloc de diamant";
            case DIAMOND -> "Diamant";
            case EMERALD_BLOCK -> "Bloc d'émeraude";
            case STICK -> "Bâton";
            case STRING -> "Ficelle";
            case ECHO_SHARD -> "Éclat d'écho";
            case BLUE_ICE -> "Glace bleue";
            case PHANTOM_MEMBRANE -> "Membrane de Phantom";
            case FEATHER -> "Plume";
            case LIGHTNING_ROD -> "Paratonnerre";
            default -> m.name().toLowerCase().replace('_', ' ');
        };
    }

    private List<Component> recipeLore(Tool t) {
        List<Component> l = new ArrayList<>();
        l.add(c("§7" + t.desc));
        l.add(c(""));
        l.add(c("§eRecette :"));
        for (String row : t.shape) {
            StringBuilder sb = new StringBuilder("§f");
            for (char ch : row.toCharArray()) sb.append(ch == ' ' ? "[ ]" : "[" + ch + "]");
            l.add(c(sb.toString()));
        }
        for (String pair : t.ingredients.split(",")) {
            String[] kv = pair.split("=");
            l.add(c("§8" + kv[0] + " = " + frName(Material.valueOf(kv[1]))));
        }
        return l;
    }

    private String fmt(long s) {
        return s >= 60 ? (s / 60) + "min " + (s % 60) + "s" : s + "s";
    }

    private boolean ready(Player p, Tool t) {
        Map<Tool, Long> m = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Long until = m.get(t);
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            p.sendActionBar(c("§c" + t.name.replaceAll("§.", "") + " : " + fmt((until - now) / 1000 + 1)));
            return false;
        }
        return true;
    }

    private void startCooldown(Player p, Tool t, int seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(t, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Casse un bloc comme si le joueur l'avait miné (les autres plugins sont prévenus). */
    private boolean breakBlock(Player p, Block b, ItemStack tool) {
        Material m = b.getType();
        if (m.isAir() || m.getHardness() < 0 || b.isLiquid()) return false;
        BlockBreakEvent ev = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;
        b.breakNaturally(tool);
        return true;
    }

    /** Retire de la durabilité. Retourne false si l'outil s'est cassé. */
    private boolean damageTool(Player p, int amount) {
        if (p.getGameMode() == GameMode.CREATIVE) return true;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!(tool.getItemMeta() instanceof Damageable d)) return true;
        int max = tool.getType().getMaxDurability();
        if (max <= 0) return true;
        int nd = d.getDamage() + amount;
        if (nd >= max) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return false;
        }
        d.setDamage(nd);
        tool.setItemMeta(d);
        p.getInventory().setItemInMainHand(tool);
        return true;
    }

    // ====== MARTEAU (3x3) + HACHE (arbre entier) ======
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId()) || p.isSneaking() || p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Tool t = toolOf(tool);
        if (t == Tool.HAMMER) hammer(p, e.getBlock(), tool);
        else if (t == Tool.AXE && Tag.LOGS.isTagged(e.getBlock().getType())) fellTree(p, e.getBlock(), tool);
    }

    private void hammer(Player p, Block origin, ItemStack tool) {
        RayTraceResult rt = p.rayTraceBlocks(7);
        BlockFace face = (rt != null && rt.getHitBlockFace() != null) ? rt.getHitBlockFace() : BlockFace.UP;
        busy.add(p.getUniqueId());
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    Block tg = switch (face) {
                        case UP, DOWN -> origin.getRelative(a, 0, b);
                        case NORTH, SOUTH -> origin.getRelative(a, b, 0);
                        default -> origin.getRelative(0, b, a);
                    };
                    if (!tg.isPreferredTool(tool)) continue;
                    if (!breakBlock(p, tg, tool)) continue;
                    if (!damageTool(p, 1)) return;
                }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    private boolean hasLeavesAround(Set<Block> logs) {
        for (Block l : logs) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (Tag.LEAVES.isTagged(l.getRelative(dx, dy, dz).getType())) return true;
        }
        return false;
    }

    private void fellTree(Player p, Block origin, ItemStack tool) {
        Set<Block> logs = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        logs.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && logs.size() < TREE_MAX_LOGS) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = cur.getRelative(dx, dy, dz);
                        if (!logs.contains(n) && Tag.LOGS.isTagged(n.getType())) { logs.add(n); queue.add(n); }
                    }
        }
        if (!hasLeavesAround(logs)) return; // pas un arbre (une maison en bois, par exemple)

        int broken = 0;
        busy.add(p.getUniqueId());
        try {
            for (Block l : logs) {
                if (l.equals(origin)) continue;
                if (breakBlock(p, l, tool)) broken++;
            }
            int leaves = 0;
            for (Block l : logs) {
                for (int dx = -2; dx <= 2 && leaves < 500; dx++)
                    for (int dy = -2; dy <= 2 && leaves < 500; dy++)
                        for (int dz = -2; dz <= 2 && leaves < 500; dz++) {
                            Block lv = l.getRelative(dx, dy, dz);
                            if (Tag.LEAVES.isTagged(lv.getType()) && lv.getBlockData() instanceof Leaves ld && !ld.isPersistent()) {
                                lv.breakNaturally();
                                leaves++;
                            }
                        }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
        damageTool(p, (broken + 1) / 2);
        p.getWorld().playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.5f, 0.7f);
    }

    // ====== INTERACTIONS (clic droit) ======
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        Tool t = toolOf(tool);
        if (t == null) return;
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return; // ouvrir un coffre, etc.

        switch (t) {
            case SHOVEL -> { if (shovelAbility(p)) e.setCancelled(true); }
            case WARDEN -> { e.setCancelled(true); wardenBlast(p); }
            case GLIDER -> gliderUse(p, tool);
            case HOE -> { if (clicked != null && hoeHarvest(p, clicked, tool)) e.setCancelled(true); }
            default -> { }
        }
    }

    // ---- Pelle : 8x8 autour de soi ----
    private boolean shovelAbility(Player p) {
        if (!ready(p, Tool.SHOVEL)) return false;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Block feet = p.getLocation().getBlock();
        int count = 0;
        busy.add(p.getUniqueId());
        try {
            for (int dx = -4; dx <= 3; dx++) {
                for (int dz = -4; dz <= 3; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dz == 0 && dy == -1) continue; // le bloc sous tes pieds reste
                        Block b = feet.getRelative(dx, dy, dz);
                        if (!b.isPreferredTool(tool)) continue;
                        if (breakBlock(p, b, tool)) count++;
                    }
                }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
        if (count == 0) { p.sendActionBar(c("§cRien à creuser autour de toi.")); return false; }
        damageTool(p, count / 4 + 1);
        startCooldown(p, Tool.SHOVEL, SHOVEL_COOLDOWN);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0), 40, 3, 0.5, 3, 0.02);
        p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 1.5f, 0.6f);
        p.sendActionBar(c("§e" + count + " blocs creusés !"));
        return true;
    }

    // ---- Épée du Warden : onde sonique ----
    private void wardenBlast(Player p) {
        if (!ready(p, Tool.WARDEN)) return;
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult rt = w.rayTraceBlocks(eye, dir, 16, FluidCollisionMode.NEVER, true);
        double len = rt != null ? rt.getHitPosition().distance(eye.toVector()) : 16;
        for (double k = 1; k <= len; k += 2) {
            w.spawnParticle(Particle.SONIC_BOOM, eye.clone().add(dir.clone().multiply(k)), 1, 0, 0, 0, 0);
        }
        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1f);
        for (Entity en : w.getNearbyEntities(eye, 17, 17, 17)) {
            if (!(en instanceof LivingEntity le) || en.equals(p) || en instanceof ArmorStand) continue;
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            double proj = to.dot(dir);
            if (proj < 0 || proj > len + 1) continue;
            double perp = to.clone().subtract(dir.clone().multiply(proj)).length();
            if (perp > 1.6) continue;
            le.damage(WARDEN_DAMAGE, p);
            le.setVelocity(dir.clone().multiply(1.2).setY(0.4));
        }
        startCooldown(p, Tool.WARDEN, WARDEN_COOLDOWN);
    }

    // ---- Houe : récolte 5x5 + replante ----
    private boolean hoeHarvest(Player p, Block clicked, ItemStack tool) {
        Block base = CROPS.contains(clicked.getType()) ? clicked : clicked.getRelative(BlockFace.UP);
        int count = 0;
        busy.add(p.getUniqueId());
        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block b = base.getRelative(dx, 0, dz);
                    if (!CROPS.contains(b.getType()) || !(b.getBlockData() instanceof Ageable age)) continue;
                    if (age.getAge() < age.getMaximumAge()) continue;
                    BlockBreakEvent ev = new BlockBreakEvent(b, p); // prévient les autres plugins (compteurs, etc.)
                    Bukkit.getPluginManager().callEvent(ev);
                    if (ev.isCancelled()) continue;
                    for (ItemStack drop : b.getDrops(tool, p)) {
                        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
                    }
                    age.setAge(0);
                    b.setBlockData(age);
                    count++;
                }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
        if (count == 0) return false;
        damageTool(p, count / 4 + 1);
        p.playSound(p.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 1f);
        return true;
    }

    // ====== EPEE DU GIVRE ======
    @EventHandler(ignoreCancelled = true)
    public void onHitEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) return;
        if (toolOf(p.getInventory().getItemInMainHand()) != Tool.ICE) return;
        if (victim instanceof Player vp && (vp.getGameMode() == GameMode.CREATIVE || vp.getGameMode() == GameMode.SPECTATOR)) return;
        if (!ready(p, Tool.ICE)) return;
        startCooldown(p, Tool.ICE, ICE_COOLDOWN);
        freeze(victim);
        p.sendActionBar(c("§bEnnemi emprisonné dans la glace !"));
    }

    private void freeze(LivingEntity victim) {
        Block base = victim.getLocation().getBlock();
        Location center = base.getLocation().add(0.5, 0, 0.5);
        center.setYaw(victim.getLocation().getYaw());
        center.setPitch(victim.getLocation().getPitch());
        victim.teleport(center);
        victim.setVelocity(new Vector(0, 0, 0));

        Map<Block, BlockData> saved = new HashMap<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
                    if (inside) continue;
                    Block b = base.getRelative(dx, dy, dz);
                    if (b.getType().isAir() || b.isReplaceable()) {
                        saved.put(b, b.getBlockData());
                        b.setType(Material.PACKED_ICE, false);
                    }
                }
            }
        }
        World w = victim.getWorld();
        w.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 60, 0.8, 1, 0.8, 0.05);
        w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        victim.setFreezeTicks(ICE_SECONDS * 20 + 40);

        boolean hadAi = victim.hasAI();
        if (victim instanceof Mob) victim.setAI(false);
        if (victim instanceof Player) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ICE_SECONDS * 20, 10));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ICE_SECONDS * 20, 200));
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Map.Entry<Block, BlockData> en : saved.entrySet()) {
                if (en.getKey().getType() == Material.PACKED_ICE) en.getKey().setBlockData(en.getValue());
            }
            if (victim instanceof Mob && victim.isValid()) victim.setAI(hadAi);
            w.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 40, 0.8, 1, 0.8, 0.05);
            w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);
        }, ICE_SECONDS * 20L);
    }

    // ====== ARC DE LA TEMPETE ======
    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (toolOf(e.getBow()) != Tool.BOW) return;
        if (!ready(p, Tool.BOW)) return; // en recharge : flèche normale
        startCooldown(p, Tool.BOW, BOW_COOLDOWN);
        e.getProjectile().getPersistentDataContainer().set(stormKey, PersistentDataType.BYTE, (byte) 1);
        p.sendActionBar(c("§9Flèche de tempête !"));
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        Entity pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(stormKey, PersistentDataType.BYTE)) return;
        Location l = e.getHitEntity() != null ? e.getHitEntity().getLocation() : pr.getLocation();
        World w = l.getWorld();
        w.strikeLightningEffect(l);
        w.spawnParticle(Particle.ELECTRIC_SPARK, l.clone().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        Entity shooter = null;
        if (pr instanceof Projectile pj && pj.getShooter() instanceof Entity s) shooter = s;
        for (Entity en : w.getNearbyEntities(l, 3, 3, 3)) {
            if (!(en instanceof LivingEntity le) || en.equals(shooter) || en instanceof ArmorStand) continue;
            if (shooter != null) le.damage(8, shooter); else le.damage(8);
            le.setFireTicks(60);
        }
        pr.remove();
    }

    // ====== PLANEUR ======
    private Enchantment unbreaking() {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
    }

    /** 5 utilisations, 15 avec Solidité III (8 / 12 / 15 pour I / II / III). */
    private int maxUses(int unbreakingLevel) {
        return GLIDER_BASE_USES + (int) Math.round(unbreakingLevel * 10 / 3.0);
    }

    /** Met à jour la barre de durabilité (= utilisations) et la description du planeur. */
    private void syncGlider(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (!(m instanceof Damageable d)) return;
        Enchantment unb = unbreaking();
        int max = maxUses(unb == null ? 0 : m.getEnchantLevel(unb));
        d.setMaxDamage(max);
        d.setDamage(Math.min(d.getDamage(), max - 1));
        d.lore(List.of(
                c("§7" + Tool.GLIDER.desc),
                c("§fUtilisations restantes : §e" + (max - d.getDamage()) + "§7/" + max),
                c("§8Impossible de monter. Non réparable.")));
        it.setItemMeta(d);
    }

    private boolean airBelow(Player p, int blocks) {
        Location l = p.getLocation();
        for (int i = 1; i <= blocks; i++) {
            Block b = l.clone().subtract(0, i, 0).getBlock();
            if (!b.isPassable() || b.isLiquid()) return false;
        }
        return true;
    }

    private void gliderUse(Player p, ItemStack item) {
        UUID id = p.getUniqueId();
        if (gliding.contains(id)) return;
        if (p.isOnGround() || p.isInWater() || p.isGliding() || !airBelow(p, 3)) {
            p.sendActionBar(c("§cUtilise le planeur en l'air, depuis une hauteur."));
            return;
        }
        if (!(item.getItemMeta() instanceof Damageable d)) return;
        Enchantment unb = unbreaking();
        int max = maxUses(unb == null ? 0 : item.getItemMeta().getEnchantLevel(unb));
        int used = d.getDamage() + 1;
        if (p.getGameMode() != GameMode.CREATIVE) {
            if (used >= max) {                         // dernière utilisation : le planeur se brise
                p.getInventory().setItemInMainHand(null);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            } else {
                d.setMaxDamage(max);
                d.setDamage(used);
                item.setItemMeta(d);
                syncGlider(item);
                p.getInventory().setItemInMainHand(item);
            }
        }
        startGlide(p);
    }

    private void startGlide(Player p) {
        UUID id = p.getUniqueId();
        gliding.add(id);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 1.1f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                boolean stop = !p.isOnline() || p.isDead() || p.isOnGround() || p.isSneaking()
                        || p.isInWater() || p.isFlying() || p.isGliding() || t > 20 * 180;
                if (stop) {
                    gliding.remove(id);
                    glideEnd.put(id, System.currentTimeMillis());
                    if (p.isOnline()) p.setFallDistance(0);
                    cancel();
                    return;
                }
                Vector look = p.getLocation().getDirection();
                Vector h = new Vector(look.getX(), 0, look.getZ());
                if (h.lengthSquared() > 0.0001) h.normalize().multiply(GLIDE_SPEED); else h = new Vector(0, 0, 0);
                p.setVelocity(new Vector(h.getX(), -GLIDE_FALL, h.getZ())); // jamais vers le haut
                p.setFallDistance(0);
                if (t % 4 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.1, 0), 2, 0.3, 0.1, 0.3, 0.01);
                if (t % 14 == 0 && t > 0) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 1.3f);
                t++;
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        UUID id = p.getUniqueId();
        Long end = glideEnd.get(id);
        if (gliding.contains(id) || (end != null && System.currentTimeMillis() - end < 1500)) e.setCancelled(true);
    }

    // ---- Le planeur ne se répare jamais (enclume, craft, meule, Mending) ----
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        Inventory inv = e.getInventory();
        ItemStack a = inv.getItem(0), b = inv.getItem(1);
        boolean ga = toolOf(a) == Tool.GLIDER, gb = toolOf(b) == Tool.GLIDER;
        if (!ga && !gb) return;
        if (gb) { e.setResult(null); return; }          // pas de combinaison pour réparer
        ItemStack res = e.getResult();
        if (res == null) return;
        Enchantment mending = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("mending"));
        if (mending != null && res.getItemMeta().hasEnchant(mending)) { e.setResult(null); return; }
        syncGlider(res);                                // Solidité : met à jour le nombre d'utilisations
        e.setResult(res);
    }

    @EventHandler
    public void onEnchantTable(PrepareItemEnchantEvent e) {
        if (toolOf(e.getItem()) == Tool.GLIDER) e.setCancelled(true);
    }

    @EventHandler
    public void onCraftGlider(PrepareItemCraftEvent e) {
        for (ItemStack st : e.getInventory().getMatrix()) {
            if (toolOf(st) == Tool.GLIDER) { e.getInventory().setResult(null); return; }
        }
    }

    @EventHandler
    public void onGrindstone(InventoryClickEvent e) {
        if (e.getInventory().getType() != InventoryType.GRINDSTONE || e.getRawSlot() != 2) return;
        ItemStack a = e.getInventory().getItem(0), b = e.getInventory().getItem(1);
        if (a != null && b != null && (toolOf(a) == Tool.GLIDER || toolOf(b) == Tool.GLIDER)) e.setCancelled(true);
    }

    // ====== MENU (/moditems pour les op, /recettes pour tous) ======
    static class ItemsMenu implements InventoryHolder {
        Inventory inv;
        boolean give;
        @Override public Inventory getInventory() { return inv; }
    }

    private void openMenu(Player p, boolean give) {
        ItemsMenu m = new ItemsMenu();
        m.give = give;
        m.inv = Bukkit.createInventory(m, 27, c(give ? "§8Objets spéciaux" : "§8Recettes des objets spéciaux"));
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(c(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) m.inv.setItem(i, pane);
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            ItemStack it = createItem(tools[i]);
            ItemMeta meta = it.getItemMeta();
            List<Component> lore = recipeLore(tools[i]);
            if (give) { lore.add(c("")); lore.add(c("§aClic : prendre l'objet")); }
            meta.lore(lore);
            it.setItemMeta(meta);
            m.inv.setItem(9 + i, it);
        }
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ItemsMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!menu.give || !p.isOp()) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int idx = e.getSlot() - 9;
        Tool[] tools = Tool.values();
        if (idx < 0 || idx >= tools.length) return;
        for (ItemStack left : p.getInventory().addItem(createItem(tools[idx])).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ItemsMenu) e.setCancelled(true);
    }

    // ====== COMMANDES ======
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("recettes")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(c("§cCommande réservée aux joueurs.")); return true; }
            openMenu(p, false);
            return true;
        }
        if (!sender.isOp()) { sender.sendMessage(c("§cCommande réservée aux opérateurs.")); return true; }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            Tool t = Tool.byId(args[2]);
            if (target == null || t == null) {
                sender.sendMessage(c("§cJoueur ou objet inconnu. Objets : hammer, axe, shovel, warden, ice, hoe, bow, glider"));
                return true;
            }
            target.getInventory().addItem(createItem(t));
            sender.sendMessage(c("§a" + t.name + " §adonné à " + target.getName() + "."));
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(c("§c/moditems give <joueur> <objet>")); return true; }
        openMenu(p, true);
        return true;
    }
}
