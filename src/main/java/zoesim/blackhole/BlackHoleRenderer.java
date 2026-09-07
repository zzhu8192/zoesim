package zoesim.blackhole;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
            // Power 1.25 gives a smooth, gradual distribution across the full extended disc
            float normDist = (float)Math.pow(rand.nextFloat(), 1.25f);
            // Elongated by ~50%: baseOrbit now extends from 1.06 to 5.81 (was 1.08 to 4.23)
            float baseOrbit = 1.06f + normDist * 4.75f;
            float eccentricity = 0.01f + rand.nextFloat() * 0.09f;
            float periapsis = rand.nextFloat() * MathUtils.PI2;
            // 3D planar tilt & pitch dispersion
            float planeTilt = (rand.nextFloat() - 0.5f) * 0.08f;
            float planePitch = (rand.nextFloat() - 0.5f) * 0.05f;
            float speedMult = 0.94f + rand.nextFloat() * 0.12f;
            // Size factor: inner puffs are slightly smaller/denser, outer puffs broader
            float baseSize = 0.85f + rand.nextFloat() * 0.35f;
            // Gradual, continuous density tapering from inner core to outer tips
            float baseAlpha = MathUtils.lerp(0.58f, 0.16f, normDist) + rand.nextFloat() * 0.08f;
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

        // 2. Sprites: Foggy bulb luminance, back accretion clouds, 3D rotating bodies, black hole sprite, front clouds, and jetstream
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        drawFoggyBulbLuminance(batch, world, horizon);
        drawAccretionClouds(batch, world, horizon, false, simTime);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        for (CosmicParticle particle : world.getCosmicParticles()) {
            if (particle.position.z <= 0f) {
                drawCelestialBody(batch, particle, arenaRadius);
            }
        }

        float size = horizon * 8.4f + MathUtils.sin(simTime * 1.35f) * 1.5f;
        batch.draw(blackHoleSprite, arenaX - size * .5f, arenaY - size * .5f, size * .5f, size * .5f,
                size, size, 1f, 1f, BlackHolePhysics.ACCRETION_DISK_ANGLE, 0, 0,
                blackHoleSprite.getWidth(), blackHoleSprite.getHeight(), false, false);

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        drawAccretionClouds(batch, world, horizon, true, simTime);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 2b. Foreground dark grey/brown nebula clouds below accretion disc (overlapping lower-left & lower-right horizon/halo)
        drawForegroundNebula(batch, world, horizon, simTime);

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

    /**
     * Renders organic dark grey/brown nebula clouds in front of (but below) the accretion disc,
     * clustered in the lower-left and lower-right regions, overlapping the event horizon rim
     * and bottom halo ring as in images/accretion_disc.png.
     */
    private void drawForegroundNebula(SpriteBatch batch, BlackHoleWorld world, float horizon, float simTime) {
        Texture nebulaTexture = textureBank.getNebulaCloudTexture();
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        float radAngle = BlackHolePhysics.ACCRETION_DISK_ANGLE * MathUtils.degreesToRadians;
        float cosA = MathUtils.cos(radAngle);
        float sinA = MathUtils.sin(radAngle);

        for (ForegroundNebulaPuff puff : NEBULA_PUFFS) {
            // Transform disc-aligned (u, v) into world coordinates (relV < 0 is strictly below disc)
            float worldOffsetX = (puff.relU * cosA - puff.relV * sinA) * horizon;
            float worldOffsetY = (puff.relU * sinA + puff.relV * cosA) * horizon;
            float px = arenaX + worldOffsetX;
            float py = arenaY + worldOffsetY;

            // Organic subtle breathing and drift
            float breathe = 1f + 0.03f * MathUtils.sin(simTime * puff.driftSpeed * 1.5f);
            float width = horizon * puff.relWidth * breathe;
            float height = horizon * puff.relHeight * breathe;
            float puffRot = BlackHolePhysics.ACCRETION_DISK_ANGLE + puff.tilt
                    + MathUtils.sin(simTime * puff.driftSpeed) * 1.5f;
            float alpha = puff.baseAlpha * MathUtils.clamp(0.90f + 0.10f * MathUtils.cos(simTime * puff.driftSpeed * 0.8f), 0f, 1f);

            batch.setColor(puff.tintR, puff.tintG, puff.tintB, alpha);
            batch.draw(nebulaTexture, px - width * 0.5f, py - height * 0.5f,
                    width * 0.5f, height * 0.5f, width, height,
                    1f, 1f, puffRot, 0, 0, nebulaTexture.getWidth(), nebulaTexture.getHeight(), false, false);
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
            // At inner rim (baseOrbit ~ 1.06): omega ~ 7.4 rad/s (visibly matching infalling celestial bodies!)
            // At outer rim (baseOrbit ~ 5.80): omega ~ 0.22 rad/s (gradual cosmic drift)
            float omega = 7.4f * (float)Math.pow(1.08f / Math.max(1.04f, puff.baseOrbit), 2.25f) * puff.speedMult;
            float headPhase = puff.phase + simTime * omega;

            float sinHead = MathUtils.sin(headPhase);
            float cosHead = MathUtils.cos(headPhase);
            boolean isFront = sinHead < 0f;
            if (isFront != front) continue;

            // Keplerian orbital radius with eccentricity
            float r = puff.baseOrbit * horizon * (1f - puff.eccentricity * puff.eccentricity)
                    / (1f + puff.eccentricity * MathUtils.cos(headPhase - puff.periapsis));

            float lx = r * cosHead;
            // Vertical thickness: 0.20 at inner disc, aggressively flattening to 0.028 toward outer edges
            float flatness = MathUtils.lerp(0.20f, 0.028f, (float)Math.pow(puff.normDist, 1.2f));
            float tiltDamping = (float)Math.pow(1f - puff.normDist * 0.85f, 2.0f);
            float ly = r * sinHead * flatness + r * (puff.planeTilt * sinHead + puff.planePitch * cosHead) * tiltDamping;

            // Aggressive almond / knife-edge tip taper along lx towards extended outer tips (6.3 * horizon)
            float tipNorm = Math.min(1f, Math.abs(lx) / (horizon * 6.3f));
            float tipTaper = (float)Math.pow(Math.max(0f, 1f - tipNorm * 0.94f), 1.6f);
            ly *= (0.04f + 0.96f * tipTaper);

            float posX = arenaX + lx * cosRotation - ly * sinRotation;
            float posY = arenaY + lx * sinRotation + ly * cosRotation;

            // Tight inner front crossing right across the event horizon silhouette
            boolean innerFrontCrossing = front && r < horizon * 1.35f && Math.abs(ly) < horizon * 0.25f;

            // Dynamic density pulsation & breathing
            float pulse = 0.88f + 0.12f * MathUtils.sin(simTime * puff.pulseFreq + puff.pulsePhase);
            float alpha = MathUtils.clamp(puff.baseAlpha * pulse, 0.04f, 1.0f);

            // Gradual density taper toward outer edges
            float edgeFade = (float)Math.pow(Math.max(0f, 1f - tipNorm * 0.85f), 1.1f);
            alpha *= edgeFade;

            // Smooth, gradual 4-stage color & gamma gradient distributed across the entire disc:
            // 1. Inner core (normDist < 0.10, r ~ 1.06 - 1.5 H): Blinding white transitioning to warm cream
            // 2. Mid-inner (normDist 0.10 - 0.38, r ~ 1.5 - 2.8 H): Radiant gold to glowing amber-orange
            // 3. Mid-outer (normDist 0.38 - 0.72, r ~ 2.8 - 4.4 H): Distinct reddish-orange / terracotta (visible in front too!)
            // 4. Outer rim (normDist 0.72 - 1.00, r ~ 4.4 - 5.8 H): Deep smoky reddish-brown / burnt copper
            float red, green, blue;
            if (innerFrontCrossing) {
                red = 1.0f;
                green = 1.0f;
                blue = 1.0f;
                alpha = Math.min(1.0f, alpha * 1.60f);
            } else if (puff.normDist < 0.10f) {
                float t = puff.normDist / 0.10f;
                red = 1.0f;
                green = MathUtils.lerp(1.0f, 0.94f, t);
                blue = MathUtils.lerp(1.0f, 0.80f, t);
                alpha *= MathUtils.lerp(1.50f, 1.20f, t);
            } else if (puff.normDist < 0.38f) {
                float t = (puff.normDist - 0.10f) / 0.28f;
                red = 1.0f;
                green = MathUtils.lerp(0.94f, 0.60f, t);
                blue = MathUtils.lerp(0.80f, 0.18f, t);
                alpha *= MathUtils.lerp(1.20f, 0.90f, t);
            } else if (puff.normDist < 0.72f) {
                float t = (puff.normDist - 0.38f) / 0.34f;
                red = MathUtils.lerp(1.0f, 0.88f, t);
                green = MathUtils.lerp(0.60f, 0.26f, t);  // clearly reddish-orange across front & sides
                blue = MathUtils.lerp(0.18f, 0.05f, t);
                alpha *= MathUtils.lerp(0.90f, 0.65f, t);
            } else {
                float t = (puff.normDist - 0.72f) / 0.28f;
                red = MathUtils.lerp(0.88f, 0.66f, t);    // deep burnt copper / crimson-brown
                green = MathUtils.lerp(0.26f, 0.12f, t);  // reddish-brown
                blue = MathUtils.lerp(0.05f, 0.02f, t);
                alpha *= MathUtils.lerp(0.65f, 0.35f, t);
            }

            // Volumetric puff radius scales with horizon, distance, and baseSize
            float puffRadius = horizon * (0.18f + puff.normDist * 0.38f) * puff.baseSize;
            if (innerFrontCrossing) {
                puffRadius *= 1.15f;
            }

            float puffDiam = puffRadius * 2f;
            float diskAngleDeg = BlackHolePhysics.ACCRETION_DISK_ANGLE;

            // Inner puffs are round/volumetric (scaleY = 1.0), outer puffs are flattened laminar streaks (scaleY -> 0.16)
            float scaleX = 1.0f;
            float scaleY = MathUtils.lerp(1.0f, 0.16f, (float)Math.pow(puff.normDist, 1.1f));

            batch.setColor(red, green, blue, alpha);
            batch.draw(cloudTex, posX - puffRadius, posY - puffRadius, puffRadius, puffRadius,
                    puffDiam, puffDiam, scaleX, scaleY, diskAngleDeg, 0, 0, (int)texW, (int)texH, false, false);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawFoggyBulbLuminance(SpriteBatch batch, BlackHoleWorld world, float horizon) {
        Texture fogTex = textureBank.getFoggyBulbTexture();
        float arenaX = world.getArenaX();
        float arenaY = world.getArenaY();
        // Extends almost to the full width of the accretion disc (~6.4x horizon radius in both directions = 12.8x width)
        float fogW = horizon * 12.8f;
        float fogH = horizon * 9.6f;
        float rotation = BlackHolePhysics.ACCRETION_DISK_ANGLE;
        batch.setColor(1f, 1f, 1f, 0.90f);
        batch.draw(fogTex, arenaX - fogW * 0.5f, arenaY - fogH * 0.5f,
                fogW * 0.5f, fogH * 0.5f, fogW, fogH,
                1f, 1f, rotation, 0, 0, fogTex.getWidth(), fogTex.getHeight(), false, false);
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
                    // Aggressive taper into razor-thin outer tips matching images/accretion_disc.png
                    float halfHeight = 62f * (float)Math.pow(Math.max(0f, 1f - t), 2.0f);
                    float vertNorm = Math.abs(dy) / Math.max(0.4f, halfHeight);
                    float diskGlow = (float)Math.exp(-2.5f * vertNorm * vertNorm) * 0.42f;
                    float edgeDensity = (float)Math.pow(Math.max(0f, 1f - t), 1.5f);
                    float doppler = MathUtils.clamp(0.58f - dx / 560f, 0.25f, 1f);
                    eyeDisc = diskGlow * edgeDensity * doppler;
                }

                // 2. Organic circular cloudy light ring surrounding the black hole (matching images/accretion_disc.png)
                // Distinct top/bottom asymmetry: top lensed canopy is significantly thicker (reaches ~230px, span 109px),
                // while bottom arch is noticeably more compact and hugs closer to the horizon (reaches ~162px, span 41px).
                // Note: In Pixmap coordinates, row y=0 is the TOP of the texture (dy < 0), which renders as SCREEN TOP.
                float circularHalo = 0f;
                float screenPolar = radius > 1f ? -dy / radius : 0f; // Positive for screen top, negative for screen bottom
                float rOut;
                if (screenPolar >= 0f) {
                    rOut = 188.0f + 42.0f * (float)Math.pow(screenPolar, 1.15f);
                } else {
                    rOut = 188.0f - 26.0f * (float)Math.pow(-screenPolar, 1.15f);
                }
                if (radius >= 121.0f && radius <= rOut) {
                    float haloNorm = (radius - 121.0f) / (rOut - 121.0f); // [0, 1] across asymmetric span
                    float angle = MathUtils.atan2(-dy, dx); // Oriented with screen top as positive Y
                    boolean isTop = dy <= 0f; // Screen top
                    float blemishPhase = isTop ? 1.73f : 4.89f;

                    // Imperfect radial wobble (angular perturbation making concentric striations organic rather than sterile)
                    float angleWobble = isTop
                            ? (MathUtils.sin(angle * 3f + 1.2f) * 2.8f + MathUtils.cos(angle * 5f - 0.7f) * 1.8f)
                            : (MathUtils.sin(angle * 4f + 2.8f) * 1.5f + MathUtils.cos(angle * 2f + 1.1f) * 1.2f);
                    float rPerturbed = radius + angleWobble;

                    // Imperfect concentric striation ripples embedded directly in the halo body (asymmetric frequencies/phases)
                    float striation1 = isTop
                            ? MathUtils.sin(rPerturbed * 0.28f + 1.1f) * 0.18f
                            : MathUtils.sin(rPerturbed * 0.45f + 3.4f) * 0.15f;
                    float striation2 = isTop
                            ? MathUtils.sin(rPerturbed * 0.50f - 2.3f) * 0.12f
                            : MathUtils.sin(rPerturbed * 0.85f - 0.9f) * 0.09f;
                    float striations = striation1 + striation2;

                    // Multi-octave organic cloudy blemishes (patchy gaseous density variations across angle and radius)
                    float blemish1 = MathUtils.sin(angle * (isTop ? 5f : 6f) + radius * 0.05f + blemishPhase);
                    float blemish2 = MathUtils.sin(angle * (isTop ? 8f : 7f) - radius * 0.09f + blemishPhase * 0.6f);
                    float blemish3 = MathUtils.cos(angle * (isTop ? 12f : 10f) + radius * 0.14f);
                    float cloudNoise = (blemish1 * 0.45f + blemish2 * 0.35f + blemish3 * 0.20f);

                    // Secondary subtle lensed crest nestled within outer halo (scaled to respective arch spans)
                    float crestR = isTop ? 182.0f : 148.0f;
                    float crestWidth = isTop ? 8.5f : 4.5f;
                    float haloCrest = (float)Math.exp(-Math.pow(rPerturbed - crestR, 2) / (2f * crestWidth * crestWidth))
                            * (isTop ? 0.40f : 0.22f) * (0.85f + 0.15f * blemish1);

                    // Blemish modulation weighting: inner rim near horizon remains solid brilliant white core plateau,
                    // while mid and outer halo display the rich organic striations and blemishes
                    float blemishWeight = MathUtils.clamp((haloNorm - 0.12f) / 0.30f, 0f, 1f) * (1f - haloNorm * 0.75f);
                    float blemishMod = 1.0f + (striations + cloudNoise * 0.22f) * blemishWeight;

                    float corePlateau = isTop
                            ? MathUtils.clamp(1f - haloNorm * 1.35f, 0f, 1f)
                            : MathUtils.clamp(1f - haloNorm * 1.80f, 0f, 1f);
                    float falloffDecay = isTop ? 2.1f : 2.6f;
                    float baseHalo = corePlateau * 0.70f + (float)Math.exp(-haloNorm * haloNorm * falloffDecay) * (1f - haloNorm) * 1.65f;
                    float intensityScale = isTop ? 1.0f : 0.76f;
                    circularHalo = (baseHalo * Math.max(0.15f, blemishMod) + haloCrest) * intensityScale;
                }

                // 3. Razor-thin circular photon ring right at the photon sphere
                float photonRing = (float)Math.exp(-Math.pow(radius - 123.6f, 2) / (2f * 2.6f * 2.6f)) * 2.2f;

                // 4. Shadow masking for background emission
                float shadowMask = MathUtils.clamp((radius - 120.0f) / 4.0f, 0f, 1f);
                float backgroundLight = (eyeDisc + circularHalo) * shadowMask;
                float totalEmission = backgroundLight + photonRing;

                // 5. Sub-pixel anti-aliased event horizon silhouette with smooth Hermite blending
                // Compute continuous transition weight [0, 1]: 0 = deep black core, 1 = exterior emission
                float edgeT = MathUtils.clamp((radius - (R_h - aaBand)) / (2f * aaBand), 0f, 1f);
                float exteriorWeight = edgeT * edgeT * (3f - 2f * edgeT); // Cubic Hermite smoothstep (C1 continuous)

                // Exterior color mapping: white at inner rim, transitioning to amber, reddish-orange, and deep brown
                float extR, extG, extB, extA;
                if (totalEmission > 0.82f) {
                    float w = Math.min(1f, (totalEmission - 0.82f) / 0.82f);
                    extR = 1f;
                    extG = 1f; // Blinding white core right at horizon
                    extB = 1f;
                    extA = Math.min(1.0f, 0.92f + w * 0.08f);
                } else if (totalEmission > 0.35f) {
                    float t = (totalEmission - 0.35f) / 0.47f;
                    extR = 1f;
                    extG = MathUtils.lerp(0.72f, 0.98f, t);
                    extB = MathUtils.lerp(0.20f, 0.88f, t);
                    extA = 0.45f + t * 0.47f;
                } else if (totalEmission > 0.10f) {
                    float t = (totalEmission - 0.10f) / 0.25f;
                    extR = MathUtils.lerp(0.86f, 1.0f, t);
                    extG = MathUtils.lerp(0.30f, 0.72f, t); // reddish-orange to amber
                    extB = MathUtils.lerp(0.04f, 0.20f, t);
                    extA = 0.22f + t * 0.23f;
                } else {
                    float t = totalEmission / 0.10f;
                    extR = 0.76f * t;
                    extG = 0.22f * t; // deep reddish-brown
                    extB = 0.03f * t;
                    extA = MathUtils.clamp(t * 0.22f, 0f, 1f);
                }

                // Deep black event horizon core
                float holeR = 0.0002f, holeG = 0.00025f, holeB = 0.0005f, holeA = 1.0f;

                // Seamless anti-aliased interpolation between event horizon and outer emission
                float outR = MathUtils.lerp(holeR, extR, exteriorWeight);
                float outG = MathUtils.lerp(holeG, extG, exteriorWeight);
                float outB = MathUtils.lerp(holeB, extB, exteriorWeight);
                float outA = MathUtils.lerp(holeA, extA, exteriorWeight);

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

    private static final class ForegroundNebulaPuff {
        final float relU;       // Offset along disc major axis in units of horizon (negative = left, positive = right)
        final float relV;       // Offset perpendicular to disc in units of horizon (negative = below disc)
        final float relWidth;   // Width along disc plane in units of horizon
        final float relHeight;  // Height perpendicular to disc in units of horizon
        final float tilt;       // Slight tilt angle in degrees relative to disc plane
        final float driftSpeed; // Dynamic slow drift/pulsing speed
        final float tintR, tintG, tintB; // Micro color variation (dark warm charcoal/brown)
        final float baseAlpha;  // Base opacity

        ForegroundNebulaPuff(float relU, float relV, float relWidth, float relHeight, float tilt,
                             float driftSpeed, float tintR, float tintG, float tintB, float baseAlpha) {
            this.relU = relU;
            this.relV = relV;
            this.relWidth = relWidth;
            this.relHeight = relHeight;
            this.tilt = tilt;
            this.driftSpeed = driftSpeed;
            this.tintR = tintR;
            this.tintG = tintG;
            this.tintB = tintB;
            this.baseAlpha = baseAlpha;
        }
    }

    private static final ForegroundNebulaPuff[] NEBULA_PUFFS = new ForegroundNebulaPuff[] {
        // --- Lower-Left Nebula Clump (overlapping lower-left horizon & bottom halo) ---
        // 1. Directly overlapping the lower-left edge of the black hole horizon & bottom halo
        new ForegroundNebulaPuff(-0.54f, -0.70f, 1.55f, 1.08f, -2f, 0.22f, 1.04f, 0.96f, 0.88f, 0.84f),
        // 2. Overlapping the bottom-left halo ring
        new ForegroundNebulaPuff(-0.36f, -0.96f, 1.35f, 0.92f, 3f, -0.18f, 0.96f, 0.92f, 0.90f, 0.80f),
        // 3. Spanning lower-left space beneath accretion disc
        new ForegroundNebulaPuff(-0.95f, -0.80f, 1.70f, 1.15f, -4f, 0.16f, 1.08f, 0.98f, 0.88f, 0.76f),
        // 4. Deep lower-left under-hang
        new ForegroundNebulaPuff(-0.72f, -1.22f, 1.45f, 1.00f, 2f, 0.20f, 1.00f, 0.92f, 0.85f, 0.70f),
        // 5. Outer wispy trail toward left edge
        new ForegroundNebulaPuff(-1.42f, -0.92f, 1.55f, 0.95f, -5f, -0.14f, 0.92f, 0.92f, 0.95f, 0.62f),
        // 6. Far outer left haze
        new ForegroundNebulaPuff(-1.85f, -1.10f, 1.35f, 0.85f, -2f, 0.18f, 0.88f, 0.88f, 0.92f, 0.48f),

        // --- Lower-Right Nebula Clump (overlapping lower-right horizon & bottom halo) ---
        // 7. Directly overlapping the lower-right edge of the black hole horizon & bottom halo
        new ForegroundNebulaPuff(0.50f, -0.68f, 1.45f, 1.00f, 3f, -0.20f, 1.02f, 0.96f, 0.88f, 0.82f),
        // 8. Overlapping the bottom-right halo ring
        new ForegroundNebulaPuff(0.34f, -0.94f, 1.25f, 0.88f, -2f, 0.16f, 0.95f, 0.92f, 0.90f, 0.76f),
        // 9. Spanning lower-right space beneath accretion disc
        new ForegroundNebulaPuff(0.92f, -0.78f, 1.58f, 1.08f, 4f, -0.15f, 1.06f, 0.96f, 0.86f, 0.72f),
        // 10. Outer wispy trail toward right edge
        new ForegroundNebulaPuff(1.38f, -0.96f, 1.40f, 0.92f, 2f, 0.18f, 0.94f, 0.90f, 0.92f, 0.58f),
        // 11. Far outer right haze
        new ForegroundNebulaPuff(1.78f, -1.10f, 1.25f, 0.80f, -1f, -0.16f, 0.90f, 0.88f, 0.90f, 0.45f)
    };
}
