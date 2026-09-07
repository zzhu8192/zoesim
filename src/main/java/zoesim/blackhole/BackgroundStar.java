package zoesim.blackhole;

/** Background field star perturbed by gravitational lensing around the black hole. */
public final class BackgroundStar {
    public final float radius;
    public final float angle;
    public final float size;
    public final float brightness;
    public final float phase;
    public final float angularMotion;
    public final float temperature;
    public final int shape;

    public BackgroundStar(float radius, float angle, float size, float brightness, float phase,
                          float angularMotion, float temperature, int shape) {
        this.radius = radius;
        this.angle = angle;
        this.size = size;
        this.brightness = brightness;
        this.phase = phase;
        this.angularMotion = angularMotion;
        this.temperature = temperature;
        this.shape = shape;
    }
}
