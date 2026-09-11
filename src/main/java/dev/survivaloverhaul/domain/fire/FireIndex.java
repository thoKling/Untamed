package dev.survivaloverhaul.domain.fire;

import dev.survivaloverhaul.domain.world.ChunkKey;
import dev.survivaloverhaul.domain.world.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The set of fire-source positions one world is currently watching.
 *
 * <p>Positions are bucketed by chunk for two reasons. Unloading a chunk drops
 * its whole bucket in one step, and sweeps walk chunk by chunk so a single tick
 * never touches more than a bounded slice of the world. Nothing here ever scans
 * blocks; the index is fed by chunk loads and by block placement.
 */
public final class FireIndex {

    private final Map<Long, Set<GridPos>> byChunk = new LinkedHashMap<>();
    private final List<Long> sweepOrder = new ArrayList<>();
    private final Set<GridPos> dousedByRain = new HashSet<>();

    private int cursor;
    private int size;

    /** @return true if the position was not already indexed */
    public boolean add(GridPos pos) {
        long key = pos.chunkKey();
        Set<GridPos> bucket = byChunk.get(key);
        if (bucket == null) {
            bucket = new LinkedHashSet<>();
            byChunk.put(key, bucket);
            sweepOrder.add(key);
        }
        if (!bucket.add(pos)) {
            return false;
        }
        size++;
        return true;
    }

    /** @return true if the position was indexed and has now been removed */
    public boolean remove(GridPos pos) {
        long key = pos.chunkKey();
        Set<GridPos> bucket = byChunk.get(key);
        if (bucket == null || !bucket.remove(pos)) {
            return false;
        }
        size--;
        dousedByRain.remove(pos);
        if (bucket.isEmpty()) {
            dropChunkBucket(key);
        }
        return true;
    }

    /** Forgets everything in a chunk, for use when that chunk unloads. */
    public int removeChunk(int chunkX, int chunkZ) {
        long key = ChunkKey.of(chunkX, chunkZ);
        Set<GridPos> bucket = byChunk.get(key);
        if (bucket == null) {
            return 0;
        }
        for (GridPos pos : bucket) {
            dousedByRain.remove(pos);
        }
        int removed = bucket.size();
        size -= removed;
        dropChunkBucket(key);
        return removed;
    }

    private void dropChunkBucket(long key) {
        byChunk.remove(key);
        int orderIndex = sweepOrder.indexOf(key);
        if (orderIndex >= 0) {
            sweepOrder.remove(orderIndex);
            if (orderIndex < cursor) {
                cursor--;
            }
        }
        if (cursor > sweepOrder.size()) {
            cursor = 0;
        }
    }

    public boolean contains(GridPos pos) {
        Set<GridPos> bucket = byChunk.get(pos.chunkKey());
        return bucket != null && bucket.contains(pos);
    }

    public boolean isDousedByRain(GridPos pos) {
        return dousedByRain.contains(pos);
    }

    public void markDousedByRain(GridPos pos) {
        if (contains(pos)) {
            dousedByRain.add(pos);
        }
    }

    public void clearDousedByRain(GridPos pos) {
        dousedByRain.remove(pos);
    }

    /**
     * Returns the next slice of positions to inspect, resuming where the last
     * sweep stopped. Whole chunk buckets are taken at a time, so the budget is a
     * soft limit: a sweep stops as soon as it has met or passed it.
     *
     * @param budget approximate number of positions to return
     */
    public List<GridPos> nextSweep(int budget) {
        if (size == 0 || budget <= 0) {
            return Collections.emptyList();
        }

        List<GridPos> slice = new ArrayList<>(Math.min(budget, size));
        int chunksVisited = 0;
        int chunkCount = sweepOrder.size();

        while (slice.size() < budget && chunksVisited < chunkCount) {
            if (cursor >= sweepOrder.size()) {
                cursor = 0;
            }
            Set<GridPos> bucket = byChunk.get(sweepOrder.get(cursor));
            if (bucket != null) {
                slice.addAll(bucket);
            }
            cursor++;
            chunksVisited++;
        }
        return slice;
    }

    public int size() {
        return size;
    }

    public int chunkCount() {
        return byChunk.size();
    }

    public void clear() {
        byChunk.clear();
        sweepOrder.clear();
        dousedByRain.clear();
        cursor = 0;
        size = 0;
    }
}
