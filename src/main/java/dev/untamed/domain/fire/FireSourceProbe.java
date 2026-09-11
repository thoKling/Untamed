package dev.untamed.domain.fire;

import dev.untamed.domain.world.GridPos;

/**
 * Reads world state for the dousing rules.
 *
 * <p>Implemented in the integration layer against a Minecraft world. Kept as an
 * interface so the rules can be exercised against a fake world in tests.
 */
@FunctionalInterface
public interface FireSourceProbe {

    FireObservation observe(GridPos pos);
}
