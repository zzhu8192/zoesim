package zoesim.blackhole;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;
import java.util.Random;

/** Simulation state and dynamics: gravity, particle movement, accretion, and collisions. */
public final class BlackHoleWorld {
    private final Random random = new Random();
    private final Array<CosmicParticle> cosmicParticles = new Array<>();
    private final Array<JetParticle> jetParticles = new Array<>();
    private final Array<AbsorptionFlare> absorptionFlares = new Array<>();
    private final BackgroundStar[] backgroundStars = new BackgroundStar[BlackHolePhysics.BACKGROUND_STAR_COUNT];
    private final BlackHoleAudio audio;

    private float arenaX;
    private float arenaY;
    private float arenaRadius;
    private float fieldRadius;
    private float speed = 1f;
    private float growthRate = 1f;
    private float mass = BlackHolePhysics.INITIAL_MASS;
    private float simTime = 0f;
    private float spawnTimer;
    private float jetTimer;
    private int absorbedParticles;
    private int startCount = 180;
    private boolean paused;
    private boolean fieldConsumed;

    public BlackHoleWorld(BlackHoleAudio audio) {
        this.audio = audio;
        createBackgroundStars();
    }

    public void resize(float arenaX, float arenaY, float arenaRadius) {
        this.arenaX = arenaX;
        this.arenaY = arenaY;
        this.arenaRadius = arenaRadius;
        if (com.badlogic.gdx.Gdx.graphics != null && com.badlogic.gdx.Gdx.graphics.getWidth() > 0) {
            float screenW = com.badlogic.gdx.Gdx.graphics.getWidth();
            float screenH = com.badlogic.gdx.Gdx.graphics.getHeight();
            float maxDx = Math.max(arenaX, screenW - arenaX);
            float maxDy = Math.max(arenaY, screenH - arenaY);
            this.fieldRadius = (float)Math.hypot(maxDx, maxDy);
        } else {
            this.fieldRadius = arenaRadius;
        }
    }

    public void reset() {
        cosmicParticles.clear();
        jetParticles.clear();
        absorptionFlares.clear();
        absorbedParticles = 0;
        mass = BlackHolePhysics.INITIAL_MASS;
        simTime = 0f;
        spawnTimer = 0f;
        jetTimer = 0f;
        fieldConsumed = false;
        paused = false;
        audio.reset();
        for (int i = 0; i < startCount; i++) {
            spawnCosmicParticle(false);
        }
        audio.startAmbience();
    }

    public void update(float dt) {
        if (paused || fieldConsumed) return;
        audio.update(dt);
        float simulationDt = dt * speed;
        simTime += simulationDt;
        spawnTimer += simulationDt;
        jetTimer += simulationDt;
        float horizon = horizon();
        while (spawnTimer > BlackHolePhysics.COSMIC_SPAWN_INTERVAL && cosmicParticles.size < BlackHolePhysics.MAX_COSMIC_PARTICLES) {
            spawnTimer -= BlackHolePhysics.COSMIC_SPAWN_INTERVAL;
            spawnCosmicParticle(true);
        }
        if (jetTimer > .022f) {
            jetTimer = 0f;
            emitJets(horizon);
        }
        for (Iterator<CosmicParticle> it = cosmicParticles.iterator(); it.hasNext();) {
            CosmicParticle particle = it.next();
            float dx = arenaX - particle.position.x;
            float dy = arenaY - particle.position.y;
            float dz = -particle.position.z;
            float distanceSquared = BlackHolePhysics.spatialDistanceSquared(dx, dy, dz);
            float distance = (float)Math.sqrt(distanceSquared);
            float disruptionRadius = horizon + Math.max(1f, particle.size * .72f);
            if (distance < disruptionRadius) {
                absorb(particle);
                it.remove();
                continue;
            }
            float gravity = BlackHolePhysics.gravitationalAcceleration(mass, distanceSquared, 400f) * 5.06f;
            // Relativistic gravitational acceleration bonus: surges strongly within near-horizon zone
            float horizonSq = horizon * horizon;
            float relativisticBonus = 1f + 2.4f * horizonSq / Math.max(distanceSquared, horizonSq);
            gravity *= relativisticBonus;

            particle.velocity.add(dx / distance * gravity * simulationDt,
                    dy / distance * gravity * simulationDt, dz / distance * gravity * simulationDt);
            // Steady orbital decay so bodies smoothly convert angular momentum into inward acceleration
            particle.velocity.scl(Math.max(.96f, 1f - simulationDt * .008f));

            // Viscous coupling / frame-dragging in inner accretion zone:
            // As bodies plunge close to the event horizon (r < horizon * 2.2f),
            // their orbital velocity dynamically synchronizes with the 3x fast inner spin of the accretion disc
            if (distance < horizon * 2.2f && distance > 1f) {
                float dragFactor = MathUtils.clamp(1f - (distance - horizon) / (horizon * 1.2f), 0f, 1f);
                float targetSpeed = Math.min(680f, distance * 7.4f);
                float currentSpeed = particle.velocity.len();
                if (currentSpeed > 1f) {
                    float syncedSpeed = MathUtils.lerp(currentSpeed, targetSpeed, dragFactor * 0.16f * simulationDt * 60f);
                    particle.velocity.scl(syncedSpeed / currentSpeed);
                }
            }

            particle.velocity.limit(950f);
            particle.position.mulAdd(particle.velocity, simulationDt);
            particle.age += simulationDt;
            particle.wobble += particle.spin * simulationDt;
            dx = particle.position.x - arenaX;
            dy = particle.position.y - arenaY;
            dz = particle.position.z;
            distance = (float)Math.sqrt(BlackHolePhysics.spatialDistanceSquared(dx, dy, dz));
            if (distance < disruptionRadius) {
                absorb(particle);
                it.remove();
                continue;
            }
            float boundary = getFieldRadius() - particle.size - 5f;
            if (distance > boundary) {
                float nx = dx / distance, ny = dy / distance, nz = dz / distance;
                particle.position.set(arenaX + nx * boundary, arenaY + ny * boundary, nz * boundary);
                float outward = particle.velocity.x * nx + particle.velocity.y * ny + particle.velocity.z * nz;
                if (outward > 0) {
                    particle.velocity.add(-1.75f * outward * nx,
                            -1.75f * outward * ny, -1.75f * outward * nz);
                }
                particle.velocity.scl(.92f);
            }
        }
        for (Iterator<JetParticle> it = jetParticles.iterator(); it.hasNext();) {
            JetParticle jet = it.next();
            jet.position.mulAdd(jet.velocity, simulationDt);
            jet.velocity.scl(1f + simulationDt * .08f);
            jet.age += simulationDt;
            if (jet.age > jet.life || jet.position.dst2(arenaX, arenaY) > getFieldRadius() * getFieldRadius()) {
                it.remove();
            }
        }
        for (Iterator<AbsorptionFlare> it = absorptionFlares.iterator(); it.hasNext();) {
            AbsorptionFlare flare = it.next();
            flare.age += simulationDt;
            if (flare.age > flare.life) {
                it.remove();
            }
        }
        if (BlackHolePhysics.fillsField(mass, getFieldRadius())) {
            fieldConsumed = true;
            paused = true;
            audio.stopAmbience();
        }
    }

    public void spawnCosmicParticle(boolean atBoundary) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float horizon = horizon();
        int type = random.nextInt(CelestialTextureBank.BODY_TYPE_COUNT);
        float sample = random.nextFloat(), size, massScale;
        switch (type) {
            case 0: size = 1.6f + sample * sample * 3.2f; massScale = 0.75f; break; // Terra (rocky earth-like)
            case 1: size = 1.4f + sample * 2.8f; massScale = 0.70f; break;          // Mars-like (desert world)
            case 2: size = 1.2f + sample * 2.5f; massScale = 0.85f; break;          // Volcanic (dense basalt/iron)
            case 3: size = 3.2f + sample * 4.2f; massScale = 0.22f; break;          // Gas Giant (diffuse Jovian)
            case 4: size = 4.0f + sample * 4.5f; massScale = 0.28f; break;          // Sun / Protostar (diffuse stellar plasma)
            case 5: size = 1.0f + sample * 2.1f; massScale = 0.50f; break;          // Europa ice moon (water ice)
            case 6: size = 0.8f + sample * sample * 2.2f; massScale = 0.60f; break; // Asteroid (chondrite stone)
            case 7: size = 1.0f + sample * 1.9f; massScale = 0.45f; break;          // Plutonian dwarf (porous ice)
            case 8: size = 2.8f + sample * 3.8f; massScale = 0.26f; break;          // Ice giant (volatile mantle)
            default: size = 0.7f + sample * 1.8f; massScale = 0.35f; break;        // Comet (dust/volatile ice)
        }
        float innerRadius = horizon + Math.max(36f, size * 1.5f + 24f);
        float outerRadius = Math.max(innerRadius + 20f, getFieldRadius() - Math.max(8f, size * .72f + 4f));
        float radius;
        if (atBoundary) {
            // Continuous spawning: staggered inflow across outer-to-mid cosmic field (continuous incoming stream)
            float minInflow = innerRadius + 60f;
            float maxInflow = outerRadius;
            float inflowFrac = random.nextFloat() < 0.65f ? random.nextFloat() * 0.50f : random.nextFloat();
            radius = minInflow + inflowFrac * Math.max(1f, maxInflow - minInflow);
        } else {
            // Initial cloud: smooth, uniform radial distribution spanning all inward spiral stages
            radius = innerRadius + random.nextFloat() * Math.max(1f, outerRadius - innerRadius);
        }

        // Dynamic Keplerian orbital velocity scaled for lively visual pace
        float circularSpeed = (float)Math.sqrt(BlackHolePhysics.gravitationalParameter(mass) / radius) * 2.25f;
        float speedVariation = 0.94f + random.nextFloat() * 0.16f;
        float orbital = circularSpeed * speedVariation;
        // Inward spiral drift: yields 2-4 energetic, accelerating revolutions
        float inward = -5.5f - random.nextFloat() * 8.5f;

        // True 3D orbital distribution across all space axes (X, Y, Z):
        // 1. Accretion Disc Co-Planar (35%): Aligned right along the -17° accretion disc plane
        // 2. Tilted Elliptical / Inclined (40%): Moderate 3D orbital inclinations (15° to 55°)
        // 3. High-Inclination & Polar (25%): Steep/perpendicular orbits crossing poles & slicing all 3D axes
        float planeCategory = random.nextFloat();
        float diskAngle = BlackHolePhysics.ACCRETION_DISK_ANGLE * MathUtils.degreesToRadians;
        float nx, ny, nz; // Normal vector of the orbital plane

        if (planeCategory < 0.35f) {
            // Accretion disc plane normal: roughly perpendicular to disk angle in 3D
            // Disc axis has tilt angle -17° with small dispersion (±8°)
            float tilt = diskAngle + (random.nextFloat() - 0.5f) * 0.28f;
            float pitch = (random.nextFloat() - 0.5f) * 0.22f;
            nx = -MathUtils.sin(tilt);
            ny = MathUtils.cos(tilt);
            nz = pitch;
        } else if (planeCategory < 0.75f) {
            // Moderate random 3D orbital plane
            float theta = random.nextFloat() * MathUtils.PI2;
            float phi = (float)Math.acos(1f - random.nextFloat() * 1.2f); // Inclined from Z axis
            nx = MathUtils.sin(phi) * MathUtils.cos(theta);
            ny = MathUtils.sin(phi) * MathUtils.sin(theta);
            nz = MathUtils.cos(phi);
        } else {
            // High-inclination / polar orbital plane
            float theta = random.nextFloat() * MathUtils.PI2;
            float phi = (float)Math.acos(random.nextFloat() * 0.5f); // Near equatorial normal => polar orbit
            nx = MathUtils.sin(phi) * MathUtils.cos(theta);
            ny = MathUtils.sin(phi) * MathUtils.sin(theta);
            nz = MathUtils.cos(phi);
        }

        // Normalize plane normal
        float normLen = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= normLen; ny /= normLen; nz /= normLen;

        // Construct orthonormal basis (u, v) in the orbital plane
        float upX = 0f, upY = 0f, upZ = 1f;
        if (Math.abs(nz) > 0.90f) {
            upX = 1f; upY = 0f; upZ = 0f;
        }
        // u = up x n
        float ux = upY * nz - upZ * ny;
        float uy = upZ * nx - upX * nz;
        float uz = upX * ny - upY * nx;
        float uLen = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= uLen; uy /= uLen; uz /= uLen;
        // v = n x u
        float vx_plane = ny * uz - nz * uy;
        float vy_plane = nz * ux - nx * uz;
        float vz_plane = nx * uy - ny * ux;

        // Position on the orbital plane circle
        float cosA = MathUtils.cos(angle);
        float sinA = MathUtils.sin(angle);
        float radialUnitX = ux * cosA + vx_plane * sinA;
        float radialUnitY = uy * cosA + vy_plane * sinA;
        float radialUnitZ = uz * cosA + vz_plane * sinA;

        // Tangent orbital velocity direction (85% prograde, 15% retrograde)
        float dir = random.nextFloat() < 0.85f ? 1f : -1f;
        float tangentUnitX = (-ux * sinA + vx_plane * cosA) * dir;
        float tangentUnitY = (-uy * sinA + vy_plane * cosA) * dir;
        float tangentUnitZ = (-uz * sinA + vz_plane * cosA) * dir;

        float x = arenaX + radialUnitX * radius;
        float y = arenaY + radialUnitY * radius;
        float z = radialUnitZ * radius;
        float vx = tangentUnitX * orbital + radialUnitX * inward;
        float vy = tangentUnitY * orbital + radialUnitY * inward;
        float vz = tangentUnitZ * orbital + radialUnitZ * inward;
        float spin = (.85f + random.nextFloat() * 3.5f) * (random.nextBoolean() ? 1f : -1f);
        cosmicParticles.add(new CosmicParticle(x, y, z, vx, vy, vz, random.nextFloat() * 360f,
                size, massScale, type, BlackHolePhysics.CELESTIAL_PALETTE[type], spin));
    }

    private void emitJets(float horizon) {
        for (int pole = -1; pole <= 1; pole += 2) {
            float baseAngle = BlackHolePhysics.JET_AXIS + (pole < 0 ? MathUtils.PI : 0f);
            for (int i = 0; i < 4; i++) {
                float spread = (random.nextFloat() + random.nextFloat() - 1f) * 0.045f;
                float angle = baseAngle + spread;
                float radius = horizon * (0.85f + random.nextFloat() * 0.35f);
                float velocity = 260f + random.nextFloat() * 140f;
                boolean shockKnot = random.nextFloat() < 0.045f;
                float startSize = shockKnot ? 12f + random.nextFloat() * 6f : 4.5f + random.nextFloat() * 4f;
                float expandRate = shockKnot ? 48f : 30f + random.nextFloat() * 14f;
                float life = 0.9f + random.nextFloat() * 0.65f;
                float brightness = shockKnot ? 1.4f : 0.85f + random.nextFloat() * 0.25f;
                float rot = random.nextFloat() * MathUtils.PI2;
                jetParticles.add(new JetParticle(
                        arenaX + MathUtils.cos(angle) * radius,
                        arenaY + MathUtils.sin(angle) * radius,
                        MathUtils.cos(angle) * velocity,
                        MathUtils.sin(angle) * velocity,
                        startSize, life, brightness,
                        random.nextFloat(), shockKnot, expandRate, rot));
            }
        }
    }

    private void absorb(CosmicParticle particle) {
        mass += particle.mass * growthRate * BlackHolePhysics.GROWTH_FACTOR_MULTIPLIER;
        absorbedParticles++;
        float angle = MathUtils.atan2(particle.position.y - arenaY, particle.position.x - arenaX);
        float flareSize = particle.bodyType == 5 ? MathUtils.clamp(particle.size * .15f, 2f, 4.5f)
                : particle.bodyType == 2 ? MathUtils.clamp(particle.size * .38f, 1.8f, 5.5f)
                : MathUtils.clamp(particle.size, 1.1f, 6f);
        absorptionFlares.add(new AbsorptionFlare(angle, horizon() * 1.04f, flareSize,
                .42f + Math.min(.36f, flareSize * .045f), particle.color, random));
        if (absorptionFlares.size > 140) {
            absorptionFlares.removeIndex(0);
        }
        audio.playAbsorb(particle.size);
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

    public float horizon() {
        return BlackHolePhysics.eventHorizon(mass);
    }

    public Array<CosmicParticle> getCosmicParticles() { return cosmicParticles; }
    public Array<JetParticle> getJetParticles() { return jetParticles; }
    public Array<AbsorptionFlare> getAbsorptionFlares() { return absorptionFlares; }
    public BackgroundStar[] getBackgroundStars() { return backgroundStars; }
    public float getArenaX() { return arenaX; }
    public float getArenaY() { return arenaY; }
    public float getArenaRadius() { return arenaRadius; }
    public float getFieldRadius() { return fieldRadius > 0f ? fieldRadius : arenaRadius; }
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) { this.speed = speed; }
    public float getSimTime() { return simTime; }
    public float getGrowthRate() { return growthRate; }
    public void setGrowthRate(float growthRate) { this.growthRate = BlackHolePhysics.clampGrowthRate(growthRate); }
    public float getMass() { return mass; }
    public int getAbsorbedParticles() { return absorbedParticles; }
    public int getStartCount() { return startCount; }
    public void setStartCount(int startCount) { this.startCount = startCount; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }
    public void togglePaused() { this.paused = !this.paused; }
    public boolean isFieldConsumed() { return fieldConsumed; }
}
