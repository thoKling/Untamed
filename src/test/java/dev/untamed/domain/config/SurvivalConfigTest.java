package dev.untamed.domain.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config files are hand-edited, so the sanitising step is load-bearing: a bad
 * value must not be able to stall the tick loop or divide by zero.
 */
class SurvivalConfigTest {

    @Test
    void defaultsAreAlreadyValid() {
        SurvivalConfig defaults = SurvivalConfig.defaults();
        assertEquals(defaults, defaults.sanitised());
    }

    @Test
    void aMissingSectionFallsBackToDefaults() {
        SurvivalConfig repaired = new SurvivalConfig(null).sanitised();
        assertEquals(TorchConfig.defaults(), repaired.torches());
    }

    @Test
    void probabilitiesAreClampedToARealRange() {
        TorchConfig repaired = new TorchConfig(true, 4.5d, -1.0d, false, 40, 512, true).sanitised();

        assertEquals(1.0d, repaired.extinguishChance());
        assertEquals(0.0d, repaired.thunderstormMultiplier());
    }

    @Test
    void notANumberBecomesZeroRatherThanPoisoningTheRules() {
        TorchConfig repaired = new TorchConfig(true, Double.NaN, 1.0d, false, 40, 512, true).sanitised();
        assertEquals(0.0d, repaired.extinguishChance());
    }

    @Test
    void aZeroIntervalCannotStallTheSweep() {
        TorchConfig repaired = new TorchConfig(true, 0.5d, 1.0d, false, 0, 0, true).sanitised();

        assertTrue(repaired.checkIntervalTicks() >= 1);
        assertTrue(repaired.maxChecksPerInterval() >= 1);
    }
}
