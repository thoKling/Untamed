package dev.untamed;

import dev.untamed.domain.config.SurvivalConfig;
import dev.untamed.integration.config.ConfigManager;
import dev.untamed.integration.registry.ModBlocks;
import dev.untamed.network.SurvivalNetworking;
import dev.untamed.server.fire.FireServices;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entry point.
 *
 * <p>Deliberately short. Its job is to load configuration, register content and
 * hand the survival systems their lifecycle hooks. Gameplay behaviour lives in
 * the domain layer, and Minecraft-facing detail lives in integration.
 */
public class Untamed implements ModInitializer {

    public static final String MOD_ID = "untamed";
    public static final Logger LOGGER = LoggerFactory.getLogger("Untamed");

    private static volatile SurvivalConfig config = SurvivalConfig.defaults();

    /** Namespaced identifier helper, so the mod id is written down once. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * The active gameplay configuration.
     *
     * <p>Server-authoritative. The client receives only the parts it needs to
     * present things correctly, over the network.
     */
    public static SurvivalConfig config() {
        return config;
    }

    @Override
    public void onInitialize() {
        config = ConfigManager.loadOrCreate();

        ModBlocks.register();
        SurvivalNetworking.registerCommon();
        FireServices.register();

        LOGGER.info("Untamed initialised (torch dousing {})",
                config.torches().enabled() ? "enabled" : "disabled");
    }
}
