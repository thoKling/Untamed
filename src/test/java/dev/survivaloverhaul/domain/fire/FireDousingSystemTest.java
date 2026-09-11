package dev.survivaloverhaul.domain.fire;

import dev.survivaloverhaul.domain.config.TorchConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The dousing rules are a pure function, so every branch is reachable without a
 * Minecraft instance. That is the point of keeping them out of the block.
 */
class FireDousingSystemTest {

    private static final TorchConfig ALWAYS = new TorchConfig(
            true, 1.0d, 1.0d, false, 40, 512, true);

    private static FireObservation lit(boolean rainedOn) {
        return new FireObservation(true, true, true, rainedOn, false, false);
    }

    private static FireObservation unlit(boolean rainedOn, boolean dousedByRain) {
        return new FireObservation(true, true, false, rainedOn, false, dousedByRain);
    }

    @Test
    void litTorchInRainGoesOut() {
        FireDousingSystem system = new FireDousingSystem(ALWAYS);
        assertEquals(FireAction.EXTINGUISH, system.evaluate(lit(true), 0.0d));
    }

    @Test
    void litTorchOutOfRainIsLeftAlone() {
        FireDousingSystem system = new FireDousingSystem(ALWAYS);
        assertEquals(FireAction.NONE, system.evaluate(lit(false), 0.0d));
    }

    @Test
    void chanceIsRespected() {
        TorchConfig halfChance = new TorchConfig(true, 0.5d, 1.0d, false, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(halfChance);

        assertEquals(FireAction.EXTINGUISH, system.evaluate(lit(true), 0.49d));
        assertEquals(FireAction.NONE, system.evaluate(lit(true), 0.5d));
    }

    @Test
    void thunderstormRaisesTheChance() {
        TorchConfig stormy = new TorchConfig(true, 0.25d, 2.0d, false, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(stormy);

        FireObservation storm = new FireObservation(true, true, true, true, true, false);
        assertEquals(0.5d, system.extinguishChance(storm), 1.0e-9d);
        assertEquals(FireAction.EXTINGUISH, system.evaluate(storm, 0.4d));
    }

    @Test
    void chanceNeverExceedsCertainty() {
        TorchConfig stormy = new TorchConfig(true, 0.8d, 4.0d, false, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(stormy);

        FireObservation storm = new FireObservation(true, true, true, true, true, false);
        assertEquals(1.0d, system.extinguishChance(storm), 1.0e-9d);
    }

    @Test
    void rainDousedTorchRelightsOnceDryWhenConfigured() {
        TorchConfig relighting = new TorchConfig(true, 1.0d, 1.0d, true, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(relighting);

        assertEquals(FireAction.RELIGHT, system.evaluate(unlit(false, true), 0.9d));
    }

    @Test
    void handDousedTorchIsNeverRelitByTheWeather() {
        TorchConfig relighting = new TorchConfig(true, 1.0d, 1.0d, true, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(relighting);

        assertEquals(FireAction.NONE, system.evaluate(unlit(false, false), 0.9d));
    }

    @Test
    void nothingRelightsWhileItIsStillRaining() {
        TorchConfig relighting = new TorchConfig(true, 1.0d, 1.0d, true, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(relighting);

        assertEquals(FireAction.NONE, system.evaluate(unlit(true, true), 0.9d));
    }

    @Test
    void missingBlockIsDroppedFromTheIndex() {
        FireDousingSystem system = new FireDousingSystem(ALWAYS);
        assertEquals(FireAction.UNTRACK, system.evaluate(FireObservation.absent(), 0.0d));
    }

    @Test
    void unloadedChunkIsLeftForLater() {
        FireDousingSystem system = new FireDousingSystem(ALWAYS);
        assertEquals(FireAction.NONE, system.evaluate(FireObservation.unloaded(), 0.0d));
    }

    @Test
    void disablingTheMechanicStopsExtinguishingButStillPrunes() {
        TorchConfig off = new TorchConfig(false, 1.0d, 1.0d, false, 40, 512, true);
        FireDousingSystem system = new FireDousingSystem(off);

        assertEquals(FireAction.NONE, system.evaluate(lit(true), 0.0d));
        assertEquals(FireAction.UNTRACK, system.evaluate(FireObservation.absent(), 0.0d));
    }
}
