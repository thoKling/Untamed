package dev.survivaloverhaul.integration.item;

import dev.survivaloverhaul.integration.block.IgnitionSources;
import dev.survivaloverhaul.integration.block.SurvivalTorchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The item form of a torch that is not burning.
 *
 * <p>Placement reuses everything vanilla already does for a torch, including the
 * choice between the standing and the wall block and the support check. Only the
 * {@code lit} value differs, so this stays a one-line change on the way out.
 */
public class UnlitTorchItem extends StandingAndWallBlockItem {

    public UnlitTorchItem(Block standing, Block wall, Direction attachment, Item.Properties properties) {
        super(standing, wall, attachment, properties);
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null || !state.hasProperty(SurvivalTorchBlock.LIT)) {
            return state;
        }
        return state.setValue(SurvivalTorchBlock.LIT, Boolean.FALSE);
    }

    /**
     * Clicking an open flame lights the torches instead of placing one.
     *
     * <p>The whole stack catches at once. Lighting them one at a time would be
     * busywork rather than difficulty, and the flame being borrowed costs the
     * player nothing either way.
     *
     * <p>Sneaking suppresses this, so a player can still place a torch against a
     * campfire or next to another torch.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.isSecondaryUseActive() && light(context)) {
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    /** @return whether the click found a flame and the stack was lit */
    private static boolean light(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (player == null || !IgnitionSources.canLightATorch(level.getBlockState(pos))) {
            return false;
        }

        // The client returns true as well, so the arm swings and no placement
        // is predicted; only the server changes what the player is holding.
        if (!level.isClientSide()) {
            ItemStack held = context.getItemInHand();
            player.setItemInHand(context.getHand(), new ItemStack(Items.TORCH, held.getCount()));
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.8f, 1.4f);
        }
        return true;
    }
}
