package fr.mobs;

import fr.mobs.Attack.Kind;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

/** Les 9 mobs custom. Chacun a 3 à 4 attaques spéciales. */
enum Type {

    FLAME("flame", "Gardien des Flammes", "§6", EntityType.HUSK, Material.HUSK_SPAWN_EGG,
            260, 14, 0.27, 10, 1.2, 16,
            Material.BLACKSTONE, Material.NETHER_BRICKS, Material.SHROOMLIGHT,
            "Maître du feu : nova, boules de feu, météores et rage.",
            new Attack(Kind.RING, "Nova de feu", 0, 7, 1.0).part(Particle.FLAME).burn(120).rad(6),
            new Attack(Kind.VOLLEY, "Boules de feu", 4, 16, 0.5).part(Particle.FLAME).burn(1).num(4),
            new Attack(Kind.STRIKES, "Pluie de météores", 4, 16, 0.9).part(Particle.FLAME).burn(80).num(4).rad(2.5),
            new Attack(Kind.FRENZY, "Rage ardente", 0, 12, 0)),

    STORM("storm", "Chevalier de la Tempête", "§b", EntityType.VINDICATOR, Material.VINDICATOR_SPAWN_EGG,
            230, 16, 0.29, 12, 1.15, 18,
            Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.SEA_LANTERN,
            "Maître de la foudre : charge, éclairs, tonnerre et surcharge.",
            new Attack(Kind.CHARGE, "Charge éclair", 4, 14, 1.0).part(Particle.ELECTRIC_SPARK),
            new Attack(Kind.STRIKES, "Foudre", 3, 16, 0.9).part(Particle.ELECTRIC_SPARK).burn(40).num(4).rad(2.2),
            new Attack(Kind.RING, "Onde de tonnerre", 0, 7, 0.8).part(Particle.ELECTRIC_SPARK).rad(6),
            new Attack(Kind.FRENZY, "Surcharge", 0, 12, 0)),

    SHADOW("shadow", "Spectre des Ombres", "§5", EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SPAWN_EGG,
            200, 18, 0.31, 10, 1.1, 20,
            Material.DEEPSLATE_TILES, Material.POLISHED_BLACKSTONE_BRICKS, Material.SOUL_LANTERN,
            "Maître des ombres : téléportation, puits, cri et voile.",
            new Attack(Kind.BEHIND, "Frappe d'ombre", 3, 16, 1.0).part(Particle.SOUL_FIRE_FLAME)
                    .fx(PotionEffectType.BLINDNESS, 80, 0),
            new Attack(Kind.ZONE, "Puits d'ombre", 3, 14, 0.4).part(Particle.SOUL_FIRE_FLAME).rad(3.5)
                    .fx(PotionEffectType.SLOWNESS, 60, 1),
            new Attack(Kind.RING, "Cri de l'ombre", 0, 6, 0.7).part(Particle.SMOKE).rad(6)
                    .fx(PotionEffectType.BLINDNESS, 80, 0),
            new Attack(Kind.FRENZY, "Voile d'ombre", 0, 12, 0)),

    FROST("frost", "Seigneur du Givre", "§3", EntityType.STRAY, Material.STRAY_SPAWN_EGG,
            180, 10, 0.27, 8, 1.15, 14,
            Material.PACKED_ICE, Material.BLUE_ICE, Material.SEA_LANTERN,
            "Maître du froid : souffle, salve, stalactites et blizzard.",
            new Attack(Kind.CONE, "Souffle glacial", 0, 12, 1.0).part(Particle.SNOWFLAKE).rad(10)
                    .fx(PotionEffectType.SLOWNESS, 100, 2),
            new Attack(Kind.VOLLEY, "Salve de glace", 5, 18, 0.6).part(Particle.SNOWFLAKE).num(5),
            new Attack(Kind.STRIKES, "Stalactites", 3, 16, 0.9).part(Particle.SNOWFLAKE).num(4).rad(2.2)
                    .fx(PotionEffectType.SLOWNESS, 60, 1),
            new Attack(Kind.RING, "Blizzard", 0, 7, 0.8).part(Particle.SNOWFLAKE).rad(6)
                    .fx(PotionEffectType.SLOWNESS, 80, 2)),

    VENOM("venom", "Reine Venimeuse", "§2", EntityType.SPIDER, Material.SPIDER_SPAWN_EGG,
            200, 14, 0.36, 6, 1.6, 14,
            Material.MOSS_BLOCK, Material.MOSSY_STONE_BRICKS, Material.VERDANT_FROGLIGHT,
            "Maîtresse du poison : nuée, bond, spores et ruée.",
            new Attack(Kind.ZONE, "Nuée toxique", 3, 14, 0.35).part(Particle.WITCH).rad(4.2)
                    .fx(PotionEffectType.POISON, 100, 1),
            new Attack(Kind.LEAP, "Bond venimeux", 3, 12, 1.0).part(Particle.WITCH).rad(4)
                    .fx(PotionEffectType.POISON, 100, 1),
            new Attack(Kind.RING, "Spores", 0, 6, 0.7).part(Particle.WITCH).rad(5)
                    .fx(PotionEffectType.POISON, 120, 1),
            new Attack(Kind.CHARGE, "Ruée", 4, 12, 0.9).part(Particle.WITCH)
                    .fx(PotionEffectType.POISON, 80, 1)),

    BERSERK("berserk", "Berserker Dévastateur", "§c", EntityType.RAVAGER, Material.RAVAGER_SPAWN_EGG,
            380, 22, 0.30, 12, 1.2, 22,
            Material.RED_SANDSTONE, Material.RED_NETHER_BRICKS, Material.GLOWSTONE,
            "Brute enragée : fracas, tremblement, charge et rage.",
            new Attack(Kind.LEAP, "Fracas sismique", 3, 12, 1.0).part(Particle.CRIT).rad(5),
            new Attack(Kind.RING, "Tremblement", 0, 6, 0.8).part(Particle.CRIT).rad(6),
            new Attack(Kind.CHARGE, "Charge dévastatrice", 4, 14, 1.0).part(Particle.CRIT),
            new Attack(Kind.FRENZY, "Rage", 0, 12, 0)),

    ABYSS("abyss", "Seigneur des Abysses", "§9", EntityType.DROWNED, Material.DROWNED_SPAWN_EGG,
            300, 16, 0.28, 12, 1.25, 18,
            Material.PRISMARINE, Material.DARK_PRISMARINE, Material.SEA_LANTERN,
            "Maître des eaux : vortex, geysers, jet et tsunami.",
            new Attack(Kind.VORTEX, "Vortex abyssal", 3, 11, 1.0).part(Particle.SOUL).rad(3.8)
                    .fx(PotionEffectType.SLOWNESS, 80, 1),
            new Attack(Kind.STRIKES, "Geysers", 3, 14, 0.8).part(Particle.CLOUD).num(4).rad(2.2),
            new Attack(Kind.CONE, "Jet des abysses", 0, 12, 0.8).part(Particle.CLOUD).rad(10)
                    .fx(PotionEffectType.SLOWNESS, 60, 1),
            new Attack(Kind.RING, "Tsunami", 0, 7, 0.8).part(Particle.CLOUD).rad(6)),

    BRUTE("brute", "Brute Écarlate", "§4", EntityType.PIGLIN_BRUTE, Material.PIGLIN_BRUTE_SPAWN_EGG,
            320, 20, 0.32, 14, 1.3, 18,
            Material.POLISHED_BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.SHROOMLIGHT,
            "Guerrier de l'Enfer : tourbillon, écrasement, charge et fureur.",
            new Attack(Kind.SPIN, "Tourbillon", 0, 5, 0.5).part(Particle.SWEEP_ATTACK).rad(3.8),
            new Attack(Kind.LEAP, "Saut écrasant", 3, 12, 1.0).part(Particle.CRIT).rad(4.5),
            new Attack(Kind.CHARGE, "Charge écarlate", 4, 12, 1.0).part(Particle.CRIT),
            new Attack(Kind.FRENZY, "Fureur", 0, 12, 0)),

    NECRO("necro", "Seigneur Nécromancien (BOSS)", "§8§l", EntityType.EVOKER, Material.EVOKER_SPAWN_EGG,
            900, 24, 0.30, 15, 1.6, 14,
            Material.SOUL_SOIL, Material.BONE_BLOCK, Material.SOUL_LANTERN,
            "Boss en 3 phases : invoque des hordes de mobs différents.",
            new Attack(Kind.SUMMON, "Invocation", 0, 30, 0),
            new Attack(Kind.STRIKES, "Pluie d'âmes", 3, 25, 1.0).part(Particle.SOUL_FIRE_FLAME).num(6).rad(2.5),
            new Attack(Kind.RING, "Cercle de mort", 0, 8, 1.2).part(Particle.WITCH).rad(7)
                    .fx(PotionEffectType.WITHER, 80, 1),
            new Attack(Kind.BEHIND, "Pas de l'ombre", 4, 20, 1.2).part(Particle.SOUL_FIRE_FLAME));

    final String id, label, color, power;
    final EntityType entity;
    final Material egg, floor, pillar, light;
    final double health, damage, speed, armor, scale, special;
    final Attack[] attacks;

    Type(String id, String label, String color, EntityType entity, Material egg,
         double health, double damage, double speed, double armor, double scale, double special,
         Material floor, Material pillar, Material light, String power, Attack... attacks) {
        this.id = id; this.label = label; this.color = color; this.entity = entity; this.egg = egg;
        this.health = health; this.damage = damage; this.speed = speed; this.armor = armor;
        this.scale = scale; this.special = special;
        this.floor = floor; this.pillar = pillar; this.light = light;
        this.power = power; this.attacks = attacks;
    }

    static Type byId(String id) {
        if (id == null) return null;
        for (Type t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
