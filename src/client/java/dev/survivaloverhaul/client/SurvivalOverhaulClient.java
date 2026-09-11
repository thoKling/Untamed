package dev.survivaloverhaul.client;

import dev.survivaloverhaul.integration.registry.ModBlocks;
import dev.survivaloverhaul.network.TorchRulesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Client entry point.
 *
 * <p>Presentation only. Nothing here decides gameplay: the client is told what
 * the rules are and what the world looks like, and renders accordingly.
 *
 * <p>There is no render-layer registration. Since Minecraft 26.x the layer is
 * derived from the texture's own transparency, so a cutout torch needs no code.
 */
public class SurvivalOverhaulClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(TorchRulesPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        ServerTorchRules.update(payload.dousingEnabled(), payload.relightWhenDry())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerTorchRules.reset());

        registerTorchTooltip();
    }

    /**
     * Explains the mechanic where the player will actually meet it.
     *
     * <p>A survival overhaul that silently changes a familiar item is just
     * confusing, and a tooltip costs nothing compared to a HUD element.
     */
    private void registerTorchTooltip() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!ServerTorchRules.dousingEnabled()) {
                return;
            }
            String key = tooltipKeyFor(stack.getItem());
            if (key == null) {
                return;
            }
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        });
    }

    /**
     * @return the tooltip line for a torch item, or null for anything else
     */
    private static String tooltipKeyFor(Item item) {
        if (item == ModBlocks.UNLIT_TORCH_ITEM) {
            // An unlit torch says how to light it. What rain does to it only
            // matters once it is burning.
            return "tooltip.survivaloverhaul.torch.unlit";
        }
        if (item == Items.TORCH || item == ModBlocks.TORCH_ITEM) {
            return ServerTorchRules.relightWhenDry()
                    ? "tooltip.survivaloverhaul.torch.relights"
                    : "tooltip.survivaloverhaul.torch.manual_relight";
        }
        return null;
    }
}
