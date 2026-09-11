package dev.survivaloverhaul.domain.world;

/**
 * A block position, expressed without any Minecraft type.
 *
 * <p>The domain layer deliberately owns its own position type so survival logic
 * can be written and tested without loading Minecraft classes.
 */
public record GridPos(int x, int y, int z) {

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** Key of the 16x16 column this position belongs to. */
    public long chunkKey() {
        return ChunkKey.of(chunkX(), chunkZ());
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
