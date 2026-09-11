package dev.survivaloverhaul.network;

import dev.survivaloverhaul.SurvivalOverhaul;
import dev.survivaloverhaul.domain.config.TorchConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a joining client which torch rules the server is running.
 *
 * <p>The client never decides anything from this. It uses it purely for
 * presentation, so a player can be told whether a doused torch will come back on
 * its own or has to be relit by hand.
 *
 * @param dousingEnabled whether rain puts torches out on this server
 * @param relightWhenDry whether rain-doused torches relight themselves
 */
public record TorchRulesPayload(boolean dousingEnabled, boolean relightWhenDry)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TorchRulesPayload> TYPE =
            new CustomPacketPayload.Type<>(SurvivalOverhaul.id("torch_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TorchRulesPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, TorchRulesPayload::dousingEnabled,
                    ByteBufCodecs.BOOL, TorchRulesPayload::relightWhenDry,
                    TorchRulesPayload::new);

    public static TorchRulesPayload from(TorchConfig config) {
        return new TorchRulesPayload(config.enabled(), config.relightWhenDry());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
