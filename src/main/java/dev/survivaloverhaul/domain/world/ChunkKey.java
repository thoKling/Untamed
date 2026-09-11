package dev.survivaloverhaul.domain.world;

/** Packing helpers for chunk coordinates used as map keys. */
public final class ChunkKey {

    private ChunkKey() {
    }

    public static long of(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    public static int chunkX(long key) {
        return (int) key;
    }

    public static int chunkZ(long key) {
        return (int) (key >> 32);
    }

    public static String describe(long key) {
        return "chunk[" + chunkX(key) + ", " + chunkZ(key) + "]";
    }
}
