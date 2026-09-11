package dev.untamed.integration.block;

import dev.untamed.integration.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Maps vanilla torch states onto the survival equivalents.
 *
 * <p>Players keep crafting and placing ordinary torches; what lands in the world
 * is the survival block. Keeping the mapping here means the mixin that calls it
 * stays a two-line hand-off.
 */
public final class TorchSubstitution {

    private TorchSubstitution() {
    }

    /**
     * @return the survival state to place instead, or empty if the state is not
     *         a vanilla torch and should be left alone
     */
    public static Optional<BlockState> substitute(BlockState placed) {
        if (placed.getBlock() == Blocks.TORCH) {
            return Optional.of(ModBlocks.TORCH.defaultBlockState()
                    .setValue(SurvivalTorchBlock.LIT, Boolean.TRUE));
        }
        if (placed.getBlock() == Blocks.WALL_TORCH) {
            return Optional.of(ModBlocks.WALL_TORCH.defaultBlockState()
                    .setValue(SurvivalWallTorchBlock.FACING,
                            placed.getValue(BlockStateProperties.HORIZONTAL_FACING))
                    .setValue(SurvivalTorchBlock.LIT, Boolean.TRUE));
        }
        return Optional.empty();
    }

    /** Whether a state is a fire source this mod manages. */
    public static boolean isManaged(BlockState state) {
        return state.getBlock() instanceof SurvivalTorchBlock;
    }

    /** Whether a managed fire source is currently burning. */
    public static boolean isLit(BlockState state) {
        return isManaged(state) && state.getValue(SurvivalTorchBlock.LIT);
    }
}
