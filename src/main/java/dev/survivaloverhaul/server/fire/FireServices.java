package dev.survivaloverhaul.server.fire;

import dev.survivaloverhaul.SurvivalOverhaul;
import dev.survivaloverhaul.integration.world.FireTrackingBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Wires the fire mechanic into the server lifecycle.
 *
 * <p>One {@link WorldFireService} exists per loaded level, created when the level
 * loads and discarded when it unloads. All of it is server-side state; clients
 * learn about doused torches only through ordinary block updates.
 */
public final class FireServices {

    private static final Map<ResourceKey<Level>, WorldFireService> SERVICES = new HashMap<>();

    private FireServices() {
    }

    public static void register() {
        ServerLevelEvents.LOAD.register((server, level) ->
                SERVICES.put(level.dimension(),
                        new WorldFireService(level, SurvivalOverhaul.config().torches())));

        ServerLevelEvents.UNLOAD.register((server, level) -> {
            WorldFireService service = SERVICES.remove(level.dimension());
            if (service != null) {
                service.clear();
            }
        });

        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) ->
                withService(level, service -> service.onChunkLoad(chunk)));

        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                withService(level, service -> service.onChunkUnload(chunk)));

        ServerTickEvents.END_LEVEL_TICK.register(level ->
                withService(level, WorldFireService::tick));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVICES.clear());

        FireTrackingBridge.setListener(new FireTrackingBridge.Listener() {
            @Override
            public void onFireSourcePlaced(Level level, BlockPos pos) {
                forLevel(level, service -> service.onFireSourcePlaced(pos));
            }

            @Override
            public void onFireSourceRelitByHand(Level level, BlockPos pos) {
                forLevel(level, service -> service.onFireSourceRelitByHand(pos));
            }
        });
    }

    /** @return the service for a level, or null if it is a client level */
    public static WorldFireService get(ServerLevel level) {
        return SERVICES.get(level.dimension());
    }

    private static void withService(ServerLevel level, Consumer<WorldFireService> action) {
        WorldFireService service = SERVICES.get(level.dimension());
        if (service != null) {
            action.accept(service);
        }
    }

    private static void forLevel(Level level, Consumer<WorldFireService> action) {
        if (level instanceof ServerLevel serverLevel) {
            withService(serverLevel, action);
        }
    }
}
