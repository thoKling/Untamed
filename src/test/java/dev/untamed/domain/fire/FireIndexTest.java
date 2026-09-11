package dev.untamed.domain.fire;

import dev.untamed.domain.world.GridPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireIndexTest {

    @Test
    void positionsAreStoredOnce() {
        FireIndex index = new FireIndex();

        assertTrue(index.add(new GridPos(4, 64, 4)));
        assertFalse(index.add(new GridPos(4, 64, 4)));
        assertEquals(1, index.size());
    }

    @Test
    void unloadingAChunkForgetsEverythingInIt() {
        FireIndex index = new FireIndex();
        index.add(new GridPos(1, 64, 1));
        index.add(new GridPos(9, 70, 3));
        index.add(new GridPos(20, 64, 1));

        assertEquals(2, index.removeChunk(0, 0));
        assertEquals(1, index.size());
        assertTrue(index.contains(new GridPos(20, 64, 1)));
    }

    @Test
    void removingTheLastPositionDropsItsChunk() {
        FireIndex index = new FireIndex();
        index.add(new GridPos(1, 64, 1));

        assertTrue(index.remove(new GridPos(1, 64, 1)));
        assertEquals(0, index.chunkCount());
        assertEquals(0, index.size());
    }

    /**
     * A sweep must not restart from the beginning each time, or torches beyond
     * the budget would never be checked on a busy server.
     */
    @Test
    void sweepsResumeWhereTheLastOneStopped() {
        FireIndex index = new FireIndex();
        index.add(new GridPos(1, 64, 1));
        index.add(new GridPos(17, 64, 1));
        index.add(new GridPos(33, 64, 1));

        List<GridPos> first = index.nextSweep(1);
        List<GridPos> second = index.nextSweep(1);
        List<GridPos> third = index.nextSweep(1);

        Set<GridPos> seen = new HashSet<>();
        seen.addAll(first);
        seen.addAll(second);
        seen.addAll(third);

        assertEquals(1, first.size());
        assertEquals(3, seen.size());
    }

    @Test
    void sweepsWrapAroundToTheStart() {
        FireIndex index = new FireIndex();
        index.add(new GridPos(1, 64, 1));
        index.add(new GridPos(17, 64, 1));

        index.nextSweep(1);
        index.nextSweep(1);
        List<GridPos> wrapped = index.nextSweep(1);

        assertEquals(List.of(new GridPos(1, 64, 1)), wrapped);
    }

    @Test
    void anEmptyIndexSweepsNothing() {
        assertTrue(new FireIndex().nextSweep(64).isEmpty());
    }

    @Test
    void rainDousedMarksAreDroppedWithTheirChunk() {
        FireIndex index = new FireIndex();
        GridPos pos = new GridPos(2, 64, 2);
        index.add(pos);
        index.markDousedByRain(pos);

        assertTrue(index.isDousedByRain(pos));

        index.removeChunk(0, 0);
        index.add(pos);

        assertFalse(index.isDousedByRain(pos));
    }

    @Test
    void untrackedPositionsCannotBeMarked() {
        FireIndex index = new FireIndex();
        GridPos pos = new GridPos(2, 64, 2);

        index.markDousedByRain(pos);

        assertFalse(index.isDousedByRain(pos));
    }
}
