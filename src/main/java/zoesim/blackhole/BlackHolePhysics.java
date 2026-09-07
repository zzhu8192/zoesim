package zoesim.blackhole;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/** Pure mathematical and physical formulas for black hole gravitation, lensing, and geometry. */
public final class BlackHolePhysics {
    public static final float GRAVITATIONAL_CONSTANT = 220f;
    public static final float INITIAL_MASS = 2200f;
    public static final float ACCRETION_DISK_ANGLE = -17f;
    public static final float JET_AXIS = (ACCRETION_DISK_ANGLE + 90f) * MathUtils.degreesToRadians;
    public static final float COSMIC_SPAWN_INTERVAL = .045f;
    public static final int MAX_COSMIC_PARTICLES = 900;
    public static final float CELESTIAL_DENSITY = .65f;
    public static final int BACKGROUND_STAR_COUNT = 260;
    public static final float GROWTH_FACTOR_MULTIPLIER = 5.0f;

    public static final Color[] CELESTIAL_PALETTE = {
            new Color(.38f, .55f, .32f, 1f), // 0: Terra (verdant green/ocean)
            new Color(.78f, .38f, .22f, 1f), // 1: Martian desert (rust red)
            new Color(.92f, .45f, .12f, 1f), // 2: Volcanic world (magma orange)
            new Color(.85f, .68f, .45f, 1f), // 3: Jovian giant (amber/cream)
            new Color(1.0f, .88f, .42f, 1f), // 4: Solar protostar (golden sun)
            new Color(.88f, .92f, .96f, 1f), // 5: Europa ice moon (pale frost)
            new Color(.58f, .55f, .52f, 1f), // 6: Chondrite asteroid (pockmarked stone)
            new Color(.72f, .52f, .42f, 1f), // 7: Plutonian dwarf (tholin red/ice)
            new Color(.28f, .48f, .72f, 1f), // 8: Azure ice giant (Neptune blue)
            new Color(.82f, .88f, .95f, 1f)  // 9: Comet (dusty ice)
    };

    private BlackHolePhysics() { }

    public static float gravitationalParameter(float mass) {
        return GRAVITATIONAL_CONSTANT * Math.max(0f, mass);
    }

    public static float gravitationalAcceleration(float mass, float distanceSquared, float softeningSquared) {
        return gravitationalParameter(mass) / Math.max(distanceSquared, softeningSquared);
    }

    public static float eventHorizon(float mass) {
        return 18f + (float)Math.sqrt(Math.max(0f, mass)) * .32f;
    }

    public static float particleMass(float radius) {
        float physicalRadius = Math.max(0f, radius);
        return physicalRadius * physicalRadius * CELESTIAL_DENSITY;
    }

    public static float lensedImageRadius(float sourceRadius, float einsteinRadius) {
        float source = Math.max(0f, sourceRadius);
        float lens = Math.max(0f, einsteinRadius);
        return .5f * (source + (float)Math.sqrt(source * source + 4f * lens * lens));
    }

    public static float spatialDistanceSquared(float dx, float dy, float dz) {
        return dx * dx + dy * dy + dz * dz;
    }

    public static float projectedBodyScale(float depth, float arenaRadius) {
        return MathUtils.clamp(1f + depth / (Math.max(1f, arenaRadius) * 3.5f), .74f, 1.26f);
    }

    public static boolean fillsField(float mass, float arenaRadius) {
        return eventHorizon(mass) >= arenaRadius - 10f;
    }

    public static int clampSeedCount(int count) {
        return MathUtils.clamp(count, 10, 360);
    }

    public static float clampSpeed(float value) {
        return MathUtils.clamp(value, .10f, 10.0f);
    }

    public static float clampGrowthRate(float value) {
        return MathUtils.clamp(value, 0.2f, 8.0f);
    }

    public static float randomUnit(int value) {
        float raw = MathUtils.sin(value * 12.9898f + 78.233f) * 43758.547f;
        return raw - (float)Math.floor(raw);
    }

    public static float gaussian(float value, float center, float width) {
        float normalized = (value - center) / width;
        return (float)Math.exp(-normalized * normalized);
    }
}
