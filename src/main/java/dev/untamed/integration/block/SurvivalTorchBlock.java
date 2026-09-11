package dev.untamed.integration.block;

import com.mojang.serialization.MapCodec;
import dev.untamed.integration.registry.ModBlocks;
import dev.untamed.integration.world.FireTrackingBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A torch that can be lit or unlit.
 *
 * <p>One block with a {@code lit} property, rather than two blocks that get
 * swapped. The light level is a function of the blockstate, so flipping the
 * property is enough: the vanilla lighting engine and the block-update sync do
 * the rest, and this mod never talks to the lighting engine directly.
 *
 * <p>This class holds no gameplay rules. Deciding whether a torch goes out is
 * the domain layer's job.
 */
public class SurvivalTorchBlock extends Block {

    public static final MapCodec<SurvivalTorchBlock> CODEC = simpleCodec(SurvivalTorchBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Matches vanilla's torch light level. */
    public static final int LIT_LUMINANCE = 14;

    private static final VoxelShape SHAPE = Block.box(6.0d, 0.0d, 6.0d, 10.0d, 10.0d, 10.0d);

    public SurvivalTorchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(LIT, Boolean.TRUE));
    }

    @Override
    protected MapCodec<? extends SurvivalTorchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Pick-block hands back a torch in the state that was picked.
     *
     * <p>Without this, both torch items map to the same block and the last one
     * registered would win, so picking a doused torch could hand back a lit one.
     */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(state.getValue(LIT) ? Items.TORCH : ModBlocks.UNLIT_TORCH_ITEM);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    /** Version-sensitive: this signature has changed across Minecraft releases. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        boolean lostSupport = direction == Direction.DOWN && !canSurvive(state, level, pos);
        return lostSupport ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide() && state.getBlock() != oldState.getBlock()) {
            FireTrackingBridge.fireSourcePlaced(level, pos);
        }
    }

    /**
     * Relighting an unlit torch by hand.
     *
     * <p>Version-sensitive: the interaction signature has changed across
     * Minecraft releases.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(LIT)) {
            return InteractionResult.PASS;
        }

        boolean igniter = stack.getItem() == Items.FLINT_AND_STEEL || stack.getItem() == Items.FIRE_CHARGE;

        if (!igniter) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, state.setValue(LIT, Boolean.TRUE));
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            FireTrackingBridge.fireSourceRelitByHand(level, pos);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }

        double x = pos.getX() + 0.5d;
        double y = pos.getY() + 0.7d;
        double z = pos.getZ() + 0.5d;

        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0d, 0.0d, 0.0d);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0d, 0.0d, 0.0d);
    }
}
