package fr.mobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MobsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    // ====== REGLAGES ======
    static final long COOLDOWN_MS = 8000;          // délai entre 2 attaques spéciales
    static final int MAX_MINIONS = 16;             // sbires du boss en même temps
    static final int GUARD_MAX = 3;                // gardes vivants par spawner de garde
    static final long GUARD_DELAY_MS = 25000;      // délai de réapparition d'un garde
    static final long TRIAL_COOLDOWN_MS = 300000;  // 5 min avant de relancer une épreuve
    static final long TRIAL_TIMEOUT_MS = 900000;   // une épreuve dure 15 min maximum
    static final double GUARD_KEY_CHANCE = 15;     // % de chance qu'un garde lâche 1 clé

    // Mobs invoqués par le boss selon la phase (1, 2, 3)
    static final EntityType[][] POOLS = {
            {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CAVE_SPIDER},
            {EntityType.HUSK, EntityType.STRAY, EntityType.VINDICATOR, EntityType.WITCH, EntityType.SPIDER},
            {EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR,
                    EntityType.RAVAGER, EntityType.BLAZE, EntityType.VEX}
    };

    private final Random rnd = new Random();
    private NamespacedKey eggKey, mobKey, minionKey, guardKey, keyKey, spawnerKey;

    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, Long> nextUse = new HashMap<>();
    private final Map<UUID, Set<UUID>> minions = new HashMap<>();
    private final Map<UUID, Integer> phases = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    // Spawners
    static class Spot {
        final String world, kind; final int x, y, z; final Type type;
        Spot(String world, int x, int y, int z, String kind, Type type) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.kind = kind; this.type = type;
        }
        String key() { return world + ";" + x + ";" + y + ";" + z; }
    }
    static class Trial {
        int stage = 0;
        final Set<UUID> alive = new HashSet<>();
        UUID boss;
        final long start = System.currentTimeMillis();
        long lastPlayer = System.currentTimeMillis();
    }
    private final Map<String, Spot> spots = new HashMap<>();
    private final Map<String, Set<UUID>> guardSets = new HashMap<>();
    private final Map<String, Trial> trials = new HashMap<>();
    private final Map<String, Long> trialCooldown = new HashMap<>();
    private final Map<String, Long> nextGuard = new HashMap<>();
    private File spotsFile;

    @Override
    public void onEnable() {
        eggKey = new NamespacedKey(this, "egg_type");
        mobKey = new NamespacedKey(this, "mob_type");
        minionKey = new NamespacedKey(this, "minion_of");
        guardKey = new NamespacedKey(this, "guard");
        keyKey = new NamespacedKey(this, "boss_key");
        spawnerKey = new NamespacedKey(this, "spawner_item");
        spotsFile = new File(getDataFolder(), "spawners.yml");
        loadSpots();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("mobegg").setExecutor(this);
        getCommand("mobvault").setExecutor(this);
        getCommand("mobspawner").setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::spawnerTick, 40L, 20L);
    }

    @Override
    public void onDisable() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }

    private static Component c(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    private boolean hittable(Player p) {
        return p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
    }

    private void hurt(Player p, double dmg, Entity source) {
        if (source != null && source.isValid()) p.damage(dmg, source); else p.damage(dmg);
    }

    private void applyHit(Player p, Attack a, Entity src, double dmg) {
        hurt(p, dmg, src);
        if (a.ignite > 0 && a.kind != Attack.Kind.VOLLEY) p.setFireTicks(a.ignite);
        if (a.effect != null) p.addPotionEffect(new PotionEffect(a.effect, a.effTicks, a.effAmp));
        if (a.particle == Particle.SNOWFLAKE) p.setFreezeTicks(p.getMaxFreezeTicks());
    }

    private void ringParticles(Location center, double radius, Particle p, int points) {
        World w = center.getWorld();
        for (int i = 0; i < points; i++) {
            double ang = 2 * Math.PI * i / points;
            w.spawnParticle(p, center.clone().add(Math.cos(ang) * radius, 0.15, Math.sin(ang) * radius), 1, 0, 0, 0, 0);
        }
    }

    private void setBase(LivingEntity le, Attribute a, double v) {
        AttributeInstance inst = le.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private Type typeOf(Entity en) {
        return Type.byId(en.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING));
    }

    private boolean isGuard(Entity en) {
        return en.getPersistentDataContainer().has(guardKey, PersistentDataType.BYTE);
    }

    private Player nearestPlayer(Location l, double max) {
        Player best = null;
        double bd = max * max;
        for (Player p : l.getWorld().getPlayers()) {
            if (!hittable(p)) continue;
            double d = p.getLocation().distanceSquared(l);
            if (d <= bd) { bd = d; best = p; }
        }
        return best;
    }

    // ====== OEUFS ======
    private ItemStack createEgg(Type t, int amount) {
        ItemStack it = new ItemStack(t.egg, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(t.color + "Oeuf : " + t.label));
        meta.lore(List.of(c("§7" + t.power), c("§8Clic droit sur un bloc pour l'invoquer.")));
        meta.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, t.id);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Type t = Type.byId(id);
        if (t == null) return;
        Location loc = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        spawnMob(t, loc, false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) it.setAmount(it.getAmount() - 1);
    }

    // ====== CLES DE BOSS ======
    private ItemStack createKey(Type t, int amount) {
        ItemStack it = new ItemStack(Material.TRIAL_KEY, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(t.color + "Clé : " + t.label));
        meta.lore(List.of(c("§7Ouvre le vault de ce boss."), c("§8Utilise /mobvault")));
        meta.getPersistentDataContainer().set(keyKey, PersistentDataType.STRING, t.id);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKey(ItemStack it, Type t) {
        if (it == null || !it.hasItemMeta()) return false;
        return t.id.equals(it.getItemMeta().getPersistentDataContainer().get(keyKey, PersistentDataType.STRING));
    }

    private int countKeys(Player p, Type t) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isKey(it, t)) n += it.getAmount();
        return n;
    }

    private boolean consumeKey(Player p, Type t) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isKey(it, t)) { it.setAmount(it.getAmount() - 1); return true; }
        }
        return false;
    }

    // ====== APPARITION ======
    private LivingEntity spawnMob(Type t, Location loc, boolean guard) {
        EntityType et = (guard && t == Type.NECRO) ? EntityType.VINDICATOR : t.entity;
        if (!(loc.getWorld().spawnEntity(loc, et) instanceof LivingEntity le)) return null;

        double hp = guard ? Math.max(30, t.health * 0.15) : t.health;
        double dmg = guard ? t.damage * 0.5 : t.damage;
        double armor = guard ? t.armor * 0.5 : t.armor;
        double scale = guard ? Math.max(0.8, t.scale * 0.75) : t.scale;

        le.customName(c((guard ? "§7Garde : " : "") + t.color + t.label));
        le.setCustomNameVisible(true);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        le.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, t.id);
        if (guard) le.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);

        setBase(le, Attribute.MAX_HEALTH, hp);
        le.setHealth(hp);
        setBase(le, Attribute.ATTACK_DAMAGE, dmg);
        if (t != Type.NECRO) setBase(le, Attribute.MOVEMENT_SPEED, t.speed);
        setBase(le, Attribute.ARMOR, armor);
        setBase(le, Attribute.KNOCKBACK_RESISTANCE, 0.6);
        setBase(le, Attribute.SCALE, scale);
        setBase(le, Attribute.FOLLOW_RANGE, 40);

        if (le instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        if (le instanceof Zombie z) z.setBaby(false);

        EntityEquipment eq = le.getEquipment();
        if (eq != null && !guard) {
            switch (t) {
                case FLAME -> {
                    eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                    eq.setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                    eq.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));
                }
                case STORM -> {
                    eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                    eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                    eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                    eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                }
                case SHADOW -> {
                    eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                    eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                    eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                }
                case FROST -> {
                    eq.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                    eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                }
                case ABYSS -> {
                    eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                    eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                }
                case BRUTE -> eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                default -> { }
            }
        }
        if (eq != null) {
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }

        if (!guard) {
            tracked.add(le.getUniqueId());
            if (t == Type.NECRO) {
                phases.put(le.getUniqueId(), 1);
                createBar(le);
            }
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        return le;
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (typeOf(e.getEntity()) != null && !isGuard(e.getEntity())) tracked.add(e.getEntity().getUniqueId());
    }

    // ====== MORT : CLES + BUTIN ======
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Entity dead = e.getEntity();
        if (dead.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        Type t = typeOf(dead);
        if (t == null) return;
        e.getDrops().clear();

        if (isGuard(dead)) {
            e.setDroppedExp(40);
            if (rnd.nextDouble() * 100 < GUARD_KEY_CHANCE) e.getDrops().add(createKey(t, 1));
            return;
        }

        UUID id = dead.getUniqueId();
        tracked.remove(id);
        nextUse.remove(id);
        phases.remove(id);
        removeBar(id);
        Set<UUID> mine = minions.remove(id);
        if (mine != null) {
            for (UUID mid : mine) {
                Entity m = Bukkit.getEntity(mid);
                if (m != null && m.isValid()) {
                    m.getWorld().spawnParticle(Particle.SMOKE, m.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                    m.remove();
                }
            }
        }

        e.getDrops().add(createKey(t, 1 + rnd.nextInt(5)));          // 1 à 5 clés
        for (Loot l : Loot.of(t)) {                                   // butin spécifique
            if (rnd.nextDouble() * 100 < l.chance) e.getDrops().add(l.create(rnd));
        }
        e.setDroppedExp(t == Type.NECRO ? 2500 : 500);
        Location l = dead.getLocation();
        l.getWorld().spawnParticle(Particle.EXPLOSION, l.clone().add(0, 1, 0), 3);
    }

    // ====== BARRE DE BOSS + PHASES ======
    private void createBar(Entity boss) {
        BossBar bar = Bukkit.createBossBar("§5§lSeigneur Nécromancien", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bars.put(boss.getUniqueId(), bar);
    }

    private void removeBar(UUID id) {
        BossBar b = bars.remove(id);
        if (b != null) b.removeAll();
    }

    private void updateBoss(Mob boss) {
        UUID id = boss.getUniqueId();
        AttributeInstance mh = boss.getAttribute(Attribute.MAX_HEALTH);
        double max = mh != null ? mh.getValue() : 1;
        double ratio = Math.max(0, Math.min(1, boss.getHealth() / max));
        int newPhase = ratio > 0.66 ? 1 : ratio > 0.33 ? 2 : 3;
        int old = phases.getOrDefault(id, 1);
        if (newPhase > old) {
            phases.put(id, newPhase);
            phaseChange(boss, newPhase);
        } else {
            phases.putIfAbsent(id, newPhase);
        }
        BossBar bar = bars.get(id);
        if (bar == null) { createBar(boss); bar = bars.get(id); }
        bar.setProgress(ratio);
        bar.setTitle("§5§lSeigneur Nécromancien §7- Phase " + phases.get(id) + "/3");
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) <= 60 * 60) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
    }

    private void phaseChange(Mob boss, int phase) {
        World w = boss.getWorld();
        Location c = boss.getLocation();
        w.playSound(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.8f);
        ringParticles(c, 6, Particle.SOUL_FIRE_FLAME, 40);
        w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 1.5, 0), 6, 1.5, 1, 1.5, 0);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4)); // 3 s d'invincibilité
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= 50 * 50) {
                p.showTitle(Title.title(c("§5§lPHASE " + phase), c("§7Le Nécromancien invoque ses serviteurs !")));
            }
        }
        spawnWave(boss, phase, 4 + phase * 2, nearestPlayer(c, 40));
    }

    // ====== SBIRES DU BOSS ======
    private void spawnWave(Mob boss, int phase, int n, Player target) {
        Set<UUID> set = minions.computeIfAbsent(boss.getUniqueId(), k -> new HashSet<>());
        set.removeIf(id -> {
            Entity m = Bukkit.getEntity(id);
            return m == null || !m.isValid();
        });
        n = Math.min(n, MAX_MINIONS - set.size());
        if (n <= 0) return;
        EntityType[] pool = POOLS[Math.max(0, Math.min(2, phase - 1))];
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n + rnd.nextDouble();
            double r = 3 + rnd.nextDouble() * 2;
            Location l = boss.getLocation().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            spawnMinion(boss, pool[rnd.nextInt(pool.length)], l, target, phase, set);
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 2f, 0.7f);
    }

    private void spawnMinion(Mob boss, EntityType type, Location l, Player target, int phase, Set<UUID> set) {
        World w = l.getWorld();
        if (!(w.spawnEntity(l, type) instanceof LivingEntity m)) return;
        m.customName(c("§8Serviteur du Nécromancien"));
        m.setCustomNameVisible(true);
        m.setRemoveWhenFarAway(true);
        m.setCanPickupItems(false);
        m.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, boss.getUniqueId().toString());
        if (m instanceof Zombie z) z.setBaby(false);

        double hp = 40 + 30 * phase;
        setBase(m, Attribute.MAX_HEALTH, hp);
        m.setHealth(hp);
        setBase(m, Attribute.ATTACK_DAMAGE, 6 + 4 * phase);
        setBase(m, Attribute.ARMOR, 4 + 4 * phase);

        EntityEquipment eq = m.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.IRON_HELMET)); // pas de combustion au soleil
            eq.setHelmetDropChance(0f);
        }
        if (m instanceof Mob mm && target != null) mm.setTarget(target);
        set.add(m.getUniqueId());
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
        w.spawnParticle(Particle.SMOKE, l.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
    }

    // ====== BOUCLE DES ATTAQUES SPECIALES ======
    private long cooldownFor(Type t, int phase) {
        if (t != Type.NECRO) return COOLDOWN_MS;
        return phase == 1 ? 9000 : phase == 2 ? 7000 : 5000;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<UUID> it = tracked.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity en = Bukkit.getEntity(id);
            if (!(en instanceof Mob mob) || !mob.isValid()) {
                it.remove(); nextUse.remove(id); phases.remove(id); removeBar(id);
                continue;
            }
            Type t = typeOf(mob);
            if (t == null) continue;
            if (t == Type.NECRO) updateBoss(mob);
            if (!(mob.getTarget() instanceof Player target)) continue;
            if (target.isDead() || !target.getWorld().equals(mob.getWorld()) || !hittable(target)) continue;
            Long nu = nextUse.get(id);
            if (nu != null && nu > now) continue;

            double dist = mob.getLocation().distance(target.getLocation());
            List<Attack> ok = new ArrayList<>();
            for (Attack a : t.attacks) if (dist >= a.min && dist <= a.max) ok.add(a);
            if (ok.isEmpty()) continue;
            Attack a = ok.get(rnd.nextInt(ok.size()));
            int phase = phases.getOrDefault(id, 1);
            if (run(mob, target, a, t.special * a.mult, phase)) nextUse.put(id, now + cooldownFor(t, phase));
        }
    }

    private boolean run(Mob mob, Player target, Attack a, double dmg, int phase) {
        return switch (a.kind) {
            case RING -> ringAttack(mob, a, dmg);
            case CHARGE -> charge(mob, target, a, dmg);
            case BEHIND -> behind(mob, target, a, dmg);
            case CONE -> cone(mob, target, a, dmg);
            case ZONE -> zone(mob, target, a, dmg);
            case LEAP -> leap(mob, target, a, dmg);
            case VORTEX -> vortex(mob, a, dmg);
            case SPIN -> spin(mob, a, dmg);
            case VOLLEY -> volley(mob, target, a, dmg);
            case STRIKES -> strikes(mob, target, a, dmg);
            case FRENZY -> frenzy(mob);
            case SUMMON -> summonAttack(mob, target, phase);
        };
    }

    // ---- RING : onde autour du mob ----
    private boolean ringAttack(Mob mob, Attack a, double dmg) {
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!mob.isValid()) { cancel(); return; }
                Location c = mob.getLocation();
                World w = c.getWorld();
                if (t < 20) {
                    ringParticles(c, a.radius, Particle.SMOKE, 30);
                    ringParticles(c, a.radius * (t / 20.0), a.particle, 20);
                } else {
                    for (double r = 1; r <= a.radius; r += 1) ringParticles(c, r, a.particle, (int) (r * 10));
                    w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 1, 0), 4, 1.5, 0.5, 1.5, 0);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
                    for (Player p : w.getPlayers()) {
                        if (!hittable(p) || p.getLocation().distance(c) > a.radius) continue;
                        applyHit(p, a, mob, dmg);
                        Vector kb = p.getLocation().toVector().subtract(c.toVector());
                        if (kb.lengthSquared() > 0.01) p.setVelocity(kb.normalize().multiply(1.1).setY(0.5));
                    }
                    cancel();
                    return;
                }
                t += 2;
            }
        }.runTaskTimer(this, 0L, 2L);
        return true;
    }

    // ---- CHARGE : fonce sur le joueur ----
    private boolean charge(Mob mob, Player target, Attack a, double dmg) {
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.8f);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();
            Vector dir;
            @Override public void run() {
                if (!mob.isValid()) { cancel(); return; }
                Location c = mob.getLocation().add(0, 1, 0);
                World w = c.getWorld();
                if (t < 12) {
                    w.spawnParticle(a.particle, c, 12, 0.5, 0.8, 0.5, 0.05);
                } else if (t == 12) {
                    dir = target.getLocation().toVector().subtract(mob.getLocation().toVector());
                    dir.setY(0);
                    if (dir.lengthSquared() < 0.01) { cancel(); return; }
                    dir.normalize();
                    mob.setVelocity(dir.clone().multiply(1.9).setY(0.2));
                    w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.7f);
                } else {
                    w.spawnParticle(Particle.SWEEP_ATTACK, c, 2, 0.4, 0.3, 0.4, 0);
                    w.spawnParticle(a.particle, c, 8, 0.4, 0.5, 0.4, 0.2);
                    for (Player p : w.getPlayers()) {
                        if (hit.contains(p.getUniqueId()) || !hittable(p)) continue;
                        if (p.getLocation().add(0, 1, 0).distance(c) > 2.5) continue;
                        hit.add(p.getUniqueId());
                        applyHit(p, a, mob, dmg);
                        p.setVelocity(dir.clone().multiply(1.2).setY(0.4));
                        w.spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.1);
                        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 1f);
                    }
                    if (t > 22) { cancel(); return; }
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ---- BEHIND : téléportation dans le dos ----
    private boolean behind(Mob mob, Player target, Attack a, double dmg) {
        Location from = mob.getLocation().add(0, 1, 0);
        from.getWorld().spawnParticle(Particle.SMOKE, from, 40, 0.4, 0.8, 0.4, 0.05);
        from.getWorld().spawnParticle(a.particle, from, 25, 0.4, 0.8, 0.4, 0.05);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.6f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!mob.isValid() || !target.isOnline() || target.isDead()) return;
            Location tl = target.getLocation();
            Location b = tl.clone().add(tl.getDirection().setY(0).normalize().multiply(-1.6));
            b.setDirection(tl.toVector().subtract(b.toVector()));
            mob.teleport(b);
            World w = b.getWorld();
            w.spawnParticle(Particle.SMOKE, b.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
            w.spawnParticle(a.particle, b.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
            w.playSound(b, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.4f);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!mob.isValid() || !target.isOnline() || target.isDead()) return;
                Location eye = target.getLocation().add(0, 1, 0);
                if (eye.distance(mob.getLocation()) > 3.8) return;
                World w2 = eye.getWorld();
                w2.spawnParticle(Particle.SWEEP_ATTACK, eye, 3, 0.5, 0.3, 0.5, 0);
                w2.spawnParticle(Particle.DAMAGE_INDICATOR, eye, 12, 0.3, 0.5, 0.3, 0.1);
                w2.spawnParticle(Particle.CRIT, eye, 20, 0.4, 0.5, 0.4, 0.3);
                w2.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
                applyHit(target, a, mob, dmg);
            }, 8L);
        }, 12L);
        return true;
    }

    // ---- CONE : souffle devant le mob ----
    private boolean cone(Mob mob, Player target, Attack a, double dmg) {
        Vector dir = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (dir.lengthSquared() < 0.01) return false;
        dir.normalize();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.7f);
        new BukkitRunnable() {
            int t = 0;
            final Set<UUID> hit = new HashSet<>();
            @Override public void run() {
                if (!mob.isValid()) { cancel(); return; }
                Location s = mob.getEyeLocation();
                World w = s.getWorld();
                if (t < 10) {
                    w.spawnParticle(a.particle, s, 8, 0.4, 0.4, 0.4, 0.02);
                } else {
                    for (double k = 1; k <= a.radius; k += 0.8) {
                        Location pt = s.clone().add(dir.clone().multiply(k));
                        double spread = k * 0.12;
                        w.spawnParticle(a.particle, pt, 4, spread, spread, spread, 0.01);
                        w.spawnParticle(Particle.CLOUD, pt, 1, spread, spread, spread, 0.01);
                    }
                    for (Player p : w.getPlayers()) {
                        if (hit.contains(p.getUniqueId()) || !hittable(p)) continue;
                        Vector to = p.getEyeLocation().toVector().subtract(s.toVector());
                        double d = to.length();
                        if (d > a.radius || d < 0.1) continue;
                        if (to.normalize().dot(dir) < 0.88) continue;
                        hit.add(p.getUniqueId());
                        applyHit(p, a, mob, dmg);
                    }
                    if (t >= 24) { cancel(); return; }
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ---- ZONE : nuage persistant sur la position du joueur ----
    private boolean zone(Mob mob, Player target, Attack a, double dmg) {
        Location center = target.getLocation().clone();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 2f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                World w = center.getWorld();
                if (t < 20) {
                    ringParticles(center, a.radius, a.particle, 24);
                } else {
                    if (t % 2 == 0) {
                        for (int i = 0; i < 10; i++) {
                            w.spawnParticle(a.particle, center.clone().add(
                                    rnd.nextDouble() * a.radius * 2 - a.radius, rnd.nextDouble() * 1.5,
                                    rnd.nextDouble() * a.radius * 2 - a.radius), 2, 0.1, 0.1, 0.1, 0);
                        }
                    }
                    if (t % 10 == 0) {
                        for (Player p : w.getPlayers()) {
                            if (!hittable(p) || p.getLocation().distance(center) > a.radius + 0.2) continue;
                            applyHit(p, a, mob, dmg);
                        }
                    }
                    if (t >= 100) { cancel(); return; }
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ---- LEAP : bond puis choc au sol ----
    private boolean leap(Mob mob, Player target, Attack a, double dmg) {
        Vector d = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        d.setY(0);
        if (d.lengthSquared() > 0.01) d.normalize();
        mob.setVelocity(d.multiply(0.8).setY(1.1));
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.8f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!mob.isValid()) { cancel(); return; }
                Location c = mob.getLocation();
                World w = c.getWorld();
                if (t < 18) {
                    if (t % 3 == 0) w.spawnParticle(Particle.ANGRY_VILLAGER, c.clone().add(0, 2, 0), 3, 0.4, 0.3, 0.4, 0);
                } else {
                    for (double r = 1; r <= a.radius; r += 1) ringParticles(c, r, a.particle, (int) (r * 8));
                    w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 5, 2, 0.3, 2, 0);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                    for (Player p : w.getPlayers()) {
                        if (!hittable(p) || p.getLocation().distance(c) > a.radius) continue;
                        applyHit(p, a, mob, dmg);
                        Vector kb = p.getLocation().toVector().subtract(c.toVector());
                        if (kb.lengthSquared() < 0.01) kb = new Vector(0, 0, 0); else kb.normalize();
                        p.setVelocity(kb.multiply(0.8).setY(0.9));
                    }
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ---- VORTEX : aspire puis écrase ----
    private boolean vortex(Mob mob, Attack a, double dmg) {
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 2f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!mob.isValid()) { cancel(); return; }
                Location c = mob.getLocation();
                World w = c.getWorld();
                if (t < 24) {
                    for (int i = 0; i < 3; i++) {
                        double ang = t * 0.5 + i * 2.09;
                        double r = 8 - t * 0.3;
                        Location l = c.clone().add(Math.cos(ang) * r, 0.3 + t * 0.05, Math.sin(ang) * r);
                        w.spawnParticle(a.particle, l, 2, 0.05, 0.05, 0.05, 0);
                        w.spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
                    }
                    for (Player p : w.getPlayers()) {
                        if (!hittable(p) || p.getLocation().distance(c) > 10) continue;
                        Vector pull = c.toVector().subtract(p.getLocation().toVector());
                        if (pull.length() > 1.5) p.setVelocity(pull.normalize().multiply(0.35));
                    }
                } else {
                    ringParticles(c, 3, Particle.CLOUD, 24);
                    w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 1, 0), 3, 1, 0.3, 1, 0);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                    for (Player p : w.getPlayers()) {
                        if (!hittable(p) || p.getLocation().distance(c) > a.radius) continue;
                        applyHit(p, a, mob, dmg);
                    }
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ---- SPIN : tourbillon, 3 coups ----
    private boolean spin(Mob mob, Attack a, double dmg) {
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 2f, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (!mob.isValid()) { cancel(); return; }
                Location c = mob.getLocation();
                World w = c.getWorld();
                for (int k = 0; k < 6; k++) {
                    double ang = n * 0.8 + k * Math.PI / 3;
                    Location l = c.clone().add(Math.cos(ang) * 2.5, 1, Math.sin(ang) * 2.5);
                    w.spawnParticle(n < 6 ? Particle.CRIT : a.particle, l, 1, 0, 0, 0, 0);
                }
                if (n == 6 || n == 10 || n == 14) {
                    w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.8f);
                    for (Player p : w.getPlayers()) {
                        if (!hittable(p) || p.getLocation().distance(c) > a.radius) continue;
                        applyHit(p, a, mob, dmg);
                        Vector kb = p.getLocation().toVector().subtract(c.toVector());
                        if (kb.lengthSquared() > 0.01) p.setVelocity(kb.normalize().multiply(0.5).setY(0.25));
                    }
                }
                if (n >= 14) { cancel(); return; }
                n++;
            }
        }.runTaskTimer(this, 0L, 2L);
        return true;
    }

    // ---- VOLLEY : salve de projectiles ----
    private boolean volley(Mob mob, Player target, Attack a, double dmg) {
        mob.getWorld().playSound(mob.getLocation(), a.ignite > 0 ? Sound.ENTITY_BLAZE_SHOOT : Sound.ENTITY_SKELETON_SHOOT, 2f, 0.8f);
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (!mob.isValid() || !target.isOnline() || i >= a.count) { cancel(); return; }
                Location eye = mob.getEyeLocation();
                Vector v = target.getEyeLocation().toVector().subtract(eye.toVector());
                if (v.lengthSquared() < 0.01) { cancel(); return; }
                v.normalize().add(new Vector((rnd.nextDouble() - 0.5) * 0.12,
                        (rnd.nextDouble() - 0.5) * 0.08, (rnd.nextDouble() - 0.5) * 0.12)).normalize();
                if (a.ignite > 0) {
                    SmallFireball f = mob.launchProjectile(SmallFireball.class, v.clone().multiply(1.2));
                    f.setIsIncendiary(false);
                } else {
                    Arrow ar = mob.launchProjectile(Arrow.class, v.clone().multiply(2.5));
                    ar.setDamage(dmg / 2.5);
                    ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                }
                eye.getWorld().spawnParticle(a.particle, eye, 6, 0.2, 0.2, 0.2, 0.02);
                i++;
            }
        }.runTaskTimer(this, 0L, 4L);
        return true;
    }

    // ---- STRIKES : plusieurs impacts autour du joueur ----
    private boolean strikes(Mob mob, Player target, Attack a, double dmg) {
        World w = target.getWorld();
        Location base = target.getLocation().clone();
        List<Location> pts = new ArrayList<>();
        for (int i = 0; i < a.count; i++) {
            pts.add(i == 0 ? base.clone()
                    : base.clone().add(rnd.nextDouble() * 8 - 4, 0, rnd.nextDouble() * 8 - 4));
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 2f, 0.8f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                for (int i = 0; i < pts.size(); i++) {
                    int rel = t - i * 6;
                    Location l = pts.get(i);
                    if (rel >= 0 && rel < 20 && rel % 4 == 0) {
                        ringParticles(l, a.radius, a.particle, 16);
                    } else if (rel == 20) {
                        w.spawnParticle(a.particle, l.clone().add(0, 1, 0), 40, a.radius * 0.4, 1, a.radius * 0.4, 0.05);
                        w.spawnParticle(Particle.EXPLOSION, l.clone().add(0, 0.5, 0), 1);
                        if (a.particle == Particle.ELECTRIC_SPARK) w.strikeLightningEffect(l);
                        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);
                        for (Player p : w.getPlayers()) {
                            if (!hittable(p) || p.getLocation().distance(l) > a.radius + 0.6) continue;
                            applyHit(p, a, mob, dmg);
                        }
                    }
                }
                if (t > (pts.size() - 1) * 6 + 21) { cancel(); return; }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ---- FRENZY : le mob se renforce ----
    private boolean frenzy(Mob mob) {
        if (mob.hasPotionEffect(PotionEffectType.STRENGTH)) return false;
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 1));
        Location c = mob.getLocation().add(0, 1.5, 0);
        c.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, c, 12, 0.5, 0.6, 0.5, 0);
        c.getWorld().playSound(c, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 1.2f);
        return true;
    }

    // ---- SUMMON : le boss invoque une horde ----
    private boolean summonAttack(Mob boss, Player target, int phase) {
        Set<UUID> set = minions.computeIfAbsent(boss.getUniqueId(), k -> new HashSet<>());
        set.removeIf(id -> {
            Entity m = Bukkit.getEntity(id);
            return m == null || !m.isValid();
        });
        if (set.size() >= MAX_MINIONS) return false;
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!boss.isValid()) { cancel(); return; }
                Location c = boss.getLocation();
                if (t < 30) {
                    ringParticles(c, 4, Particle.SOUL_FIRE_FLAME, 24);
                    c.getWorld().spawnParticle(Particle.WITCH, c.clone().add(0, 2.5, 0), 6, 0.5, 0.3, 0.5, 0);
                } else {
                    spawnWave(boss, phase, 3 + phase * 2, target);
                    cancel();
                    return;
                }
                t++;
            }
        }.runTaskTimer(this, 0L, 1L);
        return true;
    }

    // ====== MENU DES OEUFS (/mobegg) ======
    static class EggMenu implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(c(" "));
        pane.setItemMeta(pm);
        return pane;
    }

    private void openEggMenu(Player p) {
        EggMenu m = new EggMenu();
        m.inv = Bukkit.createInventory(m, 27, c("§8Oeufs de mobs custom"));
        for (int i = 0; i < 27; i++) m.inv.setItem(i, pane());
        Type[] types = Type.values();
        for (int i = 0; i < types.length; i++) {
            Type t = types[i];
            ItemStack egg = new ItemStack(t.egg);
            ItemMeta meta = egg.getItemMeta();
            meta.displayName(c(t.color + t.label));
            List<Component> lore = new ArrayList<>();
            lore.add(c("§7" + t.power));
            lore.add(c(""));
            lore.add(c("§fVie : §c" + (int) t.health + " §7| §fDégâts : §c" + (int) t.damage + " §7| §fArmure : §b" + (int) t.armor));
            lore.add(c("§fAttaques spéciales :"));
            for (Attack a : t.attacks) lore.add(c("§8 - §e" + a.name));
            lore.add(c(""));
            lore.add(c("§aClic : §f1 oeuf  §7| §aShift + clic : §f16 oeufs"));
            meta.lore(lore);
            egg.setItemMeta(meta);
            m.inv.setItem(9 + i, egg);
        }
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onEggMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof EggMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) { p.closeInventory(); return; }
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int idx = e.getSlot() - 9;
        Type[] types = Type.values();
        if (idx < 0 || idx >= types.length) return;
        p.getInventory().addItem(createEgg(types[idx], e.isShiftClick() ? 16 : 1));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
    }

    // ====== VAULT (/mobvault) ======
    static class VaultMenu implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private double diffMult(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 0.6;
            case EASY -> 0.8;
            case NORMAL -> 1.0;
            case HARD -> 1.4;
        };
    }

    private int diffRolls(Difficulty d) {
        return switch (d) {
            case PEACEFUL, EASY -> 1;
            case NORMAL -> 2;
            case HARD -> 3;
        };
    }

    private String diffName(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> "§aPaisible";
            case EASY -> "§aFacile";
            case NORMAL -> "§eNormal";
            case HARD -> "§cDifficile";
        };
    }

    private void openVault(Player p) {
        VaultMenu m = new VaultMenu();
        m.inv = Bukkit.createInventory(m, 27, c("§8Vault des boss"));
        fillVault(m.inv, p);
        p.openInventory(m.inv);
    }

    private void fillVault(Inventory inv, Player p) {
        Difficulty d = p.getWorld().getDifficulty();
        double mult = diffMult(d);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane());
        Type[] types = Type.values();
        for (int i = 0; i < types.length; i++) {
            Type t = types[i];
            ItemStack it = new ItemStack(Material.TRIAL_KEY);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(c(t.color + "Vault : " + t.label));
            List<Component> lore = new ArrayList<>();
            lore.add(c("§7Difficulté du monde : " + diffName(d)));
            lore.add(c("§7Tirages : §f" + diffRolls(d) + " §7| Bonus de chance : §fx" + mult));
            lore.add(c("§7Tes clés : §f" + countKeys(p, t)));
            lore.add(c(""));
            lore.add(c("§7Récompenses possibles :"));
            for (Loot l : Loot.of(t)) {
                lore.add(c("§8 - " + l.displayName() + " §7(" + (int) Math.min(100, l.chance * mult) + "%)"));
            }
            lore.add(c(""));
            lore.add(c("§aClic : utiliser 1 clé"));
            meta.lore(lore);
            it.setItemMeta(meta);
            inv.setItem(9 + i, it);
        }
    }

    private List<ItemStack> rollVault(Type t, Difficulty d) {
        double mult = diffMult(d);
        List<ItemStack> out = new ArrayList<>();
        for (int r = 0; r < diffRolls(d); r++) {
            for (Loot l : Loot.of(t)) {
                if (rnd.nextDouble() * 100 < Math.min(100, l.chance * mult)) out.add(l.create(rnd));
            }
        }
        if (out.isEmpty()) out.add(Loot.of(t).get(0).create(rnd));
        return out;
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler
    public void onVaultClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof VaultMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int idx = e.getSlot() - 9;
        Type[] types = Type.values();
        if (idx < 0 || idx >= types.length) return;
        Type t = types[idx];
        if (!consumeKey(p, t)) {
            p.sendMessage(c("§cTu n'as pas de clé : " + t.color + t.label));
            return;
        }
        List<ItemStack> loot = rollVault(t, p.getWorld().getDifficulty());
        StringBuilder sb = new StringBuilder("§aVault ouvert ! §7Tu obtiens : ");
        for (ItemStack it : loot) {
            give(p, it);
            sb.append("§f").append(it.getAmount()).append("x ").append(it.getType().name().toLowerCase().replace('_', ' ')).append("§7, ");
        }
        p.sendMessage(c(sb.toString()));
        p.playSound(p.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 1f, 1f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.5, 0), 25, 0.5, 0.5, 0.5, 0.05);
        fillVault(e.getView().getTopInventory(), p);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof EggMenu || h instanceof VaultMenu || h instanceof SpawnerMenu) e.setCancelled(true);
    }

    // ====== SPAWNERS (/mobspawner) ======
    private static String blockKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private void loadSpots() {
        if (!spotsFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(spotsFile);
        for (String line : cfg.getStringList("spots")) {
            String[] p = line.split(";");
            if (p.length < 6) continue;
            Type t = Type.byId(p[5]);
            if (t == null) continue;
            try {
                Spot s = new Spot(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], t);
                spots.put(s.key(), s);
            } catch (NumberFormatException ignored) { }
        }
    }

    private void saveSpots() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Spot s : spots.values()) list.add(s.key() + ";" + s.kind + ";" + s.type.id);
        cfg.set("spots", list);
        try {
            getDataFolder().mkdirs();
            cfg.save(spotsFile);
        } catch (IOException ex) {
            getLogger().warning("Impossible de sauvegarder spawners.yml : " + ex.getMessage());
        }
    }

    // ====== ITEMS DE SPAWNER + MENU (/mobspawner) ======
    static class SpawnerMenu implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    /** kind = "garde" ou "epreuve". 9 boss x 2 sortes = 18 spawners. */
    private ItemStack createSpawnerItem(String kind, Type t, int amount) {
        boolean guard = kind.equals("garde");
        ItemStack it = new ItemStack(Material.SPAWNER, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c((guard ? "§eSpawner de gardes : " : "§6Spawner d'épreuve : ") + t.color + t.label));
        List<Component> lore = new ArrayList<>();
        if (guard) {
            lore.add(c("§7Fait apparaître jusqu'à " + GUARD_MAX + " gardes quand un joueur approche."));
            lore.add(c("§7Les gardes peuvent lâcher une clé de ce boss."));
        } else {
            lore.add(c("§7Clic droit : 3 vagues de gardes, puis le boss."));
            lore.add(c("§7Le boss lâche 1 à 5 clés et son butin."));
        }
        lore.add(c("§8Pose-le où tu veux (op uniquement)."));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(spawnerKey, PersistentDataType.STRING, kind + ":" + t.id);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack labelItem(Material m, String name, String... lines) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        List<Component> lore = new ArrayList<>();
        for (String l : lines) lore.add(c(l));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void openSpawnerMenu(Player p) {
        SpawnerMenu m = new SpawnerMenu();
        m.inv = Bukkit.createInventory(m, 45, c("§8Spawners de boss"));
        for (int i = 0; i < 45; i++) m.inv.setItem(i, pane());
        m.inv.setItem(4, labelItem(Material.SHIELD, "§eSpawners de gardes",
                "§7Des gardes réapparaissent tant que", "§7des joueurs sont à proximité."));
        m.inv.setItem(22, labelItem(Material.TRIAL_SPAWNER, "§6Spawners d'épreuve",
                "§7Clic droit sur le spawner :", "§73 vagues de gardes puis le boss."));
        Type[] types = Type.values();
        for (int i = 0; i < types.length; i++) {
            m.inv.setItem(9 + i, createSpawnerItem("garde", types[i], 1));
            m.inv.setItem(27 + i, createSpawnerItem("epreuve", types[i], 1));
        }
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onSpawnerMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof SpawnerMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.isOp()) { p.closeInventory(); return; }
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        String kind;
        int idx;
        if (slot >= 9 && slot <= 17) { kind = "garde"; idx = slot - 9; }
        else if (slot >= 27 && slot <= 35) { kind = "epreuve"; idx = slot - 27; }
        else return;
        Type[] types = Type.values();
        if (idx >= types.length) return;
        give(p, createSpawnerItem(kind, types[idx], e.isShiftClick() ? 16 : 1));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
    }

    /** Quand on pose un spawner du plugin, il est enregistré et configuré. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (hand == null || !hand.hasItemMeta()) return;
        String tag = hand.getItemMeta().getPersistentDataContainer().get(spawnerKey, PersistentDataType.STRING);
        if (tag == null) return;
        if (!e.getPlayer().isOp()) { e.setCancelled(true); return; }
        String[] part = tag.split(":");
        Type t = Type.byId(part.length > 1 ? part[1] : null);
        if (t == null) return;
        Block b = e.getBlockPlaced();
        if (b.getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(t.entity);   // le mob du boss tourne dans la cage
                cs.setSpawnCount(0);           // mais le spawner vanilla ne fait rien :
                cs.setMaxNearbyEntities(0);    // c'est le plugin qui gère tout
            } catch (RuntimeException ignored) { }
            cs.update();
        }
        Spot sp = new Spot(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), part[0], t);
        spots.put(sp.key(), sp);
        saveSpots();
        e.getPlayer().sendMessage(c("§aSpawner " + (part[0].equals("garde") ? "de gardes" : "d'épreuve")
                + " posé : " + t.color + t.label));
    }

    private void removeSpot(String key) {
        Spot s = spots.remove(key);
        if (s == null) return;
        Trial tr = trials.remove(key);
        if (tr != null) cleanupTrial(tr);
        Set<UUID> g = guardSets.remove(key);
        if (g != null) for (UUID id : g) { Entity en = Bukkit.getEntity(id); if (en != null) en.remove(); }
        saveSpots();
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent e) {
        String key = blockKey(e.getBlock());
        Spot sp = spots.get(key);
        if (sp == null) return;
        if (!e.getPlayer().isOp()) { e.setCancelled(true); return; }
        removeSpot(key);
        e.setDropItems(false);
        e.setExpToDrop(0);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5),
                createSpawnerItem(sp.kind, sp.type, 1));
        e.getPlayer().sendMessage(c("§eSpawner retiré (l'objet est tombé au sol)."));
    }

    @EventHandler
    public void onSpawnerUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.SPAWNER) return;
        Spot s = spots.get(blockKey(b));
        if (s == null) return;
        e.setCancelled(true);
        if (!s.kind.equals("epreuve")) {
            e.getPlayer().sendMessage(c("§7Ce spawner fait apparaître des gardes de " + s.type.color + s.type.label + "§7."));
            return;
        }
        startTrial(e.getPlayer(), s);
    }

    private void startTrial(Player p, Spot s) {
        String key = s.key();
        if (trials.containsKey(key)) { p.sendMessage(c("§eL'épreuve est déjà en cours !")); return; }
        long now = System.currentTimeMillis();
        Long cd = trialCooldown.get(key);
        if (cd != null && cd > now) {
            p.sendMessage(c("§cL'épreuve se recharge : encore " + ((cd - now) / 1000 + 1) + "s."));
            return;
        }
        trials.put(key, new Trial());
        broadcastNear(spotLoc(s), 40, "§6⚔ Épreuve : " + s.type.color + s.type.label + " §6- 3 vagues puis le boss !");
    }

    private Location spotLoc(Spot s) {
        World w = Bukkit.getWorld(s.world);
        return w == null ? null : new Location(w, s.x + 0.5, s.y, s.z + 0.5);
    }

    private void broadcastNear(Location l, double radius, String msg) {
        if (l == null) return;
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= radius * radius) p.sendMessage(c(msg));
        }
    }

    private void cleanupTrial(Trial tr) {
        for (UUID id : tr.alive) { Entity en = Bukkit.getEntity(id); if (en != null) en.remove(); }
        if (tr.boss != null) { Entity en = Bukkit.getEntity(tr.boss); if (en != null) en.remove(); }
    }

    private void spawnerTick() {
        for (Spot s : new ArrayList<>(spots.values())) {
            World w = Bukkit.getWorld(s.world);
            if (w == null || !w.isChunkLoaded(s.x >> 4, s.z >> 4)) continue;
            Location sl = new Location(w, s.x + 0.5, s.y, s.z + 0.5);
            if (s.kind.equals("garde")) guardTick(s, sl); else trialTick(s, sl);
        }
    }

    private void guardTick(Spot s, Location sl) {
        World w = sl.getWorld();
        w.spawnParticle(Particle.SMOKE, sl.clone().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0.01);
        boolean near = false;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(sl) <= 24 * 24) { near = true; break; }
        }
        if (!near) return;
        String key = s.key();
        Set<UUID> set = guardSets.computeIfAbsent(key, k -> new HashSet<>());
        set.removeIf(id -> { Entity en = Bukkit.getEntity(id); return en == null || !en.isValid(); });
        long now = System.currentTimeMillis();
        if (set.size() < GUARD_MAX && now >= nextGuard.getOrDefault(key, 0L)) {
            Location l = sl.clone().add(rnd.nextDouble() * 6 - 3, 0, rnd.nextDouble() * 6 - 3);
            LivingEntity g = spawnMob(s.type, l, true);
            if (g != null) set.add(g.getUniqueId());
            nextGuard.put(key, now + GUARD_DELAY_MS);
        }
    }

    private void trialTick(Spot s, Location sl) {
        World w = sl.getWorld();
        String key = s.key();
        Trial t = trials.get(key);
        if (t == null) {
            w.spawnParticle(Particle.END_ROD, sl.clone().add(0, 1.3, 0), 3, 0.3, 0.3, 0.3, 0.01);
            return;
        }
        long now = System.currentTimeMillis();
        boolean near = false;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(sl) <= 40 * 40) { near = true; break; }
        }
        if (near) t.lastPlayer = now;
        if ((!near && now - t.lastPlayer > 30000) || now - t.start > TRIAL_TIMEOUT_MS) {
            cleanupTrial(t);
            trials.remove(key);
            broadcastNear(sl, 60, "§cL'épreuve a été annulée.");
            return;
        }
        t.alive.removeIf(id -> { Entity en = Bukkit.getEntity(id); return en == null || !en.isValid(); });

        if (t.stage <= 3) {
            if (!t.alive.isEmpty()) return;
            if (t.stage == 3) {
                t.stage = 4;
                LivingEntity boss = spawnMob(s.type, sl.clone().add(0, 0.1, 0), false);
                if (boss != null) t.boss = boss.getUniqueId();
                broadcastNear(sl, 40, "§c☠ Le boss apparaît : " + s.type.color + s.type.label);
            } else {
                t.stage++;
                int n = 2 + t.stage * 2;
                for (int i = 0; i < n; i++) {
                    double ang = 2 * Math.PI * i / n;
                    Location l = sl.clone().add(Math.cos(ang) * 4, 0, Math.sin(ang) * 4);
                    LivingEntity g = spawnMob(s.type, l, true);
                    if (g != null) t.alive.add(g.getUniqueId());
                }
                broadcastNear(sl, 40, "§6Vague " + t.stage + "/3 !");
            }
        } else {
            Entity b = t.boss == null ? null : Bukkit.getEntity(t.boss);
            if (b == null || !b.isValid()) {
                trials.remove(key);
                trialCooldown.put(key, now + TRIAL_COOLDOWN_MS);
                broadcastNear(sl, 60, "§a✔ Épreuve réussie ! Récupère tes clés et utilise /mobvault.");
            }
        }
    }

    // ====== COMMANDES ======
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "mobvault": return vaultCommand(sender, args);
            case "mobspawner": return spawnerCommand(sender, args);
            default: return eggCommand(sender, args);
        }
    }

    private boolean vaultCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (!sender.isOp()) { sender.sendMessage(c("§cRéservé aux opérateurs.")); return true; }
            if (args.length < 3) { sender.sendMessage(c("§c/mobvault give <joueur> <type> [nombre]")); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            Type t = Type.byId(args[2]);
            if (target == null || t == null) { sender.sendMessage(c("§cJoueur ou type inconnu.")); return true; }
            int n = 1;
            if (args.length >= 4) {
                try { n = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
                catch (NumberFormatException ex) { sender.sendMessage(c("§cNombre invalide.")); return true; }
            }
            give(target, createKey(t, n));
            sender.sendMessage(c("§a" + n + " clé(s) " + t.label + " données à " + target.getName() + "."));
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(c("§cCommande réservée aux joueurs.")); return true; }
        openVault(p);
        return true;
    }

    private boolean spawnerCommand(CommandSender sender, String[] args) {
        if (!sender.isOp()) { sender.sendMessage(c("§cCommande réservée aux opérateurs.")); return true; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            if (spots.isEmpty()) sender.sendMessage(c("§7Aucun spawner."));
            for (Spot sp : spots.values()) {
                sender.sendMessage(c("§e" + sp.kind + " §7- " + sp.type.color + sp.type.label
                        + " §7(" + sp.world + " " + sp.x + " " + sp.y + " " + sp.z + ")"));
            }
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(c("§cCommande réservée aux joueurs.")); return true; }
        if (args.length == 0) { openSpawnerMenu(p); return true; }
        if (args[0].equalsIgnoreCase("remove")) {
            Block b = p.getTargetBlockExact(8);
            if (b == null || !spots.containsKey(blockKey(b))) { p.sendMessage(c("§cRegarde un spawner du plugin.")); return true; }
            Spot sp = spots.get(blockKey(b));
            removeSpot(blockKey(b));
            b.setType(Material.AIR);
            give(p, createSpawnerItem(sp.kind, sp.type, 1));
            p.sendMessage(c("§eSpawner retiré (l'objet est dans ton inventaire)."));
            return true;
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("garde") || args[0].equalsIgnoreCase("epreuve"))) {
            Type t = Type.byId(args[1]);
            if (t == null) { p.sendMessage(c("§cType inconnu : flame, storm, shadow, frost, venom, berserk, abyss, brute, necro")); return true; }
            give(p, createSpawnerItem(args[0].toLowerCase(), t, 1));
            p.sendMessage(c("§aSpawner reçu. Pose-le où tu veux."));
            return true;
        }
        p.sendMessage(c("§e/mobspawner §7(menu des 18 spawners) §8| §e/mobspawner garde|epreuve <type>"));
        p.sendMessage(c("§e/mobspawner remove §7(regarde un spawner) §8| §e/mobspawner list"));
        return true;
    }

    private boolean eggCommand(CommandSender sender, String[] args) {
        if (!sender.isOp()) { sender.sendMessage(c("§cCette commande est réservée aux opérateurs.")); return true; }
        if (args.length == 0 && sender instanceof Player menuPlayer) { openEggMenu(menuPlayer); return true; }
        if (args.length >= 1 && Type.byId(args[0]) != null) {
            if (!(sender instanceof Player self)) { sender.sendMessage(c("§cUtilise /mobegg give <joueur> <type>.")); return true; }
            int n = 1;
            if (args.length >= 2) {
                try { n = Math.max(1, Math.min(64, Integer.parseInt(args[1]))); }
                catch (NumberFormatException ex) { sender.sendMessage(c("§cNombre invalide.")); return true; }
            }
            Type t = Type.byId(args[0]);
            give(self, createEgg(t, n));
            self.sendMessage(c("§a" + n + " oeuf(s) " + t.label + " ajouté(s) à ton inventaire."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            for (Type t : Type.values()) {
                sender.sendMessage(c(t.color + t.id + " §7- " + t.label + " : " + t.power));
            }
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            Type t = Type.byId(args[2]);
            if (target == null) { sender.sendMessage(c("§cJoueur introuvable.")); return true; }
            if (t == null) { sender.sendMessage(c("§cType inconnu. Fais /mobegg list")); return true; }
            int n = 1;
            if (args.length >= 4) {
                try { n = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
                catch (NumberFormatException ex) { sender.sendMessage(c("§cNombre invalide.")); return true; }
            }
            give(target, createEgg(t, n));
            sender.sendMessage(c("§a" + n + " oeuf(s) " + t.label + " donné(s) à " + target.getName() + "."));
            return true;
        }
        sender.sendMessage(c("§e/mobegg §7(menu) | §e/mobegg <type> [nombre] §7| §e/mobegg give <joueur> <type> [nombre] §7| §e/mobegg list"));
        return true;
    }
}
