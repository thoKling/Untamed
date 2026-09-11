package dev.untamed.integration.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The wall-mounted variant of {@link SurvivalTorchBlock}.
 *
 * <p>It inherits the {@code lit} property, the relight interaction and the
 * placement notification, and adds only the facing and its attachment rules.
 *
 * <p>{@code FACING} is declared as an {@link EnumProperty} rather than a more
 * specific subtype on purpose: that type has been stable across Minecraft
 * versions where the narrower one has not.
 */
public class SurvivalWallTorchBlock extends SurvivalTorchBlock {

    public static final MapCodec<SurvivalWallTorchBlock> CODEC = simpleCodec(SurvivalWallTorchBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(5.5d, 3.0d, 11.0d, 10.5d, 13.0d, 16.0d),
            Direction.SOUTH, Block.box(5.5d, 3.0d, 0.0d, 10.5d, 13.0d, 5.0d),
            Direction.WEST, Block.box(11.0d, 3.0d, 5.5d, 16.0d, 13.0d, 10.5d),
            Direction.EAST, Block.box(0.0d, 3.0d, 5.5d, 5.0d, 13.0d, 10.5d));

    public SurvivalWallTorchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(LIT, Boolean.TRUE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends SurvivalWallTorchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());

        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        for (Direction candidate : context.getNearestLookingDirections()) {
            if (!candidate.getAxis().isHorizontal()) {
                continue;
            }

            BlockState placed = state.setValue(FACING, candidate.getOpposite());

            if (placed.canSurvive(level, pos)) {
                return placed;
            }
        }

        return null;
    }

    /** Version-sensitive: this signature has changed across Minecraft releases. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighbourPos,
                                     BlockState neighbourState, RandomSource random) {
        boolean lostSupport = direction.getOpposite() == state.getValue(FACING) && !canSurvive(state, level, pos);
        return lostSupport ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }

        Direction behind = state.getValue(FACING).getOpposite();

        double x = pos.getX() + 0.5d + 0.27d * behind.getStepX();
        double y = pos.getY() + 0.7d;
        double z = pos.getZ() + 0.5d + 0.27d * behind.getStepZ();

        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0d, 0.0d, 0.0d);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0d, 0.0d, 0.0d);
    }
}
