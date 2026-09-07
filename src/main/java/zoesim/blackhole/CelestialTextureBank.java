package zoesim.blackhole;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/**
 * Procedurally generates realistic, multi-chromatic 3D spherical planetary and stellar textures
 * with continuous 360-degree rotational animation frames.
 */
public final class CelestialTextureBank {
    public static final int FRAME_COUNT = 24;
    public static final int BODY_TYPE_COUNT = 10;
    public static final int FRAME_SIZE = 48;

    private final Texture atlasTexture;
    private final TextureRegion[][] frames;
    private final Texture jetCloudTexture;
    private final Texture accretionCloudTexture;
    private final Texture foggyBulbTexture;
    private final Texture nebulaCloudTexture;

    public CelestialTextureBank() {
        Pixmap atlasMap = new Pixmap(FRAME_COUNT * FRAME_SIZE, BODY_TYPE_COUNT * FRAME_SIZE, Pixmap.Format.RGBA8888);
        atlasMap.setBlending(Pixmap.Blending.None);
        atlasMap.setColor(0f, 0f, 0f, 0f);
        atlasMap.fill();

        for (int type = 0; type < BODY_TYPE_COUNT; type++) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                float rotationAngle = frame * (MathUtils.PI2 / FRAME_COUNT);
                renderBodyFrame(atlasMap, type, frame * FRAME_SIZE, type * FRAME_SIZE, rotationAngle);
            }
        }

        atlasTexture = new Texture(atlasMap);
        atlasMap.dispose();
        atlasTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        frames = new TextureRegion[BODY_TYPE_COUNT][FRAME_COUNT];
        for (int type = 0; type < BODY_TYPE_COUNT; type++) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                frames[type][frame] = new TextureRegion(atlasTexture,
                        frame * FRAME_SIZE, type * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
            }
        }

        jetCloudTexture = createJetCloudTexture();
        accretionCloudTexture = createAccretionCloudTexture();
        foggyBulbTexture = createFoggyBulbTexture();
        nebulaCloudTexture = createNebulaCloudTexture();
    }

    public TextureRegion getFrame(int bodyType, float rotation) {
        int safeType = MathUtils.clamp(bodyType, 0, BODY_TYPE_COUNT - 1);
        float normalized = (rotation / MathUtils.PI2) % 1.0f;
        if (normalized < 0f) normalized += 1.0f;
        int frame = (int)(normalized * FRAME_COUNT) % FRAME_COUNT;
        return frames[safeType][frame];
    }

    public Texture getJetCloudTexture() {
        return jetCloudTexture;
    }

    public Texture getAccretionCloudTexture() {
        return accretionCloudTexture;
    }

    public Texture getFoggyBulbTexture() {
        return foggyBulbTexture;
    }

    public Texture getNebulaCloudTexture() {
        return nebulaCloudTexture;
    }

    private static void renderBodyFrame(Pixmap map, int type, int originX, int originY, float rotAngle) {
        final float radius = 20.5f;
        final float center = FRAME_SIZE * 0.5f;

        for (int py = 0; py < FRAME_SIZE; py++) {
            for (int px = 0; px < FRAME_SIZE; px++) {
                float dx = px - center + 0.5f;
                float dy = py - center + 0.5f;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);

                if (dist > radius + 1.8f) continue;

                float alpha = 1.0f;
                if (dist > radius) {
                    alpha = MathUtils.clamp((radius + 1.8f - dist) / 1.8f, 0f, 1f);
                }

                float nz = (float)Math.sqrt(Math.max(0f, 1f - (dist * dist) / (radius * radius)));
                float nx = dx / radius;
                float ny = dy / radius;

                float lat = (float)Math.asin(MathUtils.clamp(ny, -1f, 1f));
                float lon = (float)Math.atan2(nx, nz) + rotAngle;

                // 3D Lambertian directional illumination + soft limb darkening
                float lightDot = MathUtils.clamp(nx * -0.28f + ny * 0.18f + nz * 0.94f, 0f, 1f);
                float diffuse = 0.34f + 0.66f * lightDot;
                float limb = (float)Math.pow(nz, 0.45f);

                float r = 0f, g = 0f, b = 0f;

                switch (type) {
                    case 0: { // 0: Continental Terra World (emerald continents, desert ochres, deep navy ocean, clouds, polar ice)
                        float h = MathUtils.sin(lon * 2f) * MathUtils.cos(lat * 3f)
                                + 0.5f * MathUtils.sin(lon * 5f + 1.2f) * MathUtils.sin(lat * 4f)
                                + 0.25f * MathUtils.cos(lon * 9f - lat * 6f);
                        float absLat = Math.abs(lat);
                        if (absLat > 1.25f) { // Polar ice
                            r = 0.95f; g = 0.97f; b = 1.0f;
                        } else if (h > 0.10f) { // Continents
                            float veg = MathUtils.clamp((h - 0.10f) * 2.5f, 0f, 1f);
                            r = MathUtils.lerp(0.62f, 0.22f, veg);
                            g = MathUtils.lerp(0.48f, 0.52f, veg);
                            b = MathUtils.lerp(0.28f, 0.22f, veg);
                        } else { // Multi-toned ocean
                            float depth = MathUtils.clamp((0.10f - h) * 3f, 0f, 1f);
                            r = MathUtils.lerp(0.12f, 0.05f, depth);
                            g = MathUtils.lerp(0.28f, 0.14f, depth);
                            b = MathUtils.lerp(0.45f, 0.28f, depth);
                        }
                        // Semi-transparent cloud swirls
                        float cloud = MathUtils.sin(lon * 4f + lat * 2f) * MathUtils.cos(lat * 5f);
                        if (cloud > 0.42f) {
                            float cw = (cloud - 0.42f) * 1.6f;
                            r = MathUtils.lerp(r, 1f, cw);
                            g = MathUtils.lerp(g, 1f, cw);
                            b = MathUtils.lerp(b, 1f, cw);
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    case 1: { // 1: Mars-like Rust Planet (iron-oxide terracotta, amber dunes, dark basalt volcanic fields, polar frost)
                        float h = MathUtils.sin(lon * 3f) * MathUtils.cos(lat * 2f)
                                + 0.4f * MathUtils.sin(lon * 7f) * MathUtils.cos(lat * 5f);
                        float absLat = Math.abs(lat);
                        if (absLat > 1.35f) { // Carbon-dioxide ice cap
                            r = 0.92f; g = 0.94f; b = 0.96f;
                        } else if (h > 0.25f) { // Dark volcanic basaltic maria (Syrtis Major)
                            r = 0.32f; g = 0.24f; b = 0.20f;
                        } else { // Rust dunes and amber desert
                            float sand = MathUtils.clamp(h * 2f + 0.5f, 0f, 1f);
                            r = MathUtils.lerp(0.78f, 0.92f, sand);
                            g = MathUtils.lerp(0.38f, 0.52f, sand);
                            b = MathUtils.lerp(0.18f, 0.28f, sand);
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    case 2: { // 2: Volcanic Magma World (charcoal crust with glowing molten yellow, orange, and crimson magma fissures)
                        float fissure = Math.abs(MathUtils.sin(lon * 5.5f + MathUtils.sin(lat * 7f) * 1.4f))
                                + Math.abs(MathUtils.cos(lat * 6f + MathUtils.sin(lon * 4f)));
                        if (fissure < 0.28f) { // Molten fissures (self-illuminating)
                            float heat = 1f - fissure / 0.28f;
                            r = MathUtils.lerp(0.92f, 1.0f, heat);
                            g = MathUtils.lerp(0.15f, 0.92f, heat * heat);
                            b = MathUtils.lerp(0.01f, 0.50f, heat * heat * heat);
                        } else { // Basaltic charcoal crust
                            float grain = MathUtils.sin(lon * 17f) * MathUtils.cos(lat * 17f) * 0.05f;
                            r = (0.18f + grain) * diffuse * limb;
                            g = (0.14f + grain) * diffuse * limb;
                            b = (0.12f + grain) * diffuse * limb;
                        }
                        break;
                    }
                    case 3: { // 3: Jovian Gas Giant (realistic multi-layered cream, ochre, amber, deep mahogany-red storm belts, and Great Red Spot)
                        float belts = MathUtils.sin(lat * 14f) * 0.40f
                                + MathUtils.sin(lat * 26f + 0.5f) * 0.22f
                                + MathUtils.sin(lat * 42f) * 0.12f;
                        // Atmospheric zonal flow turbulence & eddy swirls
                        float eddies = MathUtils.sin(lon * 7f + lat * 18f) * 0.14f
                                + MathUtils.sin(lon * 13f - lat * 10f) * 0.08f;
                        float normBelt = MathUtils.clamp(belts + eddies + 0.5f, 0f, 1f);

                        // Color palette: deep burnt sienna / mahogany zones vs rich cream / warm ochre belts
                        if (normBelt < 0.35f) {
                            float bT = normBelt / 0.35f;
                            r = MathUtils.lerp(0.58f, 0.78f, bT);
                            g = MathUtils.lerp(0.28f, 0.48f, bT);
                            b = MathUtils.lerp(0.16f, 0.28f, bT);
                        } else if (normBelt < 0.70f) {
                            float bT = (normBelt - 0.35f) / 0.35f;
                            r = MathUtils.lerp(0.78f, 0.94f, bT);
                            g = MathUtils.lerp(0.48f, 0.78f, bT);
                            b = MathUtils.lerp(0.28f, 0.55f, bT);
                        } else {
                            float bT = (normBelt - 0.70f) / 0.30f;
                            r = MathUtils.lerp(0.94f, 0.98f, bT);
                            g = MathUtils.lerp(0.78f, 0.92f, bT);
                            b = MathUtils.lerp(0.55f, 0.82f, bT);
                        }

                        // Great Red Spot storm vortex with swirling spiral core
                        float dLat = lat - (-0.30f);
                        float dLon = (lon % MathUtils.PI2) - 1.55f;
                        if (dLon < -MathUtils.PI) dLon += MathUtils.PI2;
                        if (dLon > MathUtils.PI) dLon -= MathUtils.PI2;
                        float spotDistSq = (dLon / 0.36f) * (dLon / 0.36f) + (dLat / 0.16f) * (dLat / 0.16f);
                        if (spotDistSq < 1f) {
                            float spotCore = 1f - spotDistSq;
                            float swirl = MathUtils.sin((float)Math.sqrt(spotDistSq) * 12f - lon * 2f) * 0.15f;
                            r = MathUtils.lerp(r, 0.88f + swirl, spotCore);
                            g = MathUtils.lerp(g, 0.24f + swirl, spotCore);
                            b = MathUtils.lerp(b, 0.14f, spotCore);
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    case 4: { // 4: Luminous Solar Protostar (golden photosphere, convective granules, coronal edge)
                        float gran = MathUtils.sin(lon * 15f) * MathUtils.sin(lat * 15f)
                                + MathUtils.sin(lon * 27f + lat * 22f) * 0.4f;
                        float t = MathUtils.clamp(gran * 0.35f + 0.65f, 0f, 1f);
                        r = 1.0f;
                        g = MathUtils.lerp(0.72f, 0.96f, t);
                        b = MathUtils.lerp(0.24f, 0.72f, t * t);
                        float starGlow = 0.85f + 0.15f * limb;
                        r *= starGlow; g *= starGlow; b *= starGlow;
                        break;
                    }
                    case 5: { // 5: Europa-like Cracked Ice Moon (silvery white crust with deep reddish-brown and amber tectonic fracture networks)
                        float crack = Math.abs(MathUtils.sin(lon * 8f + MathUtils.cos(lat * 12f) * 1.6f))
                                * Math.abs(MathUtils.cos(lat * 9f + MathUtils.sin(lon * 7f) * 1.4f));
                        if (crack < 0.14f) { // Reddish-brown fracture lineae
                            r = 0.68f; g = 0.38f; b = 0.24f;
                        } else { // Silvery ice plains
                            float iceMottle = MathUtils.sin(lon * 19f) * MathUtils.cos(lat * 19f) * 0.05f;
                            r = 0.88f + iceMottle;
                            g = 0.92f + iceMottle;
                            b = 0.97f + iceMottle;
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    case 6: { // 6: Chondrite Asteroid (cratered basaltic grey stone with metallic reflective flecks and crater rims)
                        float base = 0.45f + MathUtils.sin(lon * 9f) * MathUtils.cos(lat * 9f) * 0.08f;
                        float crater1 = MathUtils.sin(lon * 6f - 1f) * MathUtils.sin(lat * 6f);
                        float crater2 = MathUtils.cos(lon * 8f + 2f) * MathUtils.cos(lat * 7f - 1f);
                        float shade = MathUtils.clamp(base + crater1 * 0.14f + crater2 * 0.12f, 0.18f, 0.82f);
                        // Metallic reflective flecks
                        float fleck = MathUtils.sin(lon * 31f) * MathUtils.cos(lat * 31f);
                        if (fleck > 0.82f) {
                            r = 0.92f; g = 0.88f; b = 0.75f;
                        } else {
                            r = shade * 0.98f; g = shade * 0.95f; b = shade * 0.90f;
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    case 7: { // 7: Plutonian Dwarf World (reddish-brown tholin terrain and bright silvery nitrogen ice heart)
                        float tholin = MathUtils.sin(lon * 1.8f) * MathUtils.cos(lat * 2.2f);
                        // Bright nitrogen ice heart
                        float dLat = lat - (0.15f);
                        float dLon = (lon % MathUtils.PI2) - 1.2f;
                        if (dLon < -MathUtils.PI) dLon += MathUtils.PI2;
                        if (dLon > MathUtils.PI) dLon -= MathUtils.PI2;
                        if ((dLon / 0.42f) * (dLon / 0.42f) + (dLat / 0.35f) * (dLat / 0.35f) < 1f) {
                            r = 0.94f; g = 0.92f; b = 0.88f; // Bright nitrogen ice heart
                        } else if (tholin > 0.15f) {
                            r = 0.65f; g = 0.34f; b = 0.22f; // Reddish tholin
                        } else {
                            r = 0.45f; g = 0.38f; b = 0.35f; // Muted bedrock
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    case 8: { // 8: Azure Ice Giant (spherical Neptune/Uranus with atmospheric banding and polar hood)
                        float band = MathUtils.sin(lat * 10f) * 0.28f + MathUtils.sin(lat * 22f) * 0.14f;
                        float t = MathUtils.clamp(band * 0.5f + 0.5f, 0f, 1f);
                        r = MathUtils.lerp(0.12f, 0.24f, t);
                        g = MathUtils.lerp(0.35f, 0.54f, t);
                        b = MathUtils.lerp(0.70f, 0.88f, t);
                        // High-altitude white cirrus clouds
                        float cirrus = MathUtils.sin(lon * 8f + lat * 16f) * MathUtils.cos(lat * 8f);
                        if (cirrus > 0.55f) {
                            float cw = (cirrus - 0.55f) * 2.2f;
                            r = MathUtils.lerp(r, 0.85f, cw);
                            g = MathUtils.lerp(g, 0.94f, cw);
                            b = MathUtils.lerp(b, 1.0f, cw);
                        }
                        r *= diffuse * limb; g *= diffuse * limb; b *= diffuse * limb;
                        break;
                    }
                    default: { // 9: Comet Nucleus (mottled dusty ice)
                        float ice = MathUtils.sin(lon * 8f) * MathUtils.cos(lat * 8f) * 0.2f + 0.55f;
                        r = ice * 0.88f * diffuse * limb;
                        g = ice * 0.92f * diffuse * limb;
                        b = ice * 0.98f * diffuse * limb;
                        break;
                    }
                }

                map.setColor(MathUtils.clamp(r, 0f, 1f), MathUtils.clamp(g, 0f, 1f),
                        MathUtils.clamp(b, 0f, 1f), alpha);
                map.drawPixel(originX + px, originY + py);
            }
        }
    }

    private static Texture createJetCloudTexture() {
        final int size = 64;
        final float center = size * 0.5f;
        final float radius = center - 2f;
        Pixmap map = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None);
        map.setColor(0f, 0f, 0f, 0f);
        map.fill();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center + 0.5f;
                float dy = y - center + 0.5f;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist >= radius) continue;

                float angle = MathUtils.atan2(dy, dx);
                float turbulence = MathUtils.sin(angle * 5f + dist * 0.22f) * 0.12f
                        + MathUtils.sin(angle * 8f - dist * 0.35f) * 0.08f;
                float normDist = MathUtils.clamp(dist / radius + turbulence, 0f, 1f);
                float core = (float)Math.exp(-normDist * normDist * 3.2f);
                float alpha = MathUtils.clamp(core, 0f, 1f);

                // Hot white-lavender core fading to rich violet
                float red = MathUtils.lerp(0.74f, 1.0f, core);
                float green = MathUtils.lerp(0.45f, 0.95f, core);
                float blue = 1.0f;

                map.setColor(red, green, blue, alpha);
                map.drawPixel(x, y);
            }
        }

        Texture texture = new Texture(map);
        map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static Texture createAccretionCloudTexture() {
        final int size = 96;
        final float center = size * 0.5f;
        final float radius = size * 0.5f - 1.5f;
        Pixmap map = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None);
        map.setColor(0f, 0f, 0f, 0f);
        map.fill();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center + 0.5f;
                float dy = y - center + 0.5f;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist >= radius) continue;

                float angle = MathUtils.atan2(dy, dx);
                // Multi-frequency spiral eddies and organic turbulence matching light halo
                float spiral = MathUtils.sin(angle * 4f + dist * 0.18f) * 0.14f
                        + MathUtils.sin(angle * 7f - dist * 0.28f) * 0.08f;
                float normDist = MathUtils.clamp(dist / radius + spiral, 0f, 1f);

                // Soft Gaussian falloff with subtle edge taper
                float core = (float)Math.exp(-normDist * normDist * 3.4f) * (1f - normDist);
                float alpha = MathUtils.clamp(core, 0f, 1f);

                // Pure diamond-white plasma core with ultra-high luminance
                float red = 1.0f;
                float green = 1.0f;
                float blue = 1.0f;

                map.setColor(red, green, blue, alpha);
                map.drawPixel(x, y);
            }
        }

        Texture texture = new Texture(map);
        map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static Texture createFoggyBulbTexture() {
        final int size = 512;
        final float center = size * 0.5f;
        final float maxRadius = size * 0.5f - 2f;
        Pixmap map = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None);
        map.setColor(0f, 0f, 0f, 0f);
        map.fill();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center + 0.5f;
                float dy = y - center + 0.5f;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist >= maxRadius) continue;

                float u = dist / maxRadius; // [0, 1] normalized radius
                float angle = MathUtils.atan2(dy, dx);

                // Multi-frequency organic fog turbulence so it feels like living diffused cosmic gas
                float turb = MathUtils.sin(angle * 3f + dist * 0.045f) * 0.035f
                        + MathUtils.sin(angle * 7f - dist * 0.075f) * 0.020f;
                float uTurb = MathUtils.clamp(u + turb, 0f, 1f);

                // Smoothly blended dual-scale atmospheric luminance (seamless gradienting across inner bulb & outer shroud)
                float innerBulb = (float)Math.exp(-uTurb * uTurb / (2f * 0.25f * 0.25f)) * 0.38f;
                float outerShroud = (float)Math.exp(-uTurb * uTurb / (2f * 0.54f * 0.54f)) * 0.20f;
                float totalAlpha = (innerBulb + outerShroud) * (1f - uTurb * uTurb);
                if (totalAlpha < 0.002f) continue;

                // Continuous Hermite smoothstep color gradient from champagne-white core to smoky bronze outer shroud
                // Completely eliminates step boundaries or knee artifacts
                float t = MathUtils.clamp((uTurb - 0.06f) / 0.58f, 0f, 1f);
                float smoothT = t * t * (3f - 2f * t); // Cubic Hermite curve
                float red = MathUtils.lerp(1.0f, 0.90f, smoothT);
                float green = MathUtils.lerp(0.96f, 0.65f, smoothT);
                float blue = MathUtils.lerp(0.88f, 0.38f, smoothT);

                map.setColor(red, green, blue, MathUtils.clamp(totalAlpha, 0f, 1f));
                map.drawPixel(x, y);
            }
        }

        Texture texture = new Texture(map);
        map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static Texture createNebulaCloudTexture() {
        final int size = 128;
        final float center = size * 0.5f;
        final float maxRadius = size * 0.5f - 2f;
        Pixmap map = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None);
        map.setColor(0f, 0f, 0f, 0f);
        map.fill();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center + 0.5f;
                float dy = y - center + 0.5f;
                float nx = dx / maxRadius;
                float ny = dy / maxRadius;
                float distSq = nx * nx + ny * ny;
                if (distSq >= 1.0f) continue;

                // 2D Cartesian multi-octave billowing cloud noise (NO angular harmonics / NO snowflake arms)
                // Produces soft, rolling cumulus billows and pillowy convective lobes:
                float w1 = MathUtils.sin(nx * 2.6f + ny * 1.4f + 0.9f) * MathUtils.cos(ny * 2.2f - nx * 1.3f + 0.5f);
                float w2 = MathUtils.sin(nx * 4.8f - ny * 3.6f + 1.8f) * MathUtils.cos(ny * 4.5f + nx * 2.8f - 0.7f);
                float w3 = MathUtils.sin(nx * 8.4f + ny * 7.2f - 2.3f) * 0.45f;
                float billowNoise = w1 * 0.52f + w2 * 0.32f + w3 * 0.16f;

                // Smooth quartic cloud envelope: zero derivative at center and boundary (C1 continuous, zero sharp edges)
                float envelope = (1f - distSq) * (1f - distSq);
                float cloudDensity = MathUtils.clamp(envelope * (0.85f + billowNoise * 0.65f), 0f, 1f);

                // Soft smoky translucent alpha (dialed down for subtle, delicate cosmic dust)
                float alpha = MathUtils.clamp(cloudDensity * 0.48f, 0f, 1f);

                // Cloudy dark grey/brown nebula colors matching images/accretion_disc.png:
                // Warm smoky umber/sepia at denser centers, fading to cool smoky slate/charcoal at wispy edges
                float red = MathUtils.lerp(0.14f, 0.25f, cloudDensity);
                float green = MathUtils.lerp(0.12f, 0.19f, cloudDensity);
                float blue = MathUtils.lerp(0.11f, 0.15f, cloudDensity);

                map.setColor(red, green, blue, alpha);
                map.drawPixel(x, y);
            }
        }

        Texture texture = new Texture(map);
        map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    public void dispose() {
        atlasTexture.dispose();
        jetCloudTexture.dispose();
        accretionCloudTexture.dispose();
        foggyBulbTexture.dispose();
        nebulaCloudTexture.dispose();
    }
}
