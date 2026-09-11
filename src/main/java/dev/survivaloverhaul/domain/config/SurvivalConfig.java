package dev.survivaloverhaul.domain.config;

/**
 * Root of the mod's gameplay configuration.
 *
 * <p>Later milestones add sibling sections (temperature, hydration, wetness,
 * inventory). Keeping them as separate records means each survival system can
 * be handed only the section it needs.
 *
 * @param torches settings for the rain/torch mechanic
 */
public record SurvivalConfig(TorchConfig torches) {

    public static SurvivalConfig defaults() {
        return new SurvivalConfig(TorchConfig.defaults());
    }

    /** Repairs a config that was deserialised from an incomplete or edited file. */
    public SurvivalConfig sanitised() {
        TorchConfig torchSection = torches == null ? TorchConfig.defaults() : torches;
        return new SurvivalConfig(torchSection.sanitised());
    }
}
