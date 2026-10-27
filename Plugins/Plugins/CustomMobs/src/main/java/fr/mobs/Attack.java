package fr.mobs;

import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;

/** Une attaque spéciale d'un mob (les paramètres changent selon le type d'attaque). */
final class Attack {

    enum Kind { RING, CHARGE, BEHIND, CONE, ZONE, LEAP, VORTEX, SPIN, VOLLEY, STRIKES, FRENZY, SUMMON }

    final Kind kind;
    final String name;
    final double min, max;   // distance à laquelle le mob peut la lancer
    final double mult;       // multiplicateur des dégâts spéciaux du mob

    Particle particle = Particle.CRIT;
    PotionEffectType effect;
    int effTicks, effAmp;
    int ignite;              // ticks de feu (pour les boules de feu : > 0 = boules de feu)
    double radius = 5;
    int count = 3;

    Attack(Kind kind, String name, double min, double max, double mult) {
        this.kind = kind; this.name = name; this.min = min; this.max = max; this.mult = mult;
    }

    Attack part(Particle p) { this.particle = p; return this; }
    Attack fx(PotionEffectType t, int ticks, int amp) { this.effect = t; this.effTicks = ticks; this.effAmp = amp; return this; }
    Attack burn(int ticks) { this.ignite = ticks; return this; }
    Attack rad(double r) { this.radius = r; return this; }
    Attack num(int n) { this.count = n; return this; }
}
