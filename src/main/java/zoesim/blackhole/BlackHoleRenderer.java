package zoesim.blackhole;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import java.util.Random;

/**
 * Procedural graphics generation and rendering for black hole, dense accretion blanket,
 * progressive relativistic jetstream, and star field.
 */
public final class BlackHoleRenderer {
    private final Texture blackHoleSprite;
    private final CelestialTextureBank textureBank;
    private final Color spectralColor = new Color();

    private static final int CLOUD_PUFF_COUNT = 1400;
    private final AccretionCloudPuff[] accretionPuffs = new AccretionCloudPuff[CLOUD_PUFF_COUNT];

    public BlackHoleRenderer() {
        initAccretionClouds();
        blackHoleSprite = createBlackHoleSprite();
        textureBank = new CelestialTextureBank();
    }

    private void initAccretionClouds() {
        Random rand = new Random(0x7A4C91E3L);
        for (int i = 0; i < CLOUD_PUFF_COUNT; i++) {
            // Power 1.55 biases puff distribution strongly toward inner and mid disc
            float normDist = (float)Math.pow(rand.nextFloat(), 1.55f);
            float baseOrbit = 1.08f + normDist * 3.15f;
            float eccentricity = 0.01f + rand.nextFloat() * 0.11f;
            float periapsis = rand.nextFloat() * MathUtils.PI2;
            // 3D planar tilt & pitch dispersion
            float planeTilt = (rand.nextFloat() - 0.5f) * 0.10f;
            float planePitch = (rand.nextFloat() - 0.5f) * 0.07f;
            float speedMult = 0.94f + rand.nextFloat() * 0.12f;
            // Size factor: inner puffs are slightly smaller/denser, outer puffs larger/diffuse
            float baseSize = 0.85f + rand.nextFloat() * 0.35f;
            // Alpha: dense in inner/mid disc, tapering toward outer edges
            float baseAlpha = 0.30f + (1f - normDist) * 0.50f + rand.nextFloat() * 0.15f;
            float phase = rand.nextFloat() * MathUtils.PI2;
            float spinRate = (rand.nextFloat() - 0.5f) * 3.5f;
            float pulseFreq = 1.2f + rand.nextFloat() * 2.4f;
            float pulsePhase = rand.nextFloat() * MathUtils.PI2;

            accretionPuffs[i] = new AccretionCloudPuff(normDist, baseOrbit, eccentricity, periapsis,
                    planeTilt, planePitch, speedMult, baseSize, baseAlpha, phase, spinRate, pulseFreq, pulsePhase);
        }
    }

    public void render(BlackHoleWorld world, float elapsed, ShapeRenderer shapes, SpriteBatch batch,
                       BitmapFont font, GlyphLayout layout) {
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        float arenaRadius = world.getArenaRadius();
        float horizon = world.horizon();
        float simTime = world.getSimTime();

        // 1. Shapes background & under-layers
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.003f, .006f, .014f, 1f);
        shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setColor(.0015f, .003f, .009f, 1f);
        shapes.circle(arenaX, arenaY, world.getFieldRadius());
        drawStarField(shapes, world, elapsed);

        // Pre-draw comet tails and ring under-layers for negative depth
        for (CosmicParticle particle : world.getCosmicParticles()) {
            if (particle.position.z <= 0f) {
                float scale = BlackHolePhysics.projectedBodyScale(particle.position.z, arenaRadius);
                float drawRadius = particle.size * scale;
                if (particle.bodyType == 9) {
                    drawCometTail(shapes, particle, drawRadius);
                } else if (particle.bodyType == 3 && drawRadius > 3.5f) {
                    drawPlanetaryRing(shapes, particle, drawRadius, true);
                }
            }
        }
        shapes.end();

        // 2. Sprites: Back accretion clouds, 3D rotating bodies, black hole sprite, front clouds, and jetstream
        batch.begin();
        drawAccretionClouds(batch, world, horizon, false, simTime);

        for (CosmicParticle particle : world.getCosmicParticles()) {
            if (particle.position.z <= 0f) {
                drawCelestialBody(batch, particle, arenaRadius);
            }
        }

        float size = horizon * 8.4f + MathUtils.sin(simTime * 1.35f) * 1.5f;
        batch.draw(blackHoleSprite, arenaX - size * .5f, arenaY - size * .5f, size * .5f, size * .5f,
                size, size, 1f, 1f, BlackHolePhysics.ACCRETION_DISK_ANGLE, 0, 0,
                blackHoleSprite.getWidth(), blackHoleSprite.getHeight(), false, false);

        drawAccretionClouds(batch, world, horizon, true, simTime);

        for (CosmicParticle particle : world.getCosmicParticles()) {
            if (particle.position.z > 0f) {
                drawCelestialBody(batch, particle, arenaRadius);
            }
        }

        drawJetClouds(batch, world, simTime);
        batch.end();

        // 3. Shapes foreground: front comet tails, front rings, and disruption flares
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (CosmicParticle particle : world.getCosmicParticles()) {
            float scale = BlackHolePhysics.projectedBodyScale(particle.position.z, arenaRadius);
            float drawRadius = particle.size * scale;
            if (particle.bodyType == 9 && particle.position.z > 0f) {
                drawCometTail(shapes, particle, drawRadius);
            } else if (particle.bodyType == 3 && drawRadius > 3.5f) {
                drawPlanetaryRing(shapes, particle, drawRadius, false);
            }
        }
        drawAbsorptionFlares(shapes, world, horizon);
        shapes.end();

        if (world.isFieldConsumed()) {
            batch.begin();
            font.setColor(.95f, .78f, 1f, 1f);
            font.getData().setScale(1.45f);
            centered(font, layout, batch, "EVENT HORIZON FILLS THE FIELD", arenaX, arenaY + 18f);
            font.setColor(.72f, .75f, .90f, .85f);
            font.getData().setScale(.95f);
            centered(font, layout, batch, "PRESS NEW CLOUD TO RESET", arenaX, arenaY - 22f);
            batch.end();
        }
    }

    private void drawCelestialBody(SpriteBatch batch, CosmicParticle particle, float arenaRadius) {
        float scale = BlackHolePhysics.projectedBodyScale(particle.position.z, arenaRadius);
        float drawRadius = particle.size * scale;
        TextureRegion region = textureBank.getFrame(particle.bodyType, particle.wobble);
        // Depth cueing: celestial bodies in the foreground receive crisp direct illumination,
        // while bodies deep behind the black hole plane have subtle atmospheric depth dimming
        float depthNorm = MathUtils.clamp(particle.position.z / (Math.max(1f, arenaRadius) * 0.7f), -1f, 1f);
        float brightness = MathUtils.clamp(0.85f + depthNorm * 0.22f, 0.60f, 1.05f);
        batch.setColor(brightness, brightness, brightness, 1f);
        batch.draw(region, particle.position.x - drawRadius, particle.position.y - drawRadius,
                drawRadius * 2f, drawRadius * 2f);
        batch.setColor(Color.WHITE);
    }

    private void drawCometTail(ShapeRenderer shapes, CosmicParticle particle, float radius) {
        float speed = particle.velocity.len();
        if (speed < 1f) return;
        float invSpeed = 1f / speed;
        float tailLength = Math.min(65f, radius * 5.2f + speed * 0.16f);
        float normVx = particle.velocity.x * invSpeed;
        float normVy = particle.velocity.y * invSpeed;

        // Multi-segment tapering ion & dust tail (broad bright base -> fine fading wispy tip)
        final int TAIL_SEGS = 8;
        float prevX = particle.position.x;
        float prevY = particle.position.y;

        for (int i = 1; i <= TAIL_SEGS; i++) {
            float frac = (float)i / TAIL_SEGS;
            float segX = particle.position.x - normVx * tailLength * frac;
            float segY = particle.position.y - normVy * tailLength * frac;

            // Width tapers smoothly from 1.6x radius down to 0.25px at tip
            float segWidth = Math.max(0.3f, radius * 1.6f * (1f - frac * 0.85f));
            float alpha = (1f - frac) * (1f - frac);

            // Broad electric-blue ion tail sheath
            shapes.setColor(0.35f, 0.65f, 1.0f, alpha * 0.35f);
            shapes.rectLine(prevX, prevY, segX, segY, segWidth * 1.8f);

            // Brilliant cyan-white core streamer
            shapes.setColor(0.82f, 0.94f, 1.0f, alpha * 0.85f);
            shapes.rectLine(prevX, prevY, segX, segY, segWidth * 0.65f);

            prevX = segX;
            prevY = segY;
        }

        // Radiant spherical coma surrounding the icy nucleus
        shapes.setColor(0.55f, 0.82f, 1.0f, 0.32f);
        shapes.circle(particle.position.x, particle.position.y, radius * 1.45f, 12);
        shapes.setColor(0.92f, 0.97f, 1.0f, 0.75f);
        shapes.circle(particle.position.x, particle.position.y, radius * 0.75f, 8);
    }

    private void drawPlanetaryRing(ShapeRenderer shapes, CosmicParticle particle, float radius, boolean back) {
        float innerRadius = radius * 1.35f;
        float outerRadius = radius * 2.15f;
        // Individualized 3D ring orientation based on particle hue/spin
        float orientation = particle.hue * MathUtils.degreesToRadians;
        // Realistic inclination: tilt angle between 18° and 42°
        float ringTilt = 0.32f + 0.35f * Math.abs(MathUtils.sin(particle.hue * 1.7f));
        float cosTilt = MathUtils.cos(ringTilt);
        float cosOri = MathUtils.cos(orientation);
        float sinOri = MathUtils.sin(orientation);

        // Multi-layered concentric ring divisions (A ring, Cassini Division, B ring)
        final int ARC_STEPS = 20;
        final float ARC_START = back ? 0f : MathUtils.PI;
        final float ARC_END = back ? MathUtils.PI : MathUtils.PI2;
        final float stepAngle = (ARC_END - ARC_START) / ARC_STEPS;

        for (int ringLayer = 0; ringLayer < 3; ringLayer++) {
            float rMid;
            float rWidth;
            float alphaBase;
            if (ringLayer == 0) { // Inner B-ring (brightest, dense)
                rMid = innerRadius + (outerRadius - innerRadius) * 0.32f;
                rWidth = (outerRadius - innerRadius) * 0.42f;
                alphaBase = back ? 0.28f : 0.58f;
                shapes.setColor(0.92f, 0.82f, 0.62f, alphaBase);
            } else if (ringLayer == 1) { // Cassini gap (dark subtle line)
                rMid = innerRadius + (outerRadius - innerRadius) * 0.60f;
                rWidth = (outerRadius - innerRadius) * 0.10f;
                alphaBase = back ? 0.08f : 0.16f;
                shapes.setColor(0.48f, 0.40f, 0.32f, alphaBase);
            } else { // Outer A-ring (translucent, silvery amber)
                rMid = innerRadius + (outerRadius - innerRadius) * 0.82f;
                rWidth = (outerRadius - innerRadius) * 0.30f;
                alphaBase = back ? 0.20f : 0.45f;
                shapes.setColor(0.85f, 0.76f, 0.64f, alphaBase);
            }

            float prevPx = 0f, prevPy = 0f;
            for (int i = 0; i <= ARC_STEPS; i++) {
                float theta = ARC_START + i * stepAngle;
                // Parametric 3D circle in ring plane
                float rx = rMid * MathUtils.cos(theta);
                float ry = rMid * MathUtils.sin(theta) * cosTilt;
                // Rotate by orientation in projected plane
                float px = particle.position.x + rx * cosOri - ry * sinOri;
                float py = particle.position.y + rx * sinOri + ry * cosOri;

                if (i > 0) {
                    shapes.rectLine(prevPx, prevPy, px, py, Math.max(0.65f, rWidth * 0.65f));
                }
                prevPx = px;
                prevPy = py;
            }
        }
    }

    private void drawJetClouds(SpriteBatch batch, BlackHoleWorld world, float elapsed) {
        Texture puffTexture = textureBank.getJetCloudTexture();
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        float arenaRadius = world.getArenaRadius();
        float horizon = world.horizon();
        float axisX = MathUtils.cos(BlackHolePhysics.JET_AXIS);
        float axisY = MathUtils.sin(BlackHolePhysics.JET_AXIS);

        // 1. Mild, soft, diffuse cloud forming around the polar funnel zone in 3D space
        for (int pole = -1; pole <= 1; pole += 2) {
            float cloudX = arenaX + axisX * pole * horizon * 0.95f;
            float cloudY = arenaY + axisY * pole * horizon * 0.95f;
            float cloudPulse = 0.92f + 0.12f * MathUtils.sin(elapsed * 2.8f + pole);
            float mildRadius = horizon * 1.25f * cloudPulse;

            // Translucent mild purple-lavender polar shroud
            batch.setColor(0.62f, 0.32f, 0.88f, 0.24f);
            batch.draw(puffTexture, cloudX - mildRadius, cloudY - mildRadius, mildRadius * 2f, mildRadius * 2f);

            float innerMildRadius = mildRadius * 0.65f;
            batch.setColor(0.82f, 0.62f, 1.0f, 0.32f);
            batch.draw(puffTexture, cloudX - innerMildRadius, cloudY - innerMildRadius, innerMildRadius * 2f, innerMildRadius * 2f);
        }

        // 2. Progressive jetstream: mild/translucent near pole -> dense & brilliant shock diamond -> naturally darkening
        for (JetParticle jet : world.getJetParticles()) {
            float progress = MathUtils.clamp(jet.age / jet.life, 0f, 1f);
            float fade = (1f - progress) * (1f - progress) * jet.brightness;
            if (fade <= 0.01f) continue;

            float currentRadius = jet.size + jet.expandRate * jet.age;
            float dx = jet.position.x - arenaX;
            float dy = jet.position.y - arenaY;
            float distFromCenter = (float)Math.sqrt(dx * dx + dy * dy);
            float distNorm = MathUtils.clamp((distFromCenter - horizon) / Math.max(1f, arenaRadius - horizon), 0f, 1f);

            // Progressive brightness curve: builds up from mild near pole to intense mid-jet, then darkens to space
            float progressiveIntensity = (float)Math.sin(distNorm * MathUtils.PI * 0.95f);
            if (distNorm < 0.15f) {
                progressiveIntensity = MathUtils.lerp(0.35f, 0.85f, distNorm / 0.15f);
            }

            if (jet.isShockKnot) {
                // Radiant shock diamond knot (matching blackhole_ejection.png)
                float outerRadius = currentRadius * 2.2f;
                batch.setColor(0.72f, 0.32f, 0.98f, Math.min(1f, fade * 0.72f * progressiveIntensity));
                batch.draw(puffTexture, jet.position.x - outerRadius, jet.position.y - outerRadius,
                        outerRadius * 2f, outerRadius * 2f);

                batch.setColor(0.98f, 0.94f, 1.0f, Math.min(1f, fade * 0.98f));
                batch.draw(puffTexture, jet.position.x - currentRadius, jet.position.y - currentRadius,
                        currentRadius * 2f, currentRadius * 2f);
            } else {
                // Progressive lavender -> violet -> deep space purple
                float r = MathUtils.lerp(0.68f, 0.96f, progressiveIntensity);
                float g = MathUtils.lerp(0.35f, 0.88f, progressiveIntensity);
                float b = 1.0f;
                float puffAlpha = Math.min(1f, fade * 0.62f * (0.35f + 0.65f * progressiveIntensity));
                batch.setColor(r, g, b, puffAlpha);
                batch.draw(puffTexture, jet.position.x - currentRadius, jet.position.y - currentRadius,
                        currentRadius * 2f, currentRadius * 2f);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawStarField(ShapeRenderer shapes, BlackHoleWorld world, float elapsed) {
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        float fieldRadius = world.getFieldRadius();
        float horizon = world.horizon();
        float einsteinRadius = horizon * 1.62f;

        for (BackgroundStar star : world.getBackgroundStars()) {
            float sourceAngle = star.angle + elapsed * star.angularMotion;
            float sourceRadius = fieldRadius * star.radius
                    + MathUtils.sin(elapsed * .045f + star.phase) * fieldRadius * .0025f;
            float imageRadius = BlackHolePhysics.lensedImageRadius(sourceRadius, einsteinRadius);
            if (imageRadius > fieldRadius * .985f) continue;
            float root = 2f * imageRadius - sourceRadius;
            float magnification = 1f + Math.min(.85f, einsteinRadius * einsteinRadius / Math.max(1f, sourceRadius * sourceRadius) * .22f);
            float twinkle = star.brightness * (.76f + .24f * MathUtils.sin(elapsed * (.45f + star.brightness * .9f) + star.phase));
            float red = star.temperature < .32f ? 1f : star.temperature > .72f ? .64f : .88f;
            float green = star.temperature < .32f ? .68f : star.temperature > .72f ? .80f : .90f;
            float blue = star.temperature < .32f ? .43f : 1f;
            drawBackgroundStar(shapes, arenaX + MathUtils.cos(sourceAngle) * imageRadius,
                    arenaY + MathUtils.sin(sourceAngle) * imageRadius,
                    star.size * magnification, red, green, blue, twinkle, star.shape);
            float secondaryRadius = .5f * (root - sourceRadius);
            if (sourceRadius < einsteinRadius * 2.1f && secondaryRadius > horizon * 1.015f) {
                float alpha = twinkle * .18f * (1f - sourceRadius / (einsteinRadius * 2.1f));
                drawBackgroundStar(shapes, arenaX - MathUtils.cos(sourceAngle) * secondaryRadius,
                        arenaY - MathUtils.sin(sourceAngle) * secondaryRadius,
                        Math.max(.35f, star.size * .55f), red, green, blue, alpha, 0);
            }
        }
    }

    private void drawBackgroundStar(ShapeRenderer shapes, float x, float y, float size, float red,
                                    float green, float blue, float alpha, int shape) {
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
        } else {
            shapes.circle(x, y, Math.max(.28f, size), 9);
        }
    }

    /**
     * Volumetric 3D spinning cloud of light with variable density, organic eddies,
     * 3x faster inner spin, and matching light-halo color gradient.
     */
    private void drawAccretionClouds(SpriteBatch batch, BlackHoleWorld world, float horizon,
                                     boolean front, float simTime) {
        Texture cloudTex = textureBank.getAccretionCloudTexture();
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        float rotation = BlackHolePhysics.ACCRETION_DISK_ANGLE * MathUtils.degreesToRadians;
        float cosRotation = MathUtils.cos(rotation);
        float sinRotation = MathUtils.sin(rotation);
        float texW = cloudTex.getWidth();
        float texH = cloudTex.getHeight();

        for (int i = 0; i < CLOUD_PUFF_COUNT; i++) {
            AccretionCloudPuff puff = accretionPuffs[i];

            // 3x faster inner spin with smooth differential Keplerian-like gradient:
            // At inner rim (baseOrbit ~ 1.08): omega ~ 7.4 rad/s (visibly matching infalling celestial bodies!)
            // At outer rim (baseOrbit ~ 4.20): omega ~ 0.36 rad/s (gradual cosmic drift)
            float omega = 7.4f * (float)Math.pow(1.10f / Math.max(1.05f, puff.baseOrbit), 2.25f) * puff.speedMult;
            float headPhase = puff.phase + simTime * omega;

            float sinHead = MathUtils.sin(headPhase);
            float cosHead = MathUtils.cos(headPhase);
            boolean isFront = sinHead < 0f;
            if (isFront != front) continue;

            // Keplerian orbital radius with eccentricity
            float r = puff.baseOrbit * horizon * (1f - puff.eccentricity * puff.eccentricity)
                    / (1f + puff.eccentricity * MathUtils.cos(headPhase - puff.periapsis));

            float lx = r * cosHead;
            // Eye-shaped vertical compression (0.22) + subtle 3D orbital plane variations
            float ly = r * sinHead * 0.22f + r * (puff.planeTilt * sinHead + puff.planePitch * cosHead);

            // Taper outer tips for natural almond/eye profile matching images/accretion_disc.png
            float tipTaper = Math.max(0.25f, 1f - (Math.abs(lx) / (horizon * 4.4f)) * 0.40f);
            ly *= tipTaper;

            float posX = arenaX + lx * cosRotation - ly * sinRotation;
            float posY = arenaY + lx * sinRotation + ly * cosRotation;

            // Front crossing: puffs passing directly across the pitch-black event horizon silhouette
            boolean frontCrossing = front && Math.abs(lx) < horizon * 1.40f && Math.abs(ly) < horizon * 0.38f;

            // Dynamic density pulsation & breathing
            float pulse = 0.88f + 0.12f * MathUtils.sin(simTime * puff.pulseFreq + puff.pulsePhase);
            float alpha = MathUtils.clamp(puff.baseAlpha * pulse, 0.05f, 1.0f);

            // Color gradient matching images/accretion_disc.png and the circular light ring:
            // 1. Inner disc & front crossing: Blazing white-hot cream / platinum
            // 2. Mid disc: Luminous champagne gold & warm ivory
            // 3. Outer edge: Smoky bronze / muted topaz, less dense and fading
            float red, green, blue;
            if (frontCrossing || puff.normDist < 0.18f) {
                red = 1.0f;
                green = frontCrossing ? 0.99f : 0.95f;
                blue = frontCrossing ? 0.96f : 0.88f;
                alpha = Math.min(0.98f, alpha * (frontCrossing ? 1.55f : 1.20f));
            } else if (puff.normDist < 0.62f) {
                float t = (puff.normDist - 0.18f) / 0.44f;
                red = 1.0f;
                green = MathUtils.lerp(0.93f, 0.74f, t);
                blue = MathUtils.lerp(0.82f, 0.46f, t);
                alpha = alpha * MathUtils.lerp(1.05f, 0.78f, t);
            } else {
                float t = (puff.normDist - 0.62f) / 0.38f;
                red = MathUtils.lerp(0.95f, 0.72f, t);
                green = MathUtils.lerp(0.72f, 0.46f, t);
                blue = MathUtils.lerp(0.46f, 0.22f, t);
                alpha = alpha * MathUtils.lerp(0.70f, 0.18f, t);
            }

            // Volumetric puff radius scales with horizon, distance, and baseSize
            float puffRadius = horizon * (0.18f + puff.normDist * 0.42f) * puff.baseSize;
            if (frontCrossing) {
                puffRadius *= 1.15f; // Extra volumetric presence across front band
            }

            float puffDiam = puffRadius * 2f;
            float puffAngleDeg = (headPhase * 1.5f + puff.spinRate * simTime) * MathUtils.radiansToDegrees;

            batch.setColor(red, green, blue, alpha);
            batch.draw(cloudTex, posX - puffRadius, posY - puffRadius, puffRadius, puffRadius,
                    puffDiam, puffDiam, 1f, 1f, puffAngleDeg, 0, 0, (int)texW, (int)texH, false, false);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawAbsorptionFlares(ShapeRenderer shapes, BlackHoleWorld world, float horizon) {
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        for (AbsorptionFlare flare : world.getAbsorptionFlares()) {
            float progress = MathUtils.clamp(flare.age / flare.life, 0f, 1f);
            float baseAngle = flare.angle + progress * .62f;
            float radialX = MathUtils.cos(baseAngle);
            float radialY = MathUtils.sin(baseAngle);
            float tangentX = -radialY;
            float tangentY = radialX;
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

    /**
     * Procedural eye-shaped accretion disc texture with blazing white front band,
     * lensed upper/lower arches, photon ring, and tapering outer edges (reference images/accretion_disc.png).
     */
    private Texture createBlackHoleSprite() {
        final int size = 1024;
        final float center = size * .5f;
        final float X_MAX = 492f;
        final float R_h = 121.0f;
        final float aaBand = 2.4f; // Sub-pixel Hermite anti-aliasing transition band
        Pixmap map = transparentPixmap(size, size);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center;
                float dy = y - center;
                float radius = (float)Math.sqrt(dx * dx + dy * dy);

                // 1. Soft underlying ambient eye-shaped disc glow (foundation beneath spinning cloud puffs)
                float eyeDisc = 0f;
                if (Math.abs(dx) < X_MAX) {
                    float t = Math.abs(dx) / X_MAX;
                    float halfHeight = 65f * (1f - (float)Math.pow(t, 1.35f));
                    float vertNorm = Math.abs(dy) / Math.max(1f, halfHeight);
                    float diskGlow = (float)Math.exp(-1.4f * vertNorm * vertNorm) * 0.36f;
                    float edgeDensity = (float)Math.pow(Math.max(0f, 1f - t), 0.75f);
                    float doppler = MathUtils.clamp(0.56f - dx / 560f, 0.25f, 1f);
                    eyeDisc = diskGlow * edgeDensity * doppler;
                }

                // 2. Luminous front band foundation across the front of the black hole
                float dopplerFront = MathUtils.clamp(0.56f - dx / 560f, 0.25f, 1f);
                float frontBand = (float)Math.exp(-(dy * dy) / (2f * 6.5f * 6.5f))
                        * (float)Math.exp(-(dx * dx) / (2f * 320f * 320f)) * 0.95f;
                float frontCore = (float)Math.exp(-(dy * dy) / (2f * 2.5f * 2.5f))
                        * (float)Math.exp(-(dx * dx) / (2f * 250f * 250f)) * 1.55f;
                float foreground = (frontBand + frontCore) * dopplerFront;

                // 3. Organic circular cloudy light ring surrounding the black hole (matching images/accretion_disc.png)
                // Extent of radius is ~1/3 of the black hole radius (extends from 121.0f to ~162.0f, smoothly fading to 0)
                float circularHalo = 0f;
                if (radius >= 121.0f && radius <= 162.0f) {
                    float haloNorm = (radius - 121.0f) / 41.0f; // [0, 1] across the 1/3-radius span
                    float angle = MathUtils.atan2(dy, dx);
                    // Multi-frequency organic turbulence matching jet cloud technique
                    float turb = MathUtils.sin(angle * 6f + radius * 0.175f) * 0.08f
                            + MathUtils.sin(angle * 11f - radius * 0.25f) * 0.04f;
                    float clampedNorm = MathUtils.clamp(haloNorm + turb, 0f, 1f);
                    // Gaussian-like falloff multiplying linear taper: brilliant near event horizon, seamlessly fading to 0 at edge
                    circularHalo = (float)Math.exp(-clampedNorm * clampedNorm * 3.0f) * (1f - clampedNorm) * 1.55f;
                }

                // 4. Razor-thin circular photon ring right at the photon sphere
                float photonRing = (float)Math.exp(-Math.pow(radius - 124.0f, 2) / (2f * 2.4f * 2.4f)) * 1.85f;

                // 5. Shadow masking for background emission
                float shadowMask = MathUtils.clamp((radius - 120.0f) / 4.0f, 0f, 1f);
                float backgroundLight = (eyeDisc + circularHalo) * shadowMask;
                float totalEmission = backgroundLight + foreground + photonRing;

                // 6. Sub-pixel anti-aliased event horizon silhouette with smooth Hermite blending
                // Compute continuous transition weight [0, 1]: 0 = deep black core, 1 = exterior emission
                float edgeT = MathUtils.clamp((radius - (R_h - aaBand)) / (2f * aaBand), 0f, 1f);
                float exteriorWeight = edgeT * edgeT * (3f - 2f * edgeT); // Cubic Hermite smoothstep (C1 continuous)

                // Exterior color mapping matching images/accretion_disc.png
                float extR, extG, extB, extA;
                if (totalEmission > 0.72f) {
                    float w = Math.min(1f, (totalEmission - 0.72f) / 0.72f);
                    extR = 1f;
                    extG = Math.min(1f, 0.90f + w * 0.10f);
                    extB = Math.min(1f, 0.75f + w * 0.25f);
                    extA = Math.min(0.99f, 0.88f + w * 0.11f);
                } else if (totalEmission > 0.22f) {
                    float t = (totalEmission - 0.22f) / 0.50f;
                    extR = Math.min(1f, 0.85f + t * 0.15f);
                    extG = 0.55f + t * 0.35f;
                    extB = 0.25f + t * 0.50f;
                    extA = 0.45f + t * 0.43f;
                } else {
                    float t = totalEmission / 0.22f;
                    extR = 0.78f * t;
                    extG = 0.45f * t;
                    extB = 0.20f * t;
                    extA = MathUtils.clamp(t * 0.45f, 0f, 1f);
                }

                // Deep black event horizon core
                float holeR = 0.0002f, holeG = 0.00025f, holeB = 0.0005f, holeA = 1.0f;

                // Seamless anti-aliased interpolation between event horizon and outer emission
                float outR = MathUtils.lerp(holeR, extR, exteriorWeight);
                float outG = MathUtils.lerp(holeG, extG, exteriorWeight);
                float outB = MathUtils.lerp(holeB, extB, exteriorWeight);
                float outA = MathUtils.lerp(holeA, extA, exteriorWeight);

                // If foreground white band cuts across the front of the black hole, composite it cleanly
                if (foreground > 0.035f && radius < R_h) {
                    float fgFactor = Math.min(1f, foreground * 0.82f);
                    outR = MathUtils.lerp(outR, extR, fgFactor);
                    outG = MathUtils.lerp(outG, extG, fgFactor);
                    outB = MathUtils.lerp(outB, extB, fgFactor);
                    outA = Math.max(outA, extA);
                }

                map.setColor(MathUtils.clamp(outR, 0f, 1f),
                             MathUtils.clamp(outG, 0f, 1f),
                             MathUtils.clamp(outB, 0f, 1f),
                             MathUtils.clamp(outA, 0f, 1f));
                map.drawPixel(x, y);
            }
        }
        return texture(map);
    }

    private static Pixmap transparentPixmap(int width, int height) {
        Pixmap map = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None);
        map.setColor(0f, 0f, 0f, 0f);
        map.fill();
        map.setBlending(Pixmap.Blending.SourceOver);
        return map;
    }

    private static Texture texture(Pixmap map) {
        Texture texture = new Texture(map);
        map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private void centered(BitmapFont font, GlyphLayout layout, SpriteBatch batch, String value, float x, float y) {
        layout.setText(font, value);
        font.draw(batch, value, x - layout.width * .5f, y);
    }

    public void dispose() {
        blackHoleSprite.dispose();
        textureBank.dispose();
    }

    private static final class AccretionCloudPuff {
        final float normDist;
        final float baseOrbit;
        final float eccentricity;
        final float periapsis;
        final float planeTilt;
        final float planePitch;
        final float speedMult;
        final float baseSize;
        final float baseAlpha;
        final float phase;
        final float spinRate;
        final float pulseFreq;
        final float pulsePhase;

        AccretionCloudPuff(float normDist, float baseOrbit, float eccentricity, float periapsis,
                           float planeTilt, float planePitch, float speedMult, float baseSize,
                           float baseAlpha, float phase, float spinRate, float pulseFreq, float pulsePhase) {
            this.normDist = normDist;
            this.baseOrbit = baseOrbit;
            this.eccentricity = eccentricity;
            this.periapsis = periapsis;
            this.planeTilt = planeTilt;
            this.planePitch = planePitch;
            this.speedMult = speedMult;
            this.baseSize = baseSize;
            this.baseAlpha = baseAlpha;
            this.phase = phase;
            this.spinRate = spinRate;
            this.pulseFreq = pulseFreq;
            this.pulsePhase = pulsePhase;
        }
    }
}
