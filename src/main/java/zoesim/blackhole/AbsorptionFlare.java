package zoesim.blackhole;

import com.badlogic.gdx.graphics.Color;

import java.util.Random;

/** High-energy flare and spark burst produced by tidal disruption at the event horizon. */
public final class AbsorptionFlare {
    public static final int SPARK_COUNT = 14;
    public final float angle;
    public final float radius;
    public final float size;
    public final float life;
    public final Color color;
    public final float[] spread = new float[SPARK_COUNT];
    public final float[] speed = new float[SPARK_COUNT];
    public final float[] scale = new float[SPARK_COUNT];
    public final float[] heat = new float[SPARK_COUNT];
    public float age;

    public AbsorptionFlare(float angle, float radius, float size, float life, Color color, Random random) {
        this.angle = angle;
        this.radius = radius;
        this.size = size;
        this.life = life;
        this.color = color;
        for (int i = 0; i < SPARK_COUNT; i++) {
            spread[i] = (random.nextFloat() + random.nextFloat() - 1f) * .82f;
            speed[i] = 18f + random.nextFloat() * (42f + size * 2.5f);
            scale[i] = .35f + random.nextFloat() * .85f;
            heat[i] = random.nextFloat();
        }
    }
}
