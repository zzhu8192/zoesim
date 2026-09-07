package zoesim;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlackHoleSimulationTest {
    @Test public void gravityUsesMassAndInverseSquareDistance() {
        assertEquals(440f, BlackHoleSimulation.gravitationalParameter(2f), .0001f);
        float near = BlackHoleSimulation.gravitationalAcceleration(10f, 100f, 1f);
        float twiceAsFar = BlackHoleSimulation.gravitationalAcceleration(10f, 400f, 1f);
        assertEquals(near / 4f, twiceAsFar, .0001f);
    }

    @Test public void gravitySofteningCapsNearCenterAcceleration() {
        float atCenter = BlackHoleSimulation.gravitationalAcceleration(10f, 0f, 900f);
        float softened = BlackHoleSimulation.gravitationalAcceleration(10f, 400f, 900f);
        assertEquals(atCenter, softened, .0001f);
    }

    @Test public void particleMassScalesWithCrossSectionalArea() {
        float small = BlackHoleSimulation.particleMass(2f);
        assertEquals(small * 4f, BlackHoleSimulation.particleMass(4f), .0001f);
        assertEquals(0f, BlackHoleSimulation.particleMass(-2f), .0001f);
    }

    @Test public void eventHorizonGrowsMonotonically() {
        float initial = BlackHoleSimulation.eventHorizon(BlackHoleSimulation.INITIAL_MASS);
        assertTrue(BlackHoleSimulation.eventHorizon(BlackHoleSimulation.INITIAL_MASS + 500f) > initial);
        assertTrue(BlackHoleSimulation.eventHorizon(BlackHoleSimulation.INITIAL_MASS + 5000f) > initial);
    }

    @Test public void pointMassLensMovesThePrimaryImageOutward() {
        assertEquals(40f, BlackHoleSimulation.lensedImageRadius(40f, 0f), .0001f);
        assertEquals(20f, BlackHoleSimulation.lensedImageRadius(0f, 20f), .0001f);
        assertTrue(BlackHoleSimulation.lensedImageRadius(40f, 20f) > 40f);
    }

    @Test public void spatialDistanceIncludesDepth() {
        assertEquals(169f, BlackHoleSimulation.spatialDistanceSquared(3f, 4f, 12f), .0001f);
        assertTrue(BlackHoleSimulation.spatialDistanceSquared(3f, 4f, 12f)
                > BlackHoleSimulation.spatialDistanceSquared(3f, 4f, 0f));
    }

    @Test public void projectedBodiesConveyFrontAndBackDepth() {
        assertEquals(1f, BlackHoleSimulation.projectedBodyScale(0f, 300f), .0001f);
        assertTrue(BlackHoleSimulation.projectedBodyScale(100f, 300f) > 1f);
        assertTrue(BlackHoleSimulation.projectedBodyScale(-100f, 300f) < 1f);
    }

    @Test public void fieldCompletionAndSeedBoundsAreDeterministic() {
        assertFalse(BlackHoleSimulation.fillsField(BlackHoleSimulation.INITIAL_MASS, 350f));
        float requiredMass = (float)Math.pow((350f - 10f - 34f) / .68f, 2);
        assertTrue(BlackHoleSimulation.fillsField(requiredMass, 350f));
        assertEquals(10, BlackHoleSimulation.clampSeedCount(0));
        assertEquals(180, BlackHoleSimulation.clampSeedCount(180));
        assertEquals(360, BlackHoleSimulation.clampSeedCount(999));
    }
}
