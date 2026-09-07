package zoesim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RpsSimulationTest {
    @Test public void rulesFormTheExpectedCycle() {
        assertTrue(RpsSimulation.beats(0, 2));
        assertTrue(RpsSimulation.beats(1, 0));
        assertTrue(RpsSimulation.beats(2, 1));
        assertFalse(RpsSimulation.beats(0, 0));
        assertFalse(RpsSimulation.beats(1, 1));
        assertFalse(RpsSimulation.beats(2, 2));
        assertFalse(RpsSimulation.beats(0, 1));
        assertFalse(RpsSimulation.beats(1, 2));
        assertFalse(RpsSimulation.beats(2, 0));
    }

    @Test public void userControlsAreClampedToSupportedRanges() {
        assertEquals(0, RpsSimulation.clampStartingCount(-10));
        assertEquals(42, RpsSimulation.clampStartingCount(42));
        assertEquals(100, RpsSimulation.clampStartingCount(120));
        assertEquals(.35f, RpsSimulation.clampSpeed(-1f), .0001f);
        assertEquals(1.4f, RpsSimulation.clampSpeed(1.4f), .0001f);
        assertEquals(2.5f, RpsSimulation.clampSpeed(8f), .0001f);
    }
}
