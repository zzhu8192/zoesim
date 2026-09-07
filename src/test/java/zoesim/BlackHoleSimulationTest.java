package zoesim;

import org.junit.Test;
import zoesim.blackhole.BlackHolePhysics;
import zoesim.blackhole.BlackHoleSimulation;

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
        float requiredMass = (float)Math.pow((350f - 10f - 18f) / .32f, 2);
        assertTrue(BlackHoleSimulation.fillsField(requiredMass, 350f));
        assertEquals(10, BlackHoleSimulation.clampSeedCount(0));
        assertEquals(180, BlackHoleSimulation.clampSeedCount(180));
        assertEquals(360, BlackHoleSimulation.clampSeedCount(999));
    }

    @Test public void physicsModuleComputesConsistentValues() {
        assertEquals(BlackHoleSimulation.INITIAL_MASS, BlackHolePhysics.INITIAL_MASS, .0001f);
        assertEquals(BlackHoleSimulation.eventHorizon(BlackHolePhysics.INITIAL_MASS),
                BlackHolePhysics.eventHorizon(BlackHolePhysics.INITIAL_MASS), .0001f);
        assertEquals(BlackHoleSimulation.gravitationalParameter(10f),
                BlackHolePhysics.gravitationalParameter(10f), .0001f);
        assertEquals(1.5f, BlackHolePhysics.clampSpeed(1.5f), .0001f);
        assertEquals(.10f, BlackHolePhysics.clampSpeed(.01f), .0001f);
        assertEquals(10.0f, BlackHolePhysics.clampSpeed(15f), .0001f);
        assertEquals(1.0f, BlackHolePhysics.clampGrowthRate(1.0f), .0001f);
        assertEquals(0.2f, BlackHolePhysics.clampGrowthRate(0.05f), .0001f);
        assertEquals(8.0f, BlackHolePhysics.clampGrowthRate(12.0f), .0001f);
    }

    @Test public void individualAbsorptionProducesGradualHorizonGrowth() {
        float initialHorizon = BlackHolePhysics.eventHorizon(BlackHolePhysics.INITIAL_MASS);
        float afterStarAbsorption = BlackHolePhysics.eventHorizon(BlackHolePhysics.INITIAL_MASS + 55f);
        float delta = afterStarAbsorption - initialHorizon;
        assertTrue("Single absorption must only cause small sub-pixel horizon expansion", delta > 0f && delta < 0.20f);
    }

    @Test public void worldInitializationKeepsSafeMarginAndBoundedGrowth() {
        zoesim.blackhole.BlackHoleWorld world = new zoesim.blackhole.BlackHoleWorld(new zoesim.blackhole.BlackHoleAudio());
        world.resize(400f, 400f, 350f);
        world.reset();

        assertEquals(180, world.getCosmicParticles().size);
        for (zoesim.blackhole.CosmicParticle p : world.getCosmicParticles()) {
            float dist = (float)Math.sqrt(BlackHolePhysics.spatialDistanceSquared(
                    400f - p.position.x, 400f - p.position.y, -p.position.z));
            float disruption = world.horizon() + Math.max(1f, p.size * 0.72f);
            assertTrue("Particle should spawn well outside disruption radius", dist >= disruption + 20f);
            assertTrue("Particle mass must be non-negative and bounded", p.mass > 0f && p.mass <= 60f);
        }

        // Run 1 second of simulation (60 steps)
        for (int i = 0; i < 60; i++) {
            world.update(1f / 60f);
        }
        assertFalse("Simulation should not prematurely consume field", world.isFieldConsumed());
        assertTrue("Mass growth must remain stable without exponential runaway",
                world.getMass() < BlackHolePhysics.INITIAL_MASS * 1.5f);
    }

    @Test public void orbitDistributionExploresAll3DSpaceAndAccretionPlane() {
        zoesim.blackhole.BlackHoleWorld world = new zoesim.blackhole.BlackHoleWorld(new zoesim.blackhole.BlackHoleAudio());
        world.resize(400f, 400f, 350f);
        world.reset();

        int positiveZCount = 0;
        int negativeZCount = 0;
        int positiveVzCount = 0;
        int negativeVzCount = 0;
        int discCoPlanarCount = 0;

        float diskAngle = BlackHolePhysics.ACCRETION_DISK_ANGLE * com.badlogic.gdx.math.MathUtils.degreesToRadians;
        float discNx = -com.badlogic.gdx.math.MathUtils.sin(diskAngle);
        float discNy = com.badlogic.gdx.math.MathUtils.cos(diskAngle);
        float discNz = 0f;

        for (zoesim.blackhole.CosmicParticle p : world.getCosmicParticles()) {
            if (p.position.z > 20f) positiveZCount++;
            if (p.position.z < -20f) negativeZCount++;
            if (p.velocity.z > 10f) positiveVzCount++;
            if (p.velocity.z < -10f) negativeVzCount++;

            // Orbital plane normal via angular momentum vector L = r x v
            float rx = p.position.x - 400f;
            float ry = p.position.y - 400f;
            float rz = p.position.z;
            float lx = ry * p.velocity.z - rz * p.velocity.y;
            float ly = rz * p.velocity.x - rx * p.velocity.z;
            float lz = rx * p.velocity.y - ry * p.velocity.x;
            float lLen = (float)Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (lLen > 0.001f) {
                float nx = lx / lLen, ny = ly / lLen, nz = lz / lLen;
                float dot = Math.abs(nx * discNx + ny * discNy + nz * discNz);
                if (dot > 0.82f) {
                    discCoPlanarCount++;
                }
            }
        }

        assertTrue("Particles must span positive Z depth", positiveZCount >= 25);
        assertTrue("Particles must span negative Z depth", negativeZCount >= 25);
        assertTrue("Velocities must explore positive Z direction", positiveVzCount >= 20);
        assertTrue("Velocities must explore negative Z direction", negativeVzCount >= 20);
        assertTrue("A distinct population of particles must orbit co-planar with the accretion disc",
                discCoPlanarCount >= 25);
    }

    @Test public void growthFactorMultiplierIsFiveAndSpreadsEvenRadialDistribution() {
        assertEquals(5.0f, BlackHolePhysics.GROWTH_FACTOR_MULTIPLIER, 0.0001f);

        zoesim.blackhole.BlackHoleWorld world = new zoesim.blackhole.BlackHoleWorld(new zoesim.blackhole.BlackHoleAudio());
        world.resize(400f, 400f, 350f);
        world.reset();

        // Check radial distribution of initial cloud: particles must be present in inner, mid, and outer zones
        int innerZone = 0;
        int midZone = 0;
        int outerZone = 0;
        float horizon = world.horizon();

        for (zoesim.blackhole.CosmicParticle p : world.getCosmicParticles()) {
            float dist = (float)Math.sqrt(BlackHolePhysics.spatialDistanceSquared(
                    400f - p.position.x, 400f - p.position.y, -p.position.z));
            float offset = dist - horizon;
            if (offset < 100f) innerZone++;
            else if (offset < 220f) midZone++;
            else outerZone++;
        }

        assertTrue("Initial cloud must populate inner accretion zone for continuous accretion", innerZone >= 20);
        assertTrue("Initial cloud must populate mid orbital zone", midZone >= 30);
        assertTrue("Initial cloud must populate outer orbital zone", outerZone >= 30);
    }

    @Test public void simTimeIntegratesSpeedAndFreezesOnPause() {
        zoesim.blackhole.BlackHoleWorld world = new zoesim.blackhole.BlackHoleWorld(new zoesim.blackhole.BlackHoleAudio());
        world.resize(400f, 400f, 350f);
        world.reset();
        assertEquals(0f, world.getSimTime(), 0.0001f);

        // 1. Normal speed (1.0x)
        world.setSpeed(1.0f);
        world.update(0.1f);
        assertEquals(0.1f, world.getSimTime(), 0.001f);

        // 2. High speed (10.0x timescale)
        world.setSpeed(10.0f);
        world.update(0.1f);
        assertEquals(0.1f + 1.0f, world.getSimTime(), 0.001f);

        // 3. Low speed (0.2x timescale)
        world.setSpeed(0.2f);
        world.update(0.1f);
        assertEquals(1.1f + 0.02f, world.getSimTime(), 0.001f);

        // 4. Paused simulation must freeze simTime
        world.setPaused(true);
        float timeBeforePause = world.getSimTime();
        world.update(0.5f);
        assertEquals(timeBeforePause, world.getSimTime(), 0.0001f);

        // 5. Reset clears simTime
        world.reset();
        assertEquals(0f, world.getSimTime(), 0.0001f);
    }

    @Test public void stellarObjectInitialVelocityShowsPronouncedVariance() {
        zoesim.blackhole.BlackHoleWorld world = new zoesim.blackhole.BlackHoleWorld(new zoesim.blackhole.BlackHoleAudio());
        world.resize(400f, 400f, 350f);
        world.reset();

        float minRatio = Float.MAX_VALUE;
        float maxRatio = Float.MIN_VALUE;

        for (zoesim.blackhole.CosmicParticle p : world.getCosmicParticles()) {
            float rx = p.position.x - 400f;
            float ry = p.position.y - 400f;
            float rz = p.position.z;
            float r = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
            float baseCircularSpeed = (float)Math.sqrt(BlackHolePhysics.gravitationalParameter(world.getMass()) / r) * 2.25f;

            // Compute tangential speed
            float radialVel = (rx * p.velocity.x + ry * p.velocity.y + rz * p.velocity.z) / r;
            float totalSpeedSq = p.velocity.x * p.velocity.x + p.velocity.y * p.velocity.y + p.velocity.z * p.velocity.z;
            float tangSpeed = (float)Math.sqrt(Math.max(0f, totalSpeedSq - radialVel * radialVel));

            float speedRatio = tangSpeed / baseCircularSpeed;
            if (speedRatio < minRatio) minRatio = speedRatio;
            if (speedRatio > maxRatio) maxRatio = speedRatio;
        }

        float varianceRange = maxRatio - minRatio;
        assertTrue("Speed variance range must be significantly widened (>= 0.35)", varianceRange >= 0.35f);
        assertTrue("Lowest speed fraction should be below 0.85", minRatio <= 0.85f);
        assertTrue("Highest speed fraction should be above 1.15", maxRatio >= 1.15f);
    }
}
