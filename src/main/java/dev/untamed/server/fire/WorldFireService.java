package dev.untamed.server.fire;

import dev.untamed.Untamed;
import dev.untamed.domain.config.TorchConfig;
import dev.untamed.domain.fire.FireDousingSystem;
import dev.untamed.domain.fire.FireIndex;
import dev.untamed.domain.fire.FireSweep;
import dev.untamed.domain.fire.SweepResult;
import dev.untamed.domain.world.GridPos;
import dev.untamed.integration.world.ChunkFireIndexer;
import dev.untamed.integration.world.MinecraftFireMutator;
import dev.untamed.integration.world.MinecraftFireProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Owns the fire index and the sweep schedule for one server level.
 *
 * <p>Everything here is bookkeeping and scheduling. The decision of whether a
 * given torch goes out belongs to {@link FireDousingSystem} in the domain layer,
 * and this class never second-guesses it.
 */
public final class WorldFireService {

    private final ServerLevel level;
    private final TorchConfig config;
    private final FireIndex index = new FireIndex();
    private final FireSweep sweep;
    private final MinecraftFireProbe probe;
    private final MinecraftFireMutator mutator;

    private int ticksUntilSweep;

    public WorldFireService(ServerLevel level, TorchConfig config) {
        this.level = level;
        this.config = config;
        this.sweep = new FireSweep(new FireDousingSystem(config));
        this.probe = new MinecraftFireProbe(level);
        this.mutator = new MinecraftFireMutator(level);
        this.ticksUntilSweep = config.checkIntervalTicks();
    }

    public ServerLevel level() {
        return level;
    }

    public int trackedCount() {
        return index.size();
    }

    public void onChunkLoad(LevelChunk chunk) {
        ChunkFireIndexer.index(chunk, index);
    }

    public void onChunkUnload(LevelChunk chunk) {
        index.removeChunk(chunk.getPos().x(), chunk.getPos().z());
    }

    public void onFireSourcePlaced(BlockPos pos) {
        index.add(toGridPos(pos));
    }

    /**
     * A torch a player relit is no longer the weather's business, so it stops
     * being a candidate for automatic relighting.
     */
    public void onFireSourceRelitByHand(BlockPos pos) {
        index.clearDousedByRain(toGridPos(pos));
    }

    /**
     * Advances the sweep schedule by one tick.
     *
     * <p>Between sweeps this is a decrement and a comparison. When the mechanic
     * is off, or when the level has nothing tracked, it does not even sweep.
     */
    public void tick() {
        if (--ticksUntilSweep > 0) {
            return;
        }
        ticksUntilSweep = config.checkIntervalTicks();

        if (!config.enabled() || index.size() == 0) {
            return;
        }
        // Nothing can change while the sky is clear unless torches are waiting
        // to dry out, so a clear-weather level skips the sweep entirely.
        if (!level.isRaining() && !config.relightWhenDry()) {
            return;
        }

        SweepResult result = sweep.run(
                index,
                probe,
                mutator,
                level.getRandom()::nextDouble,
                config.maxChecksPerInterval());

        if (result.changedAnything()) {
            Untamed.LOGGER.debug(
                    "{}: doused {}, relit {} of {} inspected",
                    level.dimension().identifier(),
                    result.extinguished(),
                    result.relit(),
                    result.inspected());
        }
    }

    public void clear() {
        index.clear();
    }

    private static GridPos toGridPos(BlockPos pos) {
        return new GridPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
