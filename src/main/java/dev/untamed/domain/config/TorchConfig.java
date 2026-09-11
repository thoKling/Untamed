package dev.untamed.domain.config;

/**
 * Tuning for the "rain extinguishes torches" mechanic.
 *
 * <p>Every gameplay constant lives here rather than in the block or the tick
 * loop, so balancing never requires touching integration code.
 *
 * @param enabled                  master switch for the mechanic
 * @param extinguishChance         probability, per check, that a rained-on lit torch goes out
 * @param thunderstormMultiplier   multiplier applied to that chance during a thunderstorm
 * @param relightWhenDry           whether rain-doused torches relight themselves once dry
 * @param checkIntervalTicks       ticks between sweeps of the tracked-torch index
 * @param maxChecksPerInterval     upper bound on positions inspected per sweep, per world
 * @param convertVanillaPlacement  whether placing a vanilla torch yields the survival torch
 */
public record TorchConfig(
        boolean enabled,
        double extinguishChance,
        double thunderstormMultiplier,
        boolean relightWhenDry,
        int checkIntervalTicks,
        int maxChecksPerInterval,
        boolean convertVanillaPlacement
) {

    public static TorchConfig defaults() {
        return new TorchConfig(true, 0.35d, 2.0d, false, 40, 512, true);
    }

    /**
     * Clamps values that would break the tick loop or make the mechanic
     * nonsensical. Config files are user-editable, so nothing downstream may
     * assume a sane value was written.
     */
    public TorchConfig sanitised() {
        return new TorchConfig(
                enabled,
                clamp(extinguishChance, 0.0d, 1.0d),
                clamp(thunderstormMultiplier, 0.0d, 100.0d),
                relightWhenDry,
                Math.max(1, checkIntervalTicks),
                Math.max(1, maxChecksPerInterval),
                convertVanillaPlacement
        );
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.min(max, Math.max(min, value));
    }
}
