package dev.survivaloverhaul.integration.mixin;

import dev.survivaloverhaul.SurvivalOverhaul;
import dev.survivaloverhaul.integration.block.TorchSubstitution;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects vanilla torch placement to the survival torch.
 *
 * <p>Version-sensitive. A torch is not placed by a plain {@code BlockItem}: it
 * is a {@link StandingAndWallBlockItem}, which overrides {@code getPlacementState}
 * to choose between the standing and the wall block and never calls {@code super}.
 * An injection on {@code BlockItem} alone therefore never sees a torch, so the
 * override has to be targeted directly. Check this if torches stop converting
 * after a Minecraft update.
 *
 * <p>{@link BlockItemMixin} still covers every other block item, so both are
 * needed. Substitution itself is idempotent and only reacts to vanilla torch
 * states, so a state passing through both injections is harmless.
 */
@Mixin(StandingAndWallBlockItem.class)
public abstract class StandingAndWallBlockItemMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void survivaloverhaul$substituteTorch(BlockPlaceContext context,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState placed = cir.getReturnValue();
        if (placed == null || !SurvivalOverhaul.config().torches().convertVanillaPlacement()) {
            return;
        }
        TorchSubstitution.substitute(placed).ifPresent(cir::setReturnValue);
    }
}
