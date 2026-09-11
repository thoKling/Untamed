package dev.survivaloverhaul.domain.fire;

import dev.survivaloverhaul.domain.config.TorchConfig;

import java.util.Objects;

/**
 * The rain/fire rules.
 *
 * <p>This class is the whole of the mechanic's decision making, and it is a pure
 * function of an observation plus a random roll. That is what lets the mechanic
 * be balanced and tested without a running game.
 */
public final class FireDousingSystem {

    private final TorchConfig config;

    public FireDousingSystem(TorchConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public TorchConfig config() {
        return config;
    }

    /**
     * Decides what should happen to one tracked position.
     *
     * @param observation state gathered from the world
     * @param roll        a uniform sample in [0, 1)
     */
    public FireAction evaluate(FireObservation observation, double roll) {
        if (!observation.chunkLoaded()) {
            return FireAction.NONE;
        }
        if (!observation.present()) {
            return FireAction.UNTRACK;
        }
        if (!config.enabled()) {
            return FireAction.NONE;
        }

        if (observation.lit()) {
            return observation.rainedOn() && roll < extinguishChance(observation)
                    ? FireAction.EXTINGUISH
                    : FireAction.NONE;
        }

        // Only rain relights what rain put out. A torch a player doused on
        // purpose stays dark, which keeps the mechanic from fighting the player.
        boolean canRelight = config.relightWhenDry()
                && observation.dousedByRain()
                && !observation.rainedOn();
        return canRelight ? FireAction.RELIGHT : FireAction.NONE;
    }

    /** The effective per-check chance for this observation, clamped to [0, 1]. */
    public double extinguishChance(FireObservation observation) {
        double chance = config.extinguishChance();
        if (observation.thundering()) {
            chance *= config.thunderstormMultiplier();
        }
        return Math.min(1.0d, Math.max(0.0d, chance));
    }
}
