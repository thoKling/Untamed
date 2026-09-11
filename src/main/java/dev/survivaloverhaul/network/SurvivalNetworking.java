package dev.survivaloverhaul.network;

import dev.survivaloverhaul.SurvivalOverhaul;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registration point for every packet the mod sends.
 *
 * <p>Kept as one small class on purpose. Later milestones add survival state,
 * backpack contents and inventory operations here, and every server-bound packet
 * they add must be validated server-side before it changes anything.
 */
public final class SurvivalNetworking {

    private SurvivalNetworking() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(TorchRulesPayload.TYPE, TorchRulesPayload.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(
                        handler.getPlayer(),
                        TorchRulesPayload.from(SurvivalOverhaul.config().torches())));
    }
}
