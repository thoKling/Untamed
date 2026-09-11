package dev.survivaloverhaul.domain.fire;

import dev.survivaloverhaul.domain.world.GridPos;

/**
 * Writes the result of the dousing rules back into the world.
 *
 * <p>The domain never decides how a change is presented: sounds, particles and
 * lighting updates all belong to the implementation.
 */
public interface FireSourceMutator {

    /** Puts out the fire source at the position. */
    void extinguish(GridPos pos);

    /** Relights the fire source at the position. */
    void relight(GridPos pos);
}
