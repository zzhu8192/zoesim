package zoesim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;
import java.util.Random;

/** Complete black-hole simulation: physics, state, input, rendering, and assets. */
public final class BlackHoleSimulation implements ArenaSimulation {
    public static final float GRAVITATIONAL_CONSTANT = 220f;
    public static final float INITIAL_MASS = 2200f;
    static final float ACCRETION_DISK_ANGLE = -17f;
    static final float JET_AXIS = (ACCRETION_DISK_ANGLE + 90f) * MathUtils.degreesToRadians;
    static final float COSMIC_SPAWN_INTERVAL = .045f;
    static final int MAX_COSMIC_PARTICLES = 900;
    private static final float CELESTIAL_DENSITY = .65f;
    private static final int BACKGROUND_STAR_COUNT = 260;
    private static final Color[] CELESTIAL_PALETTE = {
            new Color(.35f, .26f, .19f, 1f), new Color(.43f, .64f, .78f, 1f),
            new Color(.77f, .56f, .32f, 1f), new Color(.86f, .88f, .91f, 1f),
            new Color(.48f, .25f, .16f, 1f), new Color(.38f, .52f, .61f, 1f),
            new Color(.67f, .82f, .91f, 1f)
    };

    private static final class CosmicParticle {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        final float hue, size, mass, spin;
        final int bodyType;
        final Color color;
        float age, wobble;
        CosmicParticle(float x, float y, float vx, float vy, float hue, float size, float massScale,
                       int bodyType, Color color, float spin) {
            position.set(x, y); velocity.set(vx, vy); this.hue = hue; this.size = size;
            mass = particleMass(size) * massScale;
            this.bodyType = bodyType; this.color = color; this.spin = spin;
        }
    }

    private static final class BackgroundStar {
        final float radius, angle, size, brightness, phase, angularMotion, temperature;
        final int shape;
        BackgroundStar(float radius, float angle, float size, float brightness, float phase,
                       float angularMotion, float temperature, int shape) {
            this.radius = radius; this.angle = angle; this.size = size; this.brightness = brightness;
            this.phase = phase; this.angularMotion = angularMotion; this.temperature = temperature; this.shape = shape;
        }
    }

    private static final class AbsorptionFlare {
        static final int SPARK_COUNT = 14;
        final float angle, radius, size, life;
        final Color color;
        final float[] spread = new float[SPARK_COUNT];
        final float[] speed = new float[SPARK_COUNT];
        final float[] scale = new float[SPARK_COUNT];
        final float[] heat = new float[SPARK_COUNT];
        float age;
        AbsorptionFlare(float angle, float radius, float size, float life, Color color, Random random) {
            this.angle = angle; this.radius = radius; this.size = size; this.life = life; this.color = color;
            for (int i = 0; i < SPARK_COUNT; i++) {
                spread[i] = (random.nextFloat() + random.nextFloat() - 1f) * .82f;
                speed[i] = 18f + random.nextFloat() * (42f + size * 2.5f);
                scale[i] = .35f + random.nextFloat() * .85f;
                heat[i] = random.nextFloat();
            }
        }
    }

    private static final class JetParticle {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        final float size, life, brightness;
        final float spectrumBias;
        float age;
        JetParticle(float x, float y, float vx, float vy, float size, float life, float brightness,
                    float spectrumBias) {
            position.set(x, y); velocity.set(vx, vy); this.size = size; this.life = life;
            this.brightness = brightness; this.spectrumBias = spectrumBias;
        }
    }

    private final Random random = new Random();
    private final Array<CosmicParticle> cosmicParticles = new Array<>();
    private final Array<JetParticle> jetParticles = new Array<>();
    private final Array<AbsorptionFlare> absorptionFlares = new Array<>();
    private final BackgroundStar[] backgroundStars = new BackgroundStar[BACKGROUND_STAR_COUNT];
    private final Color spectralColor = new Color();
    private final Sound absorbSound;
    private final Sound ambience;
    private final Texture blackHoleSprite;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout layout;
    private float arenaX, arenaY, arenaRadius, elapsed, speed = 1f;
    private float mass = INITIAL_MASS, nextAbsorbSound, spawnTimer, jetTimer;
    private int absorbedParticles, startCount = 180, heldCountDelta;
    private float heldCountTime, heldCountRepeat;
    private String countValue = "";
    private long ambientLoopId = -1;
    private boolean paused, fieldConsumed, draggingSpeed, editingCount, menuRequested;

    public BlackHoleSimulation() {
        absorbSound = Gdx.audio.newSound(Gdx.files.internal("sounds/black-hole-absorb.ogg"));
        ambience = Gdx.audio.newSound(Gdx.files.internal("sounds/space-ambience.ogg"));
        blackHoleSprite = createBlackHoleSprite();
        createBackgroundStars();
    }

    public static float gravitationalParameter(float mass) {
        return GRAVITATIONAL_CONSTANT * Math.max(0f, mass);
    }

    public static float gravitationalAcceleration(float mass, float distanceSquared, float softeningSquared) {
        return gravitationalParameter(mass) / Math.max(distanceSquared, softeningSquared);
    }

    public static float eventHorizon(float mass) {
        return 34f + (float)Math.sqrt(Math.max(0f, mass)) * .68f;
    }

    public static float particleMass(float radius) {
        float physicalRadius = Math.max(0f, radius);
        return physicalRadius * physicalRadius * CELESTIAL_DENSITY;
    }

    static float lensedImageRadius(float sourceRadius, float einsteinRadius) {
        float source = Math.max(0f, sourceRadius);
        float lens = Math.max(0f, einsteinRadius);
        return .5f * (source + (float)Math.sqrt(source * source + 4f * lens * lens));
    }

    static boolean fillsField(float mass, float arenaRadius) { return eventHorizon(mass) >= arenaRadius - 10f; }
    static int clampSeedCount(int count) { return MathUtils.clamp(count, 10, 360); }

    @Override public void enter(float arenaX, float arenaY, float arenaRadius) {
        resize(arenaX, arenaY, arenaRadius);
        menuRequested = false;
        reset();
    }

    @Override public void resize(float arenaX, float arenaY, float arenaRadius) {
        this.arenaX = arenaX; this.arenaY = arenaY; this.arenaRadius = arenaRadius;
    }

    @Override public void update(float dt) {
        if (paused || fieldConsumed) return;
        nextAbsorbSound -= dt;
        float simulationDt = dt * speed;
        spawnTimer += simulationDt;
        jetTimer += simulationDt;
        float horizon = horizon();
        float gravitationalParameter = gravitationalParameter(mass);
        while (spawnTimer > COSMIC_SPAWN_INTERVAL && cosmicParticles.size < MAX_COSMIC_PARTICLES) {
            spawnTimer -= COSMIC_SPAWN_INTERVAL;
            spawnCosmicParticle(true);
        }
        if (jetTimer > .065f) { jetTimer = 0f; emitJets(horizon); }
        for (Iterator<CosmicParticle> it = cosmicParticles.iterator(); it.hasNext();) {
            CosmicParticle particle = it.next();
            float dx = arenaX - particle.position.x, dy = arenaY - particle.position.y;
            float distanceSquared = dx * dx + dy * dy;
            float distance = (float)Math.sqrt(distanceSquared);
            float disruptionRadius = horizon + Math.max(1f, particle.size * .72f);
            if (distance < disruptionRadius) { absorb(particle); it.remove(); continue; }
            float gravity = gravitationalAcceleration(mass, distanceSquared, 900f);
            particle.velocity.add(dx / distance * gravity * simulationDt, dy / distance * gravity * simulationDt);
            particle.velocity.scl(Math.max(.92f, 1f - simulationDt * .018f));
            particle.velocity.limit(340f);
            particle.position.mulAdd(particle.velocity, simulationDt);
            particle.age += simulationDt;
            particle.wobble += particle.spin * simulationDt;
            dx = particle.position.x - arenaX; dy = particle.position.y - arenaY;
            distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance < disruptionRadius) { absorb(particle); it.remove(); continue; }
            float boundary = arenaRadius - particle.size - 5f;
            if (distance > boundary) {
                float nx = dx / distance, ny = dy / distance;
                particle.position.set(arenaX + nx * boundary, arenaY + ny * boundary);
                float outward = particle.velocity.x * nx + particle.velocity.y * ny;
                if (outward > 0) particle.velocity.mulAdd(new Vector2(nx, ny), -1.75f * outward);
                particle.velocity.scl(.92f);
            }
        }
        for (Iterator<JetParticle> it = jetParticles.iterator(); it.hasNext();) {
            JetParticle jet = it.next();
            jet.position.mulAdd(jet.velocity, simulationDt);
            jet.velocity.scl(1f + simulationDt * .08f);
            jet.age += simulationDt;
            if (jet.age > jet.life || jet.position.dst2(arenaX, arenaY) > arenaRadius * arenaRadius) it.remove();
        }
        for (Iterator<AbsorptionFlare> it = absorptionFlares.iterator(); it.hasNext();) {
            AbsorptionFlare flare = it.next();
            flare.age += simulationDt;
            if (flare.age > flare.life) it.remove();
        }
        if (fillsField(mass, arenaRadius)) { fieldConsumed = true; paused = true; stopAmbience(); }
    }

    private void reset() {
        cosmicParticles.clear(); jetParticles.clear(); absorptionFlares.clear();
        absorbedParticles = 0; mass = INITIAL_MASS;
        nextAbsorbSound = 0f; spawnTimer = 0f; jetTimer = 0f;
        fieldConsumed = false; paused = false;
        for (int i = 0; i < startCount; i++) spawnCosmicParticle(false);
        startAmbience();
    }

    private void spawnCosmicParticle(boolean atBoundary) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float horizon = horizon();
        int roll = random.nextInt(100);
        int type = roll < 18 ? 0 : roll < 32 ? 1 : roll < 44 ? 2 : roll < 62 ? 3 : roll < 87 ? 4 : roll < 94 ? 5 : 6;
        float sample = random.nextFloat(), size, massScale;
        switch (type) {
            case 0: size = 3f + sample * sample * 7f; massScale = 1f; break;
            case 1: size = 2.5f + sample * 6f; massScale = .82f; break;
            case 2: size = 6f + sample * 9f; massScale = .55f; break;
            case 3: size = 1.4f + sample * sample * 4f; massScale = 12f; break;
            case 4: size = .8f + sample * sample * 5.5f; massScale = 1.1f; break;
            case 5: size = 13f + sample * 20f; massScale = .04f; break;
            default: size = 1.2f + sample * 4f; massScale = .5f; break;
        }
        float outerRadius = arenaRadius - Math.max(8f, size * .72f + 4f);
        float innerRadius = horizon + Math.max(18f, size * .42f);
        float radius = atBoundary ? outerRadius
                : innerRadius + (float)Math.sqrt(random.nextFloat()) * Math.max(1f, outerRadius - innerRadius);
        float orbital = (float)Math.sqrt(gravitationalParameter(mass) / radius) * (.53f + random.nextFloat() * .26f);
        float inward = -7f - random.nextFloat() * 16f;
        float x = arenaX + MathUtils.cos(angle) * radius, y = arenaY + MathUtils.sin(angle) * radius;
        float vx = MathUtils.cos(angle + MathUtils.HALF_PI) * orbital + MathUtils.cos(angle) * inward;
        float vy = MathUtils.sin(angle + MathUtils.HALF_PI) * orbital + MathUtils.sin(angle) * inward;
        float spin = (.65f + random.nextFloat() * 3.2f) * (random.nextBoolean() ? 1f : -1f);
        cosmicParticles.add(new CosmicParticle(x, y, vx, vy, random.nextFloat() * 360f, size, massScale,
                type, CELESTIAL_PALETTE[type], spin));
    }

    private void emitJets(float horizon) {
        for (int pole = -1; pole <= 1; pole += 2) for (int i = 0; i < 4; i++) {
            float spread = i < 3
                    ? (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) * .075f
                    : (random.nextBoolean() ? 1f : -1f) * (.065f + random.nextFloat() * .085f);
            float angle = JET_AXIS + (pole < 0 ? MathUtils.PI : 0f) + spread;
            float radius = horizon * (1.02f + random.nextFloat() * .12f);
            float velocity = 225f + random.nextFloat() * 135f;
            jetParticles.add(new JetParticle(arenaX + MathUtils.cos(angle) * radius,
                    arenaY + MathUtils.sin(angle) * radius, MathUtils.cos(angle) * velocity,
                    MathUtils.sin(angle) * velocity, .8f + random.nextFloat() * 1.9f,
                    .72f + random.nextFloat() * .62f, .68f + random.nextFloat() * .32f,
                    MathUtils.clamp(.72f - Math.abs(spread) * 2.8f + random.nextFloat() * .28f, 0f, 1f)));
        }
    }

    private float horizon() { return eventHorizon(mass); }

    private void absorb(CosmicParticle particle) {
        mass += particle.mass;
        absorbedParticles++;
        float angle = MathUtils.atan2(particle.position.y - arenaY, particle.position.x - arenaX);
        float flareSize = particle.bodyType == 5 ? MathUtils.clamp(particle.size * .15f, 2f, 4.5f)
                : particle.bodyType == 2 ? MathUtils.clamp(particle.size * .38f, 1.8f, 5.5f)
                : MathUtils.clamp(particle.size, 1.1f, 6f);
        absorptionFlares.add(new AbsorptionFlare(angle, horizon() * 1.04f, flareSize,
                .42f + Math.min(.36f, flareSize * .045f), particle.color, random));
        if (absorptionFlares.size > 140) absorptionFlares.removeIndex(0);
        if (nextAbsorbSound <= 0f) {
            absorbSound.play(.22f, .38f + Math.min(.26f, particle.size * .04f), 0f);
            nextAbsorbSound = .12f;
        }
    }

    private void startAmbience() { if (ambientLoopId < 0) ambientLoopId = ambience.loop(.075f, .52f, 0f); }
    private void stopAmbience() { if (ambientLoopId >= 0) { ambience.stop(ambientLoopId); ambientLoopId = -1; } }

    @Override public void render(float elapsed, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font, GlyphLayout layout) {
        this.elapsed = elapsed; this.shapes = shapes; this.batch = batch; this.font = font; this.layout = layout;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.003f, .006f, .014f, 1f); shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setColor(.0015f, .003f, .009f, 1f); shapes.circle(arenaX, arenaY, arenaRadius);
        drawStarField();
        drawJetBeams(horizon());
        drawAccretionLights(horizon(), false);
        for (CosmicParticle particle : cosmicParticles) drawCelestialBody(particle);
        drawJetParticles();
        shapes.end();
        batch.begin();
        float horizon = horizon();
        float size = horizon * 5.35f + MathUtils.sin(elapsed * 1.35f) * 1.5f;
        batch.draw(blackHoleSprite, arenaX - size * .5f, arenaY - size * .5f, size * .5f, size * .5f,
                size, size, 1f, 1f, ACCRETION_DISK_ANGLE, 0, 0,
                blackHoleSprite.getWidth(), blackHoleSprite.getHeight(), false, false);
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawAccretionLights(horizon, true);
        drawAbsorptionFlares(horizon);
        shapes.end();
        if (fieldConsumed) {
            batch.begin(); font.setColor(.95f, .78f, 1f, 1f); font.getData().setScale(1.45f);
            centered("EVENT HORIZON FILLS THE FIELD", arenaX, arenaY - horizon - 24f);
            batch.end();
        }
        drawInterface();
    }

    private void drawJetParticles() {
        for (JetParticle jet : jetParticles) {
            float fade = MathUtils.clamp(1f - jet.age / jet.life, 0f, 1f) * jet.brightness;
            float energy = MathUtils.clamp((1f - jet.age / jet.life) * .72f + jet.spectrumBias * .38f, 0f, 1f);
            float inverseSpeed = 1f / Math.max(1f, jet.velocity.len());
            float tail = 7f + jet.size * 4f;
            float tailX = jet.position.x - jet.velocity.x * inverseSpeed * tail;
            float tailY = jet.position.y - jet.velocity.y * inverseSpeed * tail;
            setSpectralColor(energy - .22f, fade * .13f, .88f);
            shapes.rectLine(tailX, tailY, jet.position.x, jet.position.y, jet.size * 3.6f);
            setSpectralColor(energy, fade * .56f, .76f);
            shapes.rectLine(tailX, tailY, jet.position.x, jet.position.y, Math.max(.7f, jet.size * 1.35f));
            setSpectralColor(energy + .22f, fade * .94f, .34f);
            shapes.circle(jet.position.x, jet.position.y, Math.max(.55f, jet.size * .48f), 10);
        }
    }

    /** False-color synchrotron spectrum: cooled red/gold edges through cyan/blue to a white-hot core. */
    private void setSpectralColor(float energy, float alpha, float saturationScale) {
        float normalized = MathUtils.clamp(energy, 0f, 1f);
        float hue = 8f + normalized * 258f;
        float saturation = (.88f - normalized * .72f) * saturationScale;
        spectralColor.fromHsv(hue, MathUtils.clamp(saturation, .08f, .9f), 1f);
        spectralColor.a = alpha;
        shapes.setColor(spectralColor);
    }

    private void drawStarField() {
        float horizon = horizon(), einsteinRadius = horizon * 1.62f;
        for (BackgroundStar star : backgroundStars) {
            float sourceAngle = star.angle + elapsed * star.angularMotion;
            float sourceRadius = arenaRadius * star.radius + MathUtils.sin(elapsed * .045f + star.phase) * arenaRadius * .0025f;
            float imageRadius = lensedImageRadius(sourceRadius, einsteinRadius);
            if (imageRadius > arenaRadius * .985f) continue;
            float root = 2f * imageRadius - sourceRadius;
            float magnification = 1f + Math.min(.85f, einsteinRadius * einsteinRadius / Math.max(1f, sourceRadius * sourceRadius) * .22f);
            float twinkle = star.brightness * (.76f + .24f * MathUtils.sin(elapsed * (.45f + star.brightness * .9f) + star.phase));
            float red = star.temperature < .32f ? 1f : star.temperature > .72f ? .64f : .88f;
            float green = star.temperature < .32f ? .68f : star.temperature > .72f ? .80f : .90f;
            float blue = star.temperature < .32f ? .43f : 1f;
            drawBackgroundStar(arenaX + MathUtils.cos(sourceAngle) * imageRadius,
                    arenaY + MathUtils.sin(sourceAngle) * imageRadius,
                    star.size * magnification, red, green, blue, twinkle, star.shape);
            float secondaryRadius = .5f * (root - sourceRadius);
            if (sourceRadius < einsteinRadius * 2.1f && secondaryRadius > horizon * 1.015f) {
                float alpha = twinkle * .18f * (1f - sourceRadius / (einsteinRadius * 2.1f));
                drawBackgroundStar(arenaX - MathUtils.cos(sourceAngle) * secondaryRadius,
                        arenaY - MathUtils.sin(sourceAngle) * secondaryRadius,
                        Math.max(.35f, star.size * .55f), red, green, blue, alpha, 0);
            }
        }
    }

    private void drawBackgroundStar(float x, float y, float size, float red, float green, float blue,
                                    float alpha, int shape) {
        shapes.setColor(red * .35f, green * .35f, blue * .42f, alpha * .18f);
        shapes.circle(x, y, size * 2.7f, 12);
        shapes.setColor(red, green, blue, alpha);
        if (shape == 2 && size > .9f) {
            shapes.rectLine(x - size * 2.5f, y, x + size * 2.5f, y, Math.max(.28f, size * .24f));
            shapes.rectLine(x, y - size * 2.5f, x, y + size * 2.5f, Math.max(.28f, size * .24f));
            shapes.circle(x, y, size * .72f, 10);
        } else if (shape == 1 && size > .65f) {
            shapes.triangle(x, y + size * 1.35f, x - size * .72f, y, x + size * .72f, y);
            shapes.triangle(x, y - size * 1.35f, x - size * .72f, y, x + size * .72f, y);
        } else shapes.circle(x, y, Math.max(.28f, size), 9);
    }

    private void drawAccretionLights(float horizon, boolean front) {
        float rotation = ACCRETION_DISK_ANGLE * MathUtils.degreesToRadians;
        float cosRotation = MathUtils.cos(rotation), sinRotation = MathUtils.sin(rotation);
        for (int lane = 0; lane < 5; lane++) {
            int knotCount = 28 + lane * 8;
            float orbit = horizon * (1.17f + lane * .19f), flatten = .16f + lane * .022f;
            for (int i = 0; i < knotCount; i++) {
                float randomness = randomUnit(i + lane * 101);
                float orbitSpeed = .78f + randomUnit(i * 7 + lane * 307) * .58f - lane * .08f;
                float phase = i * MathUtils.PI2 / knotCount + (randomness - .5f) * .17f
                        + elapsed * orbitSpeed + lane * 1.41f;
                boolean onFront = MathUtils.sin(phase) < 0f;
                if (onFront != front) continue;
                float radialNoise = 1f + (randomUnit(i * 13 + lane * 59) - .5f) * .10f
                        + MathUtils.sin(elapsed * .63f + i * 2.7f) * .018f;
                float localX = MathUtils.cos(phase) * orbit * radialNoise;
                float approaching = MathUtils.clamp(.58f - localX / (orbit * 2.1f), .18f, 1f);
                float temperature = MathUtils.clamp(.18f + approaching * .66f
                        + (randomUnit(i * 19 + lane * 83) - .5f) * .30f, 0f, 1f);
                float knot = .30f + randomUnit(i * 23 + lane * 191) * .70f;
                float alpha = (.12f + knot * .46f) * approaching * (front ? 1f : .38f);
                int trailPoints = 2 + (int)(randomUnit(i * 31 + lane * 17) * 4f);
                for (int trail = trailPoints - 1; trail >= 0; trail--) {
                    float trailPhase = phase - trail * (.018f + knot * .010f);
                    float trailX = MathUtils.cos(trailPhase) * orbit * radialNoise;
                    float phaseSine = MathUtils.sin(trailPhase);
                    float trailY = phaseSine * orbit * flatten * radialNoise;
                    if (!front) {
                        // Light from the far side is bent into the high arch seen above the shadow.
                        float lensLift = horizon * (.34f + lane * .025f)
                                * (float)Math.pow(Math.max(0f, phaseSine), .58f);
                        trailY += lensLift;
                    }
                    float x = arenaX + trailX * cosRotation - trailY * sinRotation;
                    float y = arenaY + trailX * sinRotation + trailY * cosRotation;
                    float trailFade = 1f - trail / (float)(trailPoints + 1);
                    float red = 1f;
                    float green = .60f + temperature * .39f;
                    float blue = .52f + temperature * temperature * .47f;
                    shapes.setColor(red, green, blue, alpha * trailFade * .16f);
                    shapes.circle(x, y, 1.0f + knot * 1.75f, 10);
                    shapes.setColor(1f, Math.min(1f, green + .12f), Math.min(1f, blue + .18f),
                            alpha * trailFade);
                    shapes.circle(x, y, .20f + knot * .54f, 8);
                }
            }
        }
    }

    private void drawAbsorptionFlares(float horizon) {
        for (AbsorptionFlare flare : absorptionFlares) {
            float progress = MathUtils.clamp(flare.age / flare.life, 0f, 1f);
            float baseAngle = flare.angle + progress * .62f;
            float radialX = MathUtils.cos(baseAngle), radialY = MathUtils.sin(baseAngle);
            float tangentX = -radialY, tangentY = radialX;
            float originRadius = Math.max(horizon * 1.005f, flare.radius * (1f - progress * .055f));
            float originX = arenaX + radialX * originRadius;
            float originY = arenaY + radialY * originRadius;
            for (int i = 0; i < AbsorptionFlare.SPARK_COUNT; i++) {
                float delay = i * .012f;
                float sparkAge = Math.max(0f, flare.age - delay);
                if (sparkAge <= 0f) continue;
                float lifeProgress = MathUtils.clamp(sparkAge / flare.life, 0f, 1f);
                float fade = (1f - lifeProgress) * (1f - lifeProgress);
                float along = flare.speed[i] * sparkAge * (1f + flare.spread[i] * .16f);
                float radialTravel = flare.spread[i] * flare.speed[i] * sparkAge * .34f
                        - 34f * sparkAge * sparkAge;
                float x = originX + tangentX * along + radialX * radialTravel;
                float y = originY + tangentY * along + radialY * radialTravel;
                float tail = 1.5f + flare.speed[i] * .038f;
                float heat = MathUtils.clamp(flare.heat[i] + (1f - lifeProgress) * .52f, 0f, 1f);
                float red = Math.max(.78f, flare.color.r);
                float green = MathUtils.lerp(.20f + flare.color.g * .32f, .96f, heat);
                float blue = MathUtils.lerp(.035f + flare.color.b * .18f, .82f, heat * heat);
                float sparkSize = Math.max(.32f, flare.size * flare.scale[i] * .24f);
                shapes.setColor(red, green, blue, fade * .10f);
                shapes.circle(x, y, sparkSize * 3.2f, 10);
                shapes.setColor(red, green, blue, fade * .66f);
                shapes.rectLine(x - tangentX * tail, y - tangentY * tail, x, y, Math.max(.3f, sparkSize * .62f));
                shapes.setColor(1f, .97f, .86f, fade * .92f);
                shapes.circle(x, y, sparkSize * .38f, 7);
            }
        }
    }

    private static float randomUnit(int value) {
        float raw = MathUtils.sin(value * 12.9898f + 78.233f) * 43758.547f;
        return raw - (float)Math.floor(raw);
    }

    private void drawJetBeams(float horizon) {
        float axisX = MathUtils.cos(JET_AXIS), axisY = MathUtils.sin(JET_AXIS);
        float sideX = -axisY, sideY = axisX;
        for (int pole = -1; pole <= 1; pole += 2) {
            float startX = arenaX + axisX * pole * horizon * .84f;
            float startY = arenaY + axisY * pole * horizon * .84f;
            float endX = arenaX + axisX * pole * arenaRadius * .92f;
            float endY = arenaY + axisY * pole * arenaRadius * .92f;
            float pulse = .72f + .12f * MathUtils.sin(elapsed * 3.1f + pole);
            setSpectralColor(.12f, .024f * pulse, 1f);
            shapes.triangle(startX + sideX * horizon * .10f, startY + sideY * horizon * .10f,
                    endX + sideX * 22f, endY + sideY * 22f, endX - sideX * 22f, endY - sideY * 22f);
            setSpectralColor(.42f, .032f * pulse, .92f);
            shapes.triangle(startX + sideX * horizon * .070f, startY + sideY * horizon * .070f,
                    endX + sideX * 13f, endY + sideY * 13f, endX - sideX * 13f, endY - sideY * 13f);
            setSpectralColor(.72f, .050f * pulse, .76f);
            shapes.triangle(startX + sideX * horizon * .035f, startY + sideY * horizon * .035f,
                    endX + sideX * 6f, endY + sideY * 6f, endX - sideX * 6f, endY - sideY * 6f);
            setSpectralColor(.98f, .065f * pulse, .34f);
            shapes.triangle(startX + sideX * horizon * .014f, startY + sideY * horizon * .014f,
                    endX + sideX * 2f, endY + sideY * 2f, endX - sideX * 2f, endY - sideY * 2f);
        }
    }

    private void drawCelestialBody(CosmicParticle particle) {
        float shimmer = .82f + .18f * MathUtils.sin(particle.age * 3f + particle.hue);
        Color color = particle.color;
        float x = particle.position.x, y = particle.position.y, size = particle.size;
        if (particle.bodyType == 5) {
            float breathe = .92f + .10f * MathUtils.sin(particle.age * .72f + particle.hue);
            for (int i = 7; i >= 0; i--) {
                float angle = particle.wobble * .055f + i * 2.3999632f;
                float offset = size * (.08f + i * .055f), cloudSize = size * breathe * (.52f - i * .025f);
                float alpha = (.035f + (7 - i) * .009f) * shimmer, warm = i % 3 == 0 ? .18f : 0f;
                shapes.setColor(color.r + warm, color.g * (.82f + i * .018f), color.b, alpha);
                shapes.circle(x + MathUtils.cos(angle) * offset, y + MathUtils.sin(angle) * offset * .58f,
                        Math.max(2f, cloudSize), 18);
            }
            shapes.setColor(.73f, .88f, 1f, shimmer * .24f);
            shapes.circle(x - size * .08f, y + size * .03f, Math.max(.8f, size * .055f), 9);
            return;
        }
        if (particle.bodyType == 6) {
            float inverseSpeed = 1f / Math.max(1f, particle.velocity.len());
            float tailX = x - particle.velocity.x * inverseSpeed * size * 5.8f;
            float tailY = y - particle.velocity.y * inverseSpeed * size * 5.8f;
            shapes.setColor(.32f, .68f, 1f, shimmer * .11f); shapes.rectLine(tailX, tailY, x, y, size * 2.1f);
            shapes.setColor(.72f, .89f, 1f, shimmer * .45f);
            shapes.rectLine(x - particle.velocity.x * inverseSpeed * size * 3.8f,
                    y - particle.velocity.y * inverseSpeed * size * 3.8f, x, y, Math.max(.5f, size * .72f));
            shapes.setColor(.94f, .98f, 1f, shimmer); shapes.circle(x, y, Math.max(.65f, size * .62f), 11);
            return;
        }
        if (particle.bodyType == 3) {
            float pulse = 1f + .16f * MathUtils.sin(particle.age * (2.1f + particle.spin * .08f) + particle.hue);
            shapes.setColor(.50f, .72f, 1f, shimmer * .12f); shapes.circle(x, y, size * 2.45f * pulse, 16);
            shapes.setColor(.82f, .91f, 1f, shimmer * .36f);
            float rayX = MathUtils.cos(particle.wobble * .12f) * size * 2f;
            float rayY = MathUtils.sin(particle.wobble * .12f) * size * 2f;
            shapes.rectLine(x - rayX, y - rayY, x + rayX, y + rayY, Math.max(.35f, size * .16f));
            shapes.rectLine(x + rayY, y - rayX, x - rayY, y + rayX, Math.max(.35f, size * .16f));
        }
        if (particle.bodyType == 2 && size > 3.6f) {
            float tilt = .28f + MathUtils.sin(particle.wobble * .08f) * .11f;
            float ringX = MathUtils.cos(tilt) * size * 1.42f, ringY = MathUtils.sin(tilt) * size * 1.42f;
            shapes.setColor(.72f, .55f, .34f, shimmer * .42f);
            shapes.rectLine(x - ringX, y - ringY, x + ringX, y + ringY, Math.max(.7f, size * .18f));
        }
        if (particle.bodyType == 4 && size > 2.6f) {
            for (int i = 0; i < 7; i++) {
                float a = particle.wobble + i * MathUtils.PI2 / 7f;
                float b = particle.wobble + (i + 1) * MathUtils.PI2 / 7f;
                float ra = size * (.78f + .19f * MathUtils.sin(i * 4.7f + particle.hue));
                float rb = size * (.78f + .19f * MathUtils.sin((i + 1) * 4.7f + particle.hue));
                float light = .62f + i * .035f;
                shapes.setColor(color.r * light, color.g * light, color.b * light, shimmer);
                shapes.triangle(x, y, x + MathUtils.cos(a) * ra, y + MathUtils.sin(a) * ra,
                        x + MathUtils.cos(b) * rb, y + MathUtils.sin(b) * rb);
            }
            shapes.setColor(.10f, .075f, .055f, shimmer * .48f);
            shapes.circle(x - size * .24f, y + size * .16f, Math.max(.55f, size * .13f), 8);
            return;
        }
        shapes.setColor(color.r * .18f, color.g * .18f, color.b * .18f, shimmer * .45f);
        shapes.circle(x + size * .16f, y - size * .14f, size * 1.12f, 18);
        shapes.setColor(color.r * .72f, color.g * .72f, color.b * .72f, shimmer); shapes.circle(x, y, size * .91f, 18);
        shapes.setColor(color.r, color.g, color.b, shimmer); shapes.circle(x - size * .16f, y + size * .13f, size * .65f, 16);
        if (particle.bodyType == 2) {
            float bandAngle = .10f * MathUtils.sin(particle.wobble * .09f);
            float bandX = MathUtils.cos(bandAngle) * size * .67f, bandY = MathUtils.sin(bandAngle) * size * .67f;
            shapes.setColor(.94f, .72f, .42f, shimmer * .34f);
            shapes.rectLine(x - bandX, y - bandY - size * .18f, x + bandX, y + bandY - size * .18f, Math.max(.5f, size * .11f));
            shapes.setColor(.30f, .20f, .14f, shimmer * .22f);
            shapes.rectLine(x - bandX * .86f, y - bandY + size * .27f,
                    x + bandX * .86f, y + bandY + size * .27f, Math.max(.45f, size * .08f));
        }
        if (particle.bodyType == 1 || particle.bodyType == 3) {
            shapes.setColor(.92f, .97f, 1f, shimmer * .82f);
            shapes.circle(x - size * .31f, y + size * .29f, Math.max(.55f, size * .22f), 10);
        } else if (particle.bodyType == 0 && size > 4f) {
            shapes.setColor(.12f, .09f, .07f, shimmer * .45f);
            shapes.circle(x + size * .28f, y + size * .09f, size * .13f, 8);
        }
    }

    private void drawInterface() {
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float sliderY = Gdx.graphics.getHeight() - 405f, countRow = Gdx.graphics.getHeight() - 332f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.018f, .029f, .047f, .97f); shapes.rect(panelX, 86, width, Gdx.graphics.getHeight() - 172);
        shapes.setColor(.72f, .43f, .16f, .88f); shapes.rect(panelX, Gdx.graphics.getHeight() - 132, width, 2);
        shapes.setColor(.035f, .055f, .078f, 1f); shapes.rect(panelX + 22, Gdx.graphics.getHeight() - 355, width - 44, 168);
        if (editingCount) { shapes.setColor(.11f, .19f, .27f, 1f); shapes.rect(panelX + 92, countRow - 27, 55, 34); }
        shapes.setColor(.08f, .13f, .19f, 1f); shapes.rect(panelX + 23, sliderY, width - 46, 9);
        shapes.setColor(.43f, .72f, .91f, 1f); shapes.rect(panelX + 23, sliderY, (width - 46) * ((speed - .35f) / 2.15f), 9);
        shapes.circle(panelX + 23 + (width - 46) * ((speed - .35f) / 2.15f), sliderY + 4.5f, 13);
        shapes.end();
        batch.begin();
        font.setColor(1f, .82f, .57f, 1f); font.getData().setScale(1.92f); text("BLACK HOLE", panelX + 22, Gdx.graphics.getHeight() - 84);
        font.setColor(.52f, .69f, .80f, 1f); font.getData().setScale(1f); text("CONTINUOUS STELLAR ACCRETION", panelX + 23, Gdx.graphics.getHeight() - 115);
        font.setColor(Color.WHITE); font.getData().setScale(1.16f); text("LIVE PARTICLES", panelX + 42, Gdx.graphics.getHeight() - 220);
        font.setColor(.95f, .79f, .53f, 1f); font.getData().setScale(2.2f); text(String.valueOf(cosmicParticles.size), panelX + 42, Gdx.graphics.getHeight() - 258);
        font.setColor(.44f, .62f, .73f, 1f); font.getData().setScale(.94f); text("ABSORBED  " + absorbedParticles, panelX + 150, Gdx.graphics.getHeight() - 237);
        font.getData().setScale(.82f); text("MASS  " + (int)mass, panelX + 150, Gdx.graphics.getHeight() - 258);
        font.setColor(.55f, .71f, .81f, 1f); font.getData().setScale(.94f); text("PARTICLE SEED", panelX + 42, Gdx.graphics.getHeight() - 298);
        font.setColor(Color.WHITE); font.getData().setScale(1.1f); text("-", panelX + 62, countRow);
        text(editingCount ? (countValue.isEmpty() ? "|" : countValue + "|") : String.valueOf(startCount), panelX + 101, countRow);
        text("+", panelX + 164, countRow);
        font.setColor(.55f, .71f, .81f, 1f); font.getData().setScale(1.03f); text("TIME SCALE  " + String.format("%.1fx", speed), panelX + 22, sliderY + 35);
        font.setColor(Color.WHITE); font.getData().setScale(1.08f); text(paused ? "▶  RESUME" : "Ⅱ  PAUSE", panelX + 24, sliderY - 38); text("↻  NEW CLOUD", panelX + 153, sliderY - 38);
        font.setColor(.56f, .78f, 1f, 1f); font.getData().setScale(1.02f); text("SIMULATION MENU", panelX + 24, 157);
        font.setColor(.48f, .59f, .84f, 1f); font.getData().setScale(.86f); text("SPIRAL IN. CROSS THE EVENT HORIZON.", panelX + 24, 114);
        batch.end();
    }

    @Override public void handleInput() {
        float x = Gdx.input.getX(), y = Gdx.graphics.getHeight() - Gdx.input.getY();
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float sliderY = Gdx.graphics.getHeight() - 405f, countRow = Gdx.graphics.getHeight() - 332f;
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .1f);
        if (!Gdx.input.isTouched()) { draggingSpeed = false; heldCountDelta = 0; return; }
        if (draggingSpeed) { setSpeedFromX(x, panelX, width); return; }
        if (heldCountDelta != 0) {
            heldCountTime += dt;
            if (heldCountTime > .34f) {
                heldCountRepeat += dt;
                while (heldCountRepeat >= .065f) { adjustCount(heldCountDelta); heldCountRepeat -= .065f; }
            }
            return;
        }
        if (!Gdx.input.justTouched()) return;
        if (x > panelX + 12 && x < panelX + width - 12 && y > 132 && y < 178) { menuRequested = true; return; }
        if (x > panelX + 45 && x < panelX + 92 && Math.abs(y - countRow) < 22) { startCountHold(-1); return; }
        if (x > panelX + 151 && x < panelX + 200 && Math.abs(y - countRow) < 22) { startCountHold(1); return; }
        if (x > panelX + 90 && x < panelX + 151 && Math.abs(y - countRow) < 22) { editingCount = true; countValue = ""; return; }
        if (x > panelX + 15 && x < panelX + width - 15 && Math.abs(y - sliderY) < 24) {
            draggingSpeed = true; setSpeedFromX(x, panelX, width); return;
        }
        if (y > sliderY - 66 && y < sliderY - 15) {
            if (x < panelX + 145) paused = !paused;
            else if (x < panelX + 310) reset();
        }
    }

    @Override public boolean keyTyped(char character) {
        if (!editingCount) return false;
        if (character >= '0' && character <= '9' && countValue.length() < 3) countValue += character;
        else if (character == '\b' && !countValue.isEmpty()) countValue = countValue.substring(0, countValue.length() - 1);
        else if (character == '\r' || character == '\n') commitCountEdit();
        return true;
    }

    @Override public boolean keyDown(int keycode) {
        if (!editingCount) return false;
        if (keycode == Input.Keys.ESCAPE) { editingCount = false; countValue = ""; return true; }
        if (keycode == Input.Keys.ENTER) { commitCountEdit(); return true; }
        return false;
    }

    private void startCountHold(int delta) {
        if (editingCount) commitCountEdit();
        adjustCount(delta);
        heldCountDelta = delta; heldCountTime = 0f; heldCountRepeat = 0f;
    }

    private void adjustCount(int delta) { startCount = clampSeedCount(startCount + delta); }
    private void commitCountEdit() {
        try { startCount = clampSeedCount(Integer.parseInt(countValue)); }
        catch (NumberFormatException ignored) { }
        editingCount = false; countValue = "";
    }

    private void setSpeedFromX(float x, float panelX, float width) {
        speed = RpsSimulation.clampSpeed(.35f + (x - (panelX + 23)) / (width - 46) * 2.15f);
    }

    private void createBackgroundStars() {
        Random starRandom = new Random(0x51A7F13DL);
        for (int i = 0; i < backgroundStars.length; i++) {
            float brightness = .22f + (float)Math.pow(starRandom.nextFloat(), 2.4f) * .78f;
            float size = .22f + brightness * brightness * 2.15f;
            int shape = brightness > .84f ? 2 : brightness > .60f && starRandom.nextFloat() < .38f ? 1 : 0;
            backgroundStars[i] = new BackgroundStar(.055f + (float)Math.sqrt(starRandom.nextFloat()) * .92f,
                    starRandom.nextFloat() * MathUtils.PI2, size, brightness,
                    starRandom.nextFloat() * MathUtils.PI2, (starRandom.nextFloat() - .5f) * .0022f,
                    starRandom.nextFloat(), shape);
        }
    }

    private Texture createBlackHoleSprite() {
        final int size = 384;
        final float center = size * .5f;
        Pixmap map = transparentPixmap(size, size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float dx = x - center, dy = y - center;
            float radius = (float)Math.sqrt(dx * dx + dy * dy);
            if (radius > 191f) continue;

            // A thin, optically bright disk viewed almost edge-on.
            float flattenedY = dy * 3.45f;
            float diskRadius = (float)Math.sqrt(dx * dx + flattenedY * flattenedY);
            float azimuth = MathUtils.atan2(flattenedY, dx);
            float innerDisk = gaussian(diskRadius, 88f, 7f);
            float hotDisk = gaussian(diskRadius, 108f, 18f);
            float outerDisk = gaussian(diskRadius, 145f, 38f) * .48f;
            float turbulence = .72f + .13f * MathUtils.sin(azimuth * 17f + diskRadius * .36f)
                    + .09f * MathUtils.sin(azimuth * 29f - diskRadius * .19f)
                    + .06f * MathUtils.sin(dx * .23f + dy * .41f);
            float disk = Math.max(0f, (innerDisk + hotDisk + outerDisk) * turbulence);

            // The far side of the disk is gravitationally lensed into two broad arches.
            float normalizedX = Math.min(1f, Math.abs(dx) / 119f);
            float arcHeight = 17f + 63f * (float)Math.sqrt(Math.max(0f, 1f - normalizedX * normalizedX));
            float arc = gaussian(Math.abs(dy), arcHeight, 8.5f)
                    * gaussian(Math.abs(dx), 51f, 82f);
            float arcFilaments = .76f + .15f * MathUtils.sin(radius * .43f + azimuth * 13f)
                    + .09f * MathUtils.sin(radius * .19f - azimuth * 23f);
            arc = Math.max(0f, arc * arcFilaments);

            float horizontalEnvelope = gaussian(Math.abs(dx), 76f, 112f);
            float equatorialBloom = gaussian(dy, 0f, 9.5f) * horizontalEnvelope;
            float equatorialCore = gaussian(dy, 0f, 1.65f) * horizontalEnvelope;
            float photonRing = gaussian(radius, 72f, 1.45f);
            float halo = gaussian(radius, 102f, 64f) * .14f;
            float wideRoseGlow = gaussian(radius, 132f, 76f) * .045f;
            float approaching = MathUtils.clamp(.74f - dx / 360f, .48f, 1f);

            boolean inShadow = radius < 69.5f;
            float visibleDisk = inShadow ? 0f : disk * approaching;
            float visibleArc = inShadow ? 0f : arc;
            float visiblePhotonRing = inShadow && radius < 68f ? 0f : photonRing;
            // The front edge of the disk crosses the silhouette as a razor-thin white band.
            float foregroundBand = equatorialCore * (.72f + .28f * approaching);

            float emission = visibleDisk + visibleArc * .92f + foregroundBand * 1.7f;
            float red = emission * 1.08f + equatorialBloom * .31f + visiblePhotonRing * 1.04f
                    + halo * .82f + wideRoseGlow;
            float green = emission * .91f + equatorialBloom * .24f + visiblePhotonRing * .93f
                    + halo * .58f + wideRoseGlow * .66f;
            float blue = emission * .94f + equatorialBloom * .29f + visiblePhotonRing * .97f
                    + halo * .69f + wideRoseGlow * .82f;
            float alpha = MathUtils.clamp(emission * .88f + equatorialBloom * .30f
                    + visiblePhotonRing * .92f + halo + wideRoseGlow, 0f, .995f);

            if (inShadow && foregroundBand < .035f) {
                float shadowEdge = MathUtils.clamp((69.5f - radius) / 2.5f, 0f, 1f);
                map.setColor(.0001f, .00012f, .0002f, .95f + shadowEdge * .05f);
            } else {
                map.setColor(Math.min(1f, red), Math.min(1f, green), Math.min(1f, blue), alpha);
            }
            map.drawPixel(x, y);
        }
        return texture(map);
    }

    private static float gaussian(float value, float center, float width) {
        float normalized = (value - center) / width;
        return (float)Math.exp(-normalized * normalized);
    }

    private static Pixmap transparentPixmap(int width, int height) {
        Pixmap map = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None); map.setColor(0f, 0f, 0f, 0f); map.fill();
        map.setBlending(Pixmap.Blending.SourceOver);
        return map;
    }

    private static Texture texture(Pixmap map) {
        Texture texture = new Texture(map); map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private void text(String value, float x, float y) { font.draw(batch, value, x, y); }
    private void centered(String value, float x, float y) {
        layout.setText(font, value); font.draw(batch, value, x - layout.width * .5f, y);
    }

    @Override public boolean consumeMenuRequest() { boolean result = menuRequested; menuRequested = false; return result; }
    @Override public void leave() {
        draggingSpeed = false; heldCountDelta = 0; editingCount = false; countValue = ""; paused = false;
        stopAmbience();
    }
    @Override public void dispose() { stopAmbience(); absorbSound.dispose(); ambience.dispose(); blackHoleSprite.dispose(); }
}
