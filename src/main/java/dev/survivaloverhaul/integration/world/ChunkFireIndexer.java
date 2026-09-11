package dev.survivaloverhaul.integration.world;

import dev.survivaloverhaul.domain.fire.FireIndex;
import dev.survivaloverhaul.domain.world.GridPos;
import dev.survivaloverhaul.integration.block.TorchSubstitution;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Finds the fire sources in a chunk when it loads.
 *
 * <p>This is what keeps the mechanic scalable. Instead of sweeping the world,
 * each chunk is examined once, and only those of its sections whose block
 * palette actually mentions a survival torch are walked. A chunk with no torches
 * rejects every section on the palette check and costs almost nothing.
 *
 * <p>Indexing on chunk load rather than saving positions to disk also means
 * torches from world generation, structures and other mods are picked up, and
 * that the index can never drift out of step with the world.
 */
public final class ChunkFireIndexer {

    private ChunkFireIndexer() {
    }

    /**
     * Adds every managed fire source in the chunk to the index.
     *
     * @return how many positions were added
     */
    public static int index(LevelChunk chunk, FireIndex index) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinY();
        int added = 0;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            if (!section.maybeHas(TorchSubstitution::isManaged)) {
                continue;
            }

            int sectionMinY = minY + sectionIndex * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!TorchSubstitution.isManaged(state)) {
                            continue;
                        }
                        GridPos pos = new GridPos(
                                chunkPos.getMinBlockX() + x,
                                sectionMinY + y,
                                chunkPos.getMinBlockZ() + z);
                        if (index.add(pos)) {
                            added++;
                        }
                    }
                }
            }
        }
        return added;
    }
}
