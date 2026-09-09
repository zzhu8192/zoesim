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

    @Test public void winnerDeterminationResolvesCorrectly() {
        assertEquals(RpsSimulation.Kind.ROCK, RpsSimulation.determineWinner(15, 0, 0));
        assertEquals(RpsSimulation.Kind.PAPER, RpsSimulation.determineWinner(0, 8, 0));
        assertEquals(RpsSimulation.Kind.SCISSORS, RpsSimulation.determineWinner(0, 0, 22));

        // Ongoing battles with 2 or 3 kinds alive return null
        org.junit.Assert.assertNull(RpsSimulation.determineWinner(10, 10, 10));
        org.junit.Assert.assertNull(RpsSimulation.determineWinner(5, 5, 0));
        org.junit.Assert.assertNull(RpsSimulation.determineWinner(0, 4, 3));
        org.junit.Assert.assertNull(RpsSimulation.determineWinner(0, 0, 0));
    }

    @Test public void victoryConfettiInitializesWithExpectedAttributes() {
        RpsSimulation.VictoryConfetti c = new RpsSimulation.VictoryConfetti(
                100f, 200f, 15f, 35f, 8f, 14f, 45f, 90f, 1.2f, 8f,
                com.badlogic.gdx.graphics.Color.GOLD, 3.5f
        );
        assertEquals(100f, c.x, 0.001f);
        assertEquals(200f, c.y, 0.001f);
        assertEquals(15f, c.vx, 0.001f);
        assertEquals(35f, c.vy, 0.001f);
        assertEquals(8f, c.width, 0.001f);
        assertEquals(14f, c.height, 0.001f);
        assertEquals(0f, c.age, 0.001f);
        assertEquals(3.5f, c.maxAge, 0.001f);
    }

    @Test public void victorySoundResourceExistsOnClasspath() {
        java.net.URL soundUrl = getClass().getClassLoader().getResource("sounds/victory.wav");
        org.junit.Assert.assertNotNull("Victory sound WAV must be present on classpath", soundUrl);
    }
}
