package dev.untamed.integration.mixin;

import dev.untamed.Untamed;
import dev.untamed.integration.block.TorchSubstitution;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects vanilla torch placement to the survival torch.
 *
 * <p>Catching placement at the item level covers every route a player has to a
 * torch, including off-hand placement and other mods that place block items,
 * without touching the vanilla torch block itself.
 *
 * <p>Torches that already exist in a world, or that world generation places, are
 * not converted here; they are handled where they are found, on chunk load.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void untamed$substituteTorch(BlockPlaceContext context,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState placed = cir.getReturnValue();
        if (placed == null || !Untamed.config().torches().convertVanillaPlacement()) {
            return;
        }
        TorchSubstitution.substitute(placed).ifPresent(cir::setReturnValue);
    }
}
