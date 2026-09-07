package zoesim.blackhole;

import com.badlogic.gdx.math.Vector2;

/** Relativistic polar jet particle ejected from the black hole's rotational poles. */
public final class JetParticle {
    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    public final float size;
    public final float life;
    public final float brightness;
    public final float spectrumBias;
    public final boolean isShockKnot;
    public final float expandRate;
    public final float rotation;
    public float age;

    public JetParticle(float x, float y, float vx, float vy, float size, float life, float brightness,
                       float spectrumBias) {
        this(x, y, vx, vy, size, life, brightness, spectrumBias, false, 28f, 0f);
    }

    public JetParticle(float x, float y, float vx, float vy, float size, float life, float brightness,
                       float spectrumBias, boolean isShockKnot, float expandRate, float rotation) {
        position.set(x, y);
        velocity.set(vx, vy);
        this.size = size;
        this.life = life;
        this.brightness = brightness;
        this.spectrumBias = spectrumBias;
        this.isShockKnot = isShockKnot;
        this.expandRate = expandRate;
        this.rotation = rotation;
    }
}
