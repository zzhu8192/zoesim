package zoesim.blackhole;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

/** Orbiting celestial body (star, gas giant, planet, asteroid, or comet) subject to gravitational pull. */
public final class CosmicParticle {
    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final float hue;
    public final float size;
    public final float mass;
    public final float spin;
    public final int bodyType;
    public final Color color;
    public float age;
    public float wobble;

    public CosmicParticle(float x, float y, float z, float vx, float vy, float vz, float hue, float size,
                          float massScale, int bodyType, Color color, float spin) {
        position.set(x, y, z);
        velocity.set(vx, vy, vz);
        this.hue = hue;
        this.size = size;
        this.mass = BlackHolePhysics.particleMass(size) * massScale;
        this.bodyType = bodyType;
        this.color = color;
        this.spin = spin;
    }
}
