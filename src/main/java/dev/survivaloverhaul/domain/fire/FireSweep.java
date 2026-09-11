package dev.survivaloverhaul.domain.fire;

import dev.survivaloverhaul.domain.world.GridPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Applies {@link FireDousingSystem} to a slice of a {@link FireIndex}.
 *
 * <p>This is the orchestration half of the mechanic: pick a bounded slice, ask
 * the rules about each position, apply the answers, and keep the index honest.
 * It holds no Minecraft state, so a full sweep can be simulated in a unit test.
 */
public final class FireSweep {

    private final FireDousingSystem rules;

    public FireSweep(FireDousingSystem rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Runs one sweep.
     *
     * @param index   positions being watched, mutated in place
     * @param probe   reads world state
     * @param mutator writes changes back
     * @param random  supplies uniform samples in [0, 1)
     * @param budget  approximate number of positions to inspect
     */
    public SweepResult run(FireIndex index,
                           FireSourceProbe probe,
                           FireSourceMutator mutator,
                           DoubleSupplier random,
                           int budget) {
        List<GridPos> slice = index.nextSweep(budget);
        if (slice.isEmpty()) {
            return SweepResult.EMPTY;
        }

        int extinguished = 0;
        int relit = 0;
        List<GridPos> stale = new ArrayList<>();

        for (GridPos pos : slice) {
            FireObservation observation = enrich(index, pos, probe.observe(pos));

            switch (rules.evaluate(observation, random.getAsDouble())) {
                case EXTINGUISH -> {
                    mutator.extinguish(pos);
                    index.markDousedByRain(pos);
                    extinguished++;
                }
                case RELIGHT -> {
                    mutator.relight(pos);
                    index.clearDousedByRain(pos);
                    relit++;
                }
                case UNTRACK -> stale.add(pos);
                case NONE -> {
                }
            }
        }

        for (GridPos pos : stale) {
            index.remove(pos);
        }

        return new SweepResult(slice.size(), extinguished, relit, stale.size());
    }

    /**
     * The probe reports the world; the index remembers how a torch came to be
     * dark. Merging the two here keeps that bookkeeping out of the adapter.
     */
    private FireObservation enrich(FireIndex index, GridPos pos, FireObservation observation) {
        if (!observation.present() || observation.lit()) {
            return observation;
        }
        return new FireObservation(
                observation.chunkLoaded(),
                true,
                false,
                observation.rainedOn(),
                observation.thundering(),
                index.isDousedByRain(pos)
        );
    }
}
