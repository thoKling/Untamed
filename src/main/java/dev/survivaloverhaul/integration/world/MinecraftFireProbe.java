package dev.survivaloverhaul.integration.world;

import dev.survivaloverhaul.domain.fire.FireObservation;
import dev.survivaloverhaul.domain.fire.FireSourceProbe;
import dev.survivaloverhaul.domain.world.GridPos;
import dev.survivaloverhaul.integration.block.TorchSubstitution;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads a Minecraft level on behalf of the dousing rules.
 *
 * <p>Rain exposure comes from vanilla's own precipitation test, so biome rules
 * are respected for free: no rain falls in a desert, in the Nether, above the
 * snow line, or under a roof.
 */
public final class MinecraftFireProbe implements FireSourceProbe {

    private final ServerLevel level;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    public MinecraftFireProbe(ServerLevel level) {
        this.level = level;
    }

    @Override
    public FireObservation observe(GridPos pos) {
        scratch.set(pos.x(), pos.y(), pos.z());

        if (!level.getChunkSource().hasChunk(pos.chunkX(), pos.chunkZ())) {
            return FireObservation.unloaded();
        }

        BlockState state = level.getBlockState(scratch);
        if (!TorchSubstitution.isManaged(state)) {
            return FireObservation.absent();
        }

        // isRainingAt folds together weather, sky exposure, height and biome.
        boolean rainedOn = level.isRainingAt(scratch);

        return new FireObservation(
                true,
                true,
                TorchSubstitution.isLit(state),
                rainedOn,
                level.isThundering(),
                false);
    }
}
