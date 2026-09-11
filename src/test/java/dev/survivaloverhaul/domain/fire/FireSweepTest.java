package dev.survivaloverhaul.domain.fire;

import dev.survivaloverhaul.domain.config.TorchConfig;
import dev.survivaloverhaul.domain.world.GridPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises a full sweep against a fake world, which is the closest thing the
 * mod has to an end-to-end test that does not need Minecraft running.
 */
class FireSweepTest {

    /** A minimal stand-in for a world: which positions hold a torch, and its state. */
    private static final class FakeWorld implements FireSourceProbe, FireSourceMutator {

        private final Map<GridPos, Boolean> torches = new HashMap<>();
        private final Set<GridPos> rained = new HashSet<>();

        void place(GridPos pos, boolean lit) {
            torches.put(pos, lit);
        }

        void rainOn(GridPos pos) {
            rained.add(pos);
        }

        void stopRaining() {
            rained.clear();
        }

        boolean isLit(GridPos pos) {
            return Boolean.TRUE.equals(torches.get(pos));
        }

        @Override
        public FireObservation observe(GridPos pos) {
            Boolean lit = torches.get(pos);
            if (lit == null) {
                return FireObservation.absent();
            }
            return new FireObservation(true, true, lit, rained.contains(pos), false, false);
        }

        @Override
        public void extinguish(GridPos pos) {
            torches.put(pos, false);
        }

        @Override
        public void relight(GridPos pos) {
            torches.put(pos, true);
        }
    }

    private static final TorchConfig ALWAYS_DOUSE = new TorchConfig(
            true, 1.0d, 1.0d, false, 40, 512, true);

    private static final TorchConfig RELIGHTING = new TorchConfig(
            true, 1.0d, 1.0d, true, 40, 512, true);

    @Test
    void onlyRainedOnTorchesGoOut() {
        FakeWorld world = new FakeWorld();
        FireIndex index = new FireIndex();

        GridPos exposed = new GridPos(1, 64, 1);
        GridPos sheltered = new GridPos(2, 64, 2);
        world.place(exposed, true);
        world.place(sheltered, true);
        world.rainOn(exposed);
        index.add(exposed);
        index.add(sheltered);

        SweepResult result = new FireSweep(new FireDousingSystem(ALWAYS_DOUSE))
                .run(index, world, world, () -> 0.0d, 64);

        assertEquals(1, result.extinguished());
        assertFalse(world.isLit(exposed));
        assertTrue(world.isLit(sheltered));
    }

    @Test
    void aRainDousedTorchComesBackWhenTheWeatherClears() {
        FakeWorld world = new FakeWorld();
        FireIndex index = new FireIndex();
        FireSweep sweep = new FireSweep(new FireDousingSystem(RELIGHTING));

        GridPos pos = new GridPos(1, 64, 1);
        world.place(pos, true);
        world.rainOn(pos);
        index.add(pos);

        sweep.run(index, world, world, () -> 0.0d, 64);
        assertFalse(world.isLit(pos));

        world.stopRaining();
        SweepResult second = sweep.run(index, world, world, () -> 0.0d, 64);

        assertEquals(1, second.relit());
        assertTrue(world.isLit(pos));
    }

    @Test
    void aTorchThatWasNeverRainedOnStaysDark() {
        FakeWorld world = new FakeWorld();
        FireIndex index = new FireIndex();

        GridPos pos = new GridPos(1, 64, 1);
        world.place(pos, false);
        index.add(pos);

        SweepResult result = new FireSweep(new FireDousingSystem(RELIGHTING))
                .run(index, world, world, () -> 0.0d, 64);

        assertEquals(0, result.relit());
        assertFalse(world.isLit(pos));
    }

    @Test
    void brokenTorchesLeaveTheIndex() {
        FakeWorld world = new FakeWorld();
        FireIndex index = new FireIndex();

        GridPos pos = new GridPos(1, 64, 1);
        index.add(pos);

        SweepResult result = new FireSweep(new FireDousingSystem(ALWAYS_DOUSE))
                .run(index, world, world, () -> 0.0d, 64);

        assertEquals(1, result.untracked());
        assertEquals(0, index.size());
    }

    @Test
    void sweepsStayWithinTheirBudget() {
        FakeWorld world = new FakeWorld();
        FireIndex index = new FireIndex();

        for (int chunk = 0; chunk < 8; chunk++) {
            GridPos pos = new GridPos(chunk * 16, 64, 0);
            world.place(pos, true);
            world.rainOn(pos);
            index.add(pos);
        }

        SweepResult result = new FireSweep(new FireDousingSystem(ALWAYS_DOUSE))
                .run(index, world, world, () -> 0.0d, 3);

        assertEquals(3, result.inspected());
        assertEquals(3, result.extinguished());
    }
}
