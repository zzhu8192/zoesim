package zoesim;

/** Newtonian mass and radius calculations for the black-hole simulation. */
public final class BlackHoleSimulation {
    public static final float GRAVITATIONAL_CONSTANT = 220f;
    public static final float INITIAL_MASS = 2200f;

    private BlackHoleSimulation() { }

    public static float gravitationalParameter(float mass) { return GRAVITATIONAL_CONSTANT * mass; }
    public static float eventHorizon(float mass) { return 34f + (float)Math.sqrt(mass) * .68f; }
    public static float particleMass(float radius) { return radius * radius * 6f; }
}
