package zoesim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Random;

/** A self-contained LibGDX simulation with locally generated animated sprite textures. */
public final class RpsArena extends ApplicationAdapter {
    private static final int MAX_PIECES = 360;
    private static final float PIECE_RADIUS = 17f;
    private final Random random = new Random();
    private final Array<Piece> pieces = new Array<>();
    private final Array<Impact> impacts = new Array<>();
    private final Array<CosmicParticle> cosmicParticles = new Array<>();
    private final Array<JetParticle> jetParticles = new Array<>();
    private final EnumMap<Kind, Integer> start = new EnumMap<>(Kind.class);
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private OrthographicCamera camera;
    private GlyphLayout layout;
    private Sound rockKnock, paperWrap, scissorsSnip;
    private Sound blackHoleAbsorb, blackHoleAmbience;
    private Texture rockSprite, paperSprite, blackHoleSprite;
    private Texture[] scissorsSprites;
    private float arenaX, arenaY, arenaRadius;
    private float speed = 1f;
    private boolean paused;
    private boolean draggingSpeed;
    private Kind heldStartKind;
    private int heldStartDelta;
    private float heldStartTime;
    private float heldStartRepeat;
    private Kind editingKind;
    private String editingValue = "";
    private boolean editingBlackHoleCount;
    private String blackHoleCountValue = "";
    private int heldBlackHoleDelta;
    private float heldBlackHoleTime;
    private float heldBlackHoleRepeat;
    private float elapsed;
    private int collisions;
    private int absorbedParticles;
    private int blackHoleStartCount = 180;
    private float blackHoleMass = BlackHoleSimulation.INITIAL_MASS;
    private float nextAbsorbSound;
    private float cosmicSpawnTimer;
    private float jetTimer;
    private long ambientLoopId = -1;
    private boolean fieldConsumed;
    private SimulationMode simulationMode = SimulationMode.RPS;
    private final SimulationMenu simulationMenu = new SimulationMenu();

    private enum SimulationMode { MENU, RPS, BLACK_HOLE }

    private enum Kind {
        ROCK("ROCK", new Color(0.05f, 0.82f, 1f, 1f), new Color(0.02f, 0.36f, 0.70f, 1f), "SMASH"),
        PAPER("PAPER", new Color(1f, 0.70f, 0.23f, 1f), new Color(0.84f, 0.29f, 0.08f, 1f), "WRAP"),
        SCISSORS("SCISSORS", new Color(0.95f, 0.44f, 1f, 1f), new Color(0.42f, 0.08f, 0.72f, 1f), "SNIP");
        final String label;
        final Color bright;
        final Color dark;
        final String verb;
        Kind(String label, Color bright, Color dark, String verb) { this.label = label; this.bright = bright; this.dark = dark; this.verb = verb; }
        boolean beats(Kind other) {
            return RpsSimulation.beats(ordinal(), other.ordinal());
        }
    }

    private static final class Piece {
        final Kind kind;
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        float phase;
        float safeFor;
        float cruiseSpeed;
        float currentSpeed;
        float boost;
        Piece(Kind kind, float x, float y, float vx, float vy, float phase) {
            this.kind = kind; position.set(x, y); velocity.set(vx, vy);
            cruiseSpeed = velocity.len(); currentSpeed = cruiseSpeed; velocity.nor(); this.phase = phase;
        }
    }

    private static final class Impact {
        final Kind kind;
        final Vector2 position = new Vector2();
        final float[] angles = new float[34];
        final float[] launchSpeeds = new float[34];
        final float[] gravities = new float[34];
        final float[] flickers = new float[34];
        float age;
        Impact(Kind kind, float x, float y, Random random) {
            this.kind = kind; position.set(x, y);
            for (int i = 0; i < angles.length; i++) {
                angles[i] = random.nextFloat() * MathUtils.PI2;
                launchSpeeds[i] = 95f + random.nextFloat() * 94f;
                gravities[i] = 145f + random.nextFloat() * 90f;
                flickers[i] = random.nextFloat() * MathUtils.PI2;
            }
        }
    }

    private static final class CosmicParticle {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        final float hue;
        final float size;
        final float mass;
        final int bodyType;
        final Color color;
        final float spin;
        float age, wobble;
        CosmicParticle(float x, float y, float vx, float vy, float hue, float size, int bodyType, Color color, float spin) {
            position.set(x, y); velocity.set(vx, vy); this.hue = hue; this.size = size; mass = BlackHoleSimulation.particleMass(size);
            this.bodyType = bodyType; this.color = color; this.spin = spin;
        }
    }

    private static final class JetParticle {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        final float size;
        float age;
        JetParticle(float x, float y, float vx, float vy, float size) { position.set(x, y); velocity.set(vx, vy); this.size = size; }
    }

    @Override public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.45f);
        layout = new GlyphLayout();
        camera = new OrthographicCamera();
        rockSprite = createRockSprite();
        paperSprite = createPaperSprite();
        blackHoleSprite = createBlackHoleSprite();
        scissorsSprites = new Texture[] { createScissorsSprite(18), createScissorsSprite(32), createScissorsSprite(46) };
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyTyped(char character) {
                if (editingBlackHoleCount) {
                    if (character >= '0' && character <= '9' && blackHoleCountValue.length() < 3) blackHoleCountValue += character;
                    else if (character == '\b' && !blackHoleCountValue.isEmpty()) blackHoleCountValue = blackHoleCountValue.substring(0, blackHoleCountValue.length() - 1);
                    else if (character == '\r' || character == '\n') commitBlackHoleCountEdit();
                    return true;
                }
                if (editingKind == null) return false;
                if (character >= '0' && character <= '9' && editingValue.length() < 3) editingValue += character;
                else if (character == '\b' && !editingValue.isEmpty()) editingValue = editingValue.substring(0, editingValue.length() - 1);
                else if (character == '\r' || character == '\n') commitEdit();
                return true;
            }
            @Override public boolean keyDown(int keycode) {
                if (editingBlackHoleCount) {
                    if (keycode == Input.Keys.ESCAPE) { editingBlackHoleCount = false; blackHoleCountValue = ""; return true; }
                    if (keycode == Input.Keys.ENTER) { commitBlackHoleCountEdit(); return true; }
                    return false;
                }
                if (editingKind == null) return false;
                if (keycode == Input.Keys.ESCAPE) { editingKind = null; editingValue = ""; return true; }
                if (keycode == Input.Keys.ENTER) { commitEdit(); return true; }
                return false;
            }
        });
        for (Kind kind : Kind.values()) start.put(kind, 22);
        rockKnock = Gdx.audio.newSound(Gdx.files.internal("sounds/rock-knock.wav"));
        paperWrap = Gdx.audio.newSound(Gdx.files.internal("sounds/paper-wrap.wav"));
        scissorsSnip = Gdx.audio.newSound(Gdx.files.internal("sounds/scissors-snip.wav"));
        blackHoleAbsorb = Gdx.audio.newSound(Gdx.files.internal("sounds/black-hole-absorb.ogg"));
        blackHoleAmbience = Gdx.audio.newSound(Gdx.files.internal("sounds/space-ambience.ogg"));
        resizeWorld();
        simulationMode = SimulationMode.MENU;
    }

    @Override public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 25f);
        elapsed += delta;
        resizeWorld();
        drawBackground();
        if (simulationMode == SimulationMode.MENU) {
            SimulationMenu.Choice choice = simulationMenu.render(shapes, batch, font, camera);
            if (choice == SimulationMenu.Choice.RPS) { simulationMode = SimulationMode.RPS; paused = false; reset(); }
            else if (choice == SimulationMenu.Choice.BLACK_HOLE) { simulationMode = SimulationMode.BLACK_HOLE; resetBlackHole(); }
            return;
        }
        if (!paused) {
            if (simulationMode == SimulationMode.RPS) update(delta);
            else updateBlackHole(delta);
        }
        drawArena();
        drawInterface();
        handleInput();
    }

    private void resizeWorld() {
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.update();
        float panelWidth = MathUtils.clamp(Gdx.graphics.getWidth() * .27f, 280f, 390f);
        float left = 42f;
        float usable = Gdx.graphics.getWidth() - panelWidth - left - 60f;
        arenaRadius = Math.min(usable * .5f, Gdx.graphics.getHeight() * .40f);
        arenaX = left + usable * .5f;
        arenaY = Gdx.graphics.getHeight() * .52f;
    }

    private void update(float dt) {
        for (Piece p : pieces) {
            p.safeFor -= dt;
            p.phase += dt * 3f;
            p.boost = Math.max(0f, p.boost - dt);
            float naturalSpeed = p.cruiseSpeed * (.92f + .11f * MathUtils.sin(p.phase * .63f));
            float desiredSpeed = naturalSpeed * (p.boost > 0 ? 1.31f : 1f);
            p.currentSpeed = MathUtils.lerp(p.currentSpeed, desiredSpeed, Math.min(1f, dt * (p.currentSpeed < desiredSpeed ? 2.9f : .85f)));
            p.position.mulAdd(p.velocity, dt * p.currentSpeed * speed);
            float dx = p.position.x - arenaX, dy = p.position.y - arenaY;
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            if (dist > arenaRadius - PIECE_RADIUS) {
                float nx = dx / dist, ny = dy / dist;
                p.position.set(arenaX + nx * (arenaRadius - PIECE_RADIUS), arenaY + ny * (arenaRadius - PIECE_RADIUS));
                float dot = p.velocity.x * nx + p.velocity.y * ny;
                p.velocity.mulAdd(new Vector2(nx, ny), -2f * dot).nor();
                p.currentSpeed *= .85f;
            }
        }
        for (int i = 0; i < pieces.size; i++) {
            Piece a = pieces.get(i);
            if (a.safeFor > 0) continue;
            for (int j = i + 1; j < pieces.size; j++) {
                Piece b = pieces.get(j);
                if (b.safeFor > 0 || a.kind == b.kind) continue;
                if (a.position.dst2(b.position) > PIECE_RADIUS * PIECE_RADIUS * 3.2f) continue;
                if (a.kind.beats(b.kind)) { consume(i, j); break; }
                if (b.kind.beats(a.kind)) { consume(j, i); break; }
            }
        }
        for (Iterator<Impact> it = impacts.iterator(); it.hasNext();) {
            Impact impact = it.next();
            impact.age += dt;
            if (impact.age > .86f) it.remove();
        }
    }

    private void updateBlackHole(float dt) {
        if (fieldConsumed) return;
        nextAbsorbSound -= dt;
        cosmicSpawnTimer += dt;
        jetTimer += dt;
        float horizon = blackHoleHorizon();
        float gravitationalParameter = BlackHoleSimulation.gravitationalParameter(blackHoleMass);
        while (cosmicSpawnTimer > .32f && cosmicParticles.size < 320) { cosmicSpawnTimer -= .32f; spawnCosmicParticle(true); }
        if (jetTimer > .17f) { jetTimer = 0f; emitJets(horizon); }
        for (Iterator<CosmicParticle> it = cosmicParticles.iterator(); it.hasNext();) {
            CosmicParticle particle = it.next();
            float dx = arenaX - particle.position.x, dy = arenaY - particle.position.y;
            float distance2 = dx * dx + dy * dy;
            float distance = (float)Math.sqrt(distance2);
            if (distance < horizon) { absorb(particle); it.remove(); continue; }
            // Inverse-square gravity with a small softening radius for numerical stability.
            float gravity = gravitationalParameter / Math.max(distance2, 900f);
            particle.velocity.add(dx / distance * gravity * dt, dy / distance * gravity * dt);
            // Dust-like drag slowly sheds angular momentum, producing a natural accretion spiral.
            particle.velocity.scl(1f - dt * .018f);
            particle.velocity.limit(340f);
            particle.position.mulAdd(particle.velocity, dt * speed);
            particle.age += dt;
            particle.wobble += particle.spin * dt;
            dx = particle.position.x - arenaX;
            dy = particle.position.y - arenaY;
            distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance < horizon) { absorb(particle); it.remove(); continue; }
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
            jet.position.mulAdd(jet.velocity, dt * speed);
            jet.velocity.scl(.985f); jet.age += dt;
            if (jet.age > 1.35f || jet.position.dst2(arenaX, arenaY) > arenaRadius * arenaRadius) it.remove();
        }
        if (blackHoleHorizon() >= arenaRadius - 10f) { fieldConsumed = true; paused = true; stopBlackHoleAmbience(); }
    }

    private void consume(int hunterIndex, int preyIndex) {
        if (hunterIndex >= pieces.size || preyIndex >= pieces.size || pieces.size >= MAX_PIECES) return;
        Piece hunter = pieces.get(hunterIndex);
        Piece prey = pieces.get(preyIndex);
        Kind winner = hunter.kind;
        float x = (hunter.position.x + prey.position.x) * .5f;
        float y = (hunter.position.y + prey.position.y) * .5f;
        Vector2 base = hunter.velocity.cpy().scl(80 + random.nextFloat() * 45);
        pieces.removeIndex(Math.max(hunterIndex, preyIndex));
        pieces.removeIndex(Math.min(hunterIndex, preyIndex));
        spawn(winner, x - base.y * .035f, y + base.x * .035f, base.x - base.y * .35f, base.y + base.x * .35f, .40f, .54f);
        spawn(winner, x + base.y * .035f, y - base.x * .035f, base.x + base.y * .35f, base.y - base.x * .35f, .40f, .54f);
        impacts.add(new Impact(winner, x, y, random));
        collisions++;
        playHit(winner);
    }

    private void reset() {
        pieces.clear(); impacts.clear(); collisions = 0;
        for (Kind kind : Kind.values()) for (int i = 0; i < start.get(kind); i++) spawnRandom(kind);
    }

    private void resetBlackHole() {
        cosmicParticles.clear(); jetParticles.clear(); absorbedParticles = 0; blackHoleMass = BlackHoleSimulation.INITIAL_MASS;
        nextAbsorbSound = 0f; cosmicSpawnTimer = 0f; jetTimer = 0f; fieldConsumed = false; paused = false;
        for (int i = 0; i < blackHoleStartCount; i++) spawnCosmicParticle(false);
        startBlackHoleAmbience();
    }

    private void spawnCosmicParticle(boolean atBoundary) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float horizon = blackHoleHorizon();
        float radius = atBoundary ? arenaRadius - 11f : horizon + 24f + (float)Math.sqrt(random.nextFloat()) * (arenaRadius - horizon - 35f);
        float orbital = (float)Math.sqrt(BlackHoleSimulation.gravitationalParameter(blackHoleMass) / radius) * (.53f + random.nextFloat() * .26f);
        float inward = -7f - random.nextFloat() * 16f;
        float x = arenaX + MathUtils.cos(angle) * radius, y = arenaY + MathUtils.sin(angle) * radius;
        float vx = MathUtils.cos(angle + MathUtils.HALF_PI) * orbital + MathUtils.cos(angle) * inward;
        float vy = MathUtils.sin(angle + MathUtils.HALF_PI) * orbital + MathUtils.sin(angle) * inward;
        float size = 1.2f + (float)Math.pow(random.nextFloat(), 2.2f) * 13f;
        int type = random.nextInt(5);
        Color[] palette = { new Color(.39f, .29f, .22f, 1f), new Color(.42f, .60f, .77f, 1f), new Color(.78f, .56f, .31f, 1f), new Color(.82f, .84f, .88f, 1f), new Color(.48f, .25f, .17f, 1f) };
        cosmicParticles.add(new CosmicParticle(x, y, vx, vy, random.nextFloat() * 360f, size, type, palette[type], 1.5f + random.nextFloat() * 4f));
    }

    private void emitJets(float horizon) {
        // Relativistic jets are modeled as periodically expelled plasma along the rotation poles.
        for (int pole = -1; pole <= 1; pole += 2) for (int i = 0; i < 3; i++) {
            float spread = (random.nextFloat() - .5f) * .20f;
            float angle = pole > 0 ? MathUtils.HALF_PI + spread : -MathUtils.HALF_PI + spread;
            float radius = horizon * .76f;
            float velocity = 155f + random.nextFloat() * 105f;
            jetParticles.add(new JetParticle(arenaX + MathUtils.cos(angle) * radius, arenaY + MathUtils.sin(angle) * radius,
                    MathUtils.cos(angle) * velocity, MathUtils.sin(angle) * velocity, 1.3f + random.nextFloat() * 2.3f));
        }
    }

    private float blackHoleHorizon() { return BlackHoleSimulation.eventHorizon(blackHoleMass); }

    private void absorb(CosmicParticle particle) {
        blackHoleMass += particle.mass;
        absorbedParticles++;
        if (nextAbsorbSound <= 0f) {
            blackHoleAbsorb.play(.22f, .38f + Math.min(.26f, particle.size * .04f), 0f);
            nextAbsorbSound = .12f;
        }
    }

    private void startBlackHoleAmbience() {
        if (ambientLoopId < 0) ambientLoopId = blackHoleAmbience.loop(.075f, .52f, 0f);
    }

    private void stopBlackHoleAmbience() {
        if (ambientLoopId >= 0) { blackHoleAmbience.stop(ambientLoopId); ambientLoopId = -1; }
    }

    private void spawnRandom(Kind kind) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float radius = (float)Math.sqrt(random.nextFloat()) * (arenaRadius > 10 ? arenaRadius - 25 : 240);
        float direction = random.nextFloat() * MathUtils.PI2;
        spawn(kind, arenaX + MathUtils.cos(angle) * radius, arenaY + MathUtils.sin(angle) * radius,
                MathUtils.cos(direction) * (45 + random.nextFloat() * 60), MathUtils.sin(direction) * (45 + random.nextFloat() * 60), .65f, 0f);
    }

    private void spawn(Kind kind, float x, float y, float vx, float vy, float safe, float boost) {
        Piece p = new Piece(kind, x, y, vx, vy, random.nextFloat() * MathUtils.PI2);
        p.safeFor = safe; p.boost = boost; pieces.add(p);
    }

    private void drawBackground() {
        Gdx.gl.glClearColor(.018f, .028f, .08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.025f, .05f, .13f, 1f); shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setColor(.06f, .13f, .24f, .20f); shapes.circle(arenaX, arenaY, arenaRadius + 80);
        shapes.setColor(.11f, .28f, .44f, .12f); shapes.circle(arenaX, arenaY, arenaRadius + 28);
        shapes.end();
    }

    private void drawArena() {
        if (simulationMode == SimulationMode.BLACK_HOLE) { drawBlackHoleArena(); return; }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.01f, .025f, .075f, 1f); shapes.circle(arenaX, arenaY, arenaRadius);
        for (Impact impact : impacts) drawImpact(impact);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(.45f, .92f, 1f, .75f); shapes.circle(arenaX, arenaY, arenaRadius);
        shapes.setColor(.16f, .49f, .85f, .3f); shapes.circle(arenaX, arenaY, arenaRadius - 7);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (Piece p : pieces) drawSprite(p);
        batch.end();
    }

    private void drawBlackHoleArena() {
        float horizon = blackHoleHorizon();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.006f, .01f, .04f, 1f); shapes.circle(arenaX, arenaY, arenaRadius);
        // Deep, nearly black gravitational shading outside the event horizon.
        for (int ring = 8; ring >= 0; ring--) {
            float radius = horizon + ring * 14f;
            float shade = .006f + ring * .006f;
            shapes.setColor(shade, .004f, shade * 2.2f, .34f);
            shapes.circle(arenaX, arenaY, radius);
        }
        for (CosmicParticle particle : cosmicParticles) drawCelestialBody(particle);
        for (JetParticle jet : jetParticles) {
            float fade = 1f - jet.age / 1.35f;
            shapes.setColor(.44f, .76f, 1f, fade * .75f); shapes.circle(jet.position.x, jet.position.y, jet.size);
            shapes.setColor(.94f, .98f, 1f, fade * .75f); shapes.circle(jet.position.x, jet.position.y, jet.size * .43f);
        }
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float size = horizon * 5.23f + MathUtils.sin(elapsed * 2f) * 5f;
        batch.draw(blackHoleSprite, arenaX - size * .5f, arenaY - size * .5f, size * .5f, size * .5f, size, size, 1f, 1f, -17f, 0, 0, blackHoleSprite.getWidth(), blackHoleSprite.getHeight(), false, false);
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(.23f, .17f, .42f, .55f); shapes.circle(arenaX, arenaY, horizon + 3f);
        shapes.setColor(.27f, .57f, 1f, .72f); shapes.circle(arenaX, arenaY, arenaRadius);
        shapes.end();
        if (fieldConsumed) {
            batch.begin(); font.setColor(.95f, .78f, 1f, 1f); font.getData().setScale(1.45f); centered("EVENT HORIZON FILLS THE FIELD", arenaX, arenaY - horizon - 24f); font.getData().setScale(1.45f); batch.end();
        }
    }

    private void drawCelestialBody(CosmicParticle particle) {
        float shimmer = .75f + .25f * MathUtils.sin(particle.age * 4f + particle.hue);
        Color color = particle.color;
        shapes.setColor(color.r * .35f, color.g * .35f, color.b * .35f, shimmer); shapes.circle(particle.position.x + particle.size * .22f, particle.position.y - particle.size * .18f, particle.size);
        shapes.setColor(color.r, color.g, color.b, shimmer); shapes.circle(particle.position.x, particle.position.y, particle.size * .88f);
        if (particle.bodyType == 1 || particle.bodyType == 3) {
            shapes.setColor(.9f, .95f, 1f, shimmer * .65f); shapes.circle(particle.position.x - particle.size * .28f, particle.position.y + particle.size * .27f, Math.max(.7f, particle.size * .25f));
        }
        if (particle.bodyType == 2 && particle.size > 3.6f) {
            shapes.setColor(.95f, .77f, .48f, shimmer * .55f); shapes.circle(particle.position.x - particle.size * .32f, particle.position.y + particle.size * .1f, particle.size * .24f);
        }
        if (particle.bodyType == 4 && particle.size > 5f) {
            shapes.setColor(.76f, .56f, .37f, shimmer * .38f);
            shapes.rectLine(particle.position.x - particle.size * 1.3f, particle.position.y, particle.position.x + particle.size * 1.3f, particle.position.y, Math.max(1f, particle.size * .16f));
        }
    }

    private void drawSprite(Piece p) {
        Texture sprite;
        float size;
        float rotation;
        float scale = 1f;
        switch (p.kind) {
            case ROCK:
                sprite = rockSprite; size = 42f; rotation = p.phase * 4f; scale += MathUtils.sin(p.phase * 1.2f) * .035f; break;
            case PAPER:
                sprite = paperSprite; size = 43f; rotation = -14f + MathUtils.sin(p.phase * 1.7f) * 13f; scale += MathUtils.sin(p.phase * 2f) * .06f; break;
            case SCISSORS:
                sprite = scissorsSprites[(int)((p.phase * 2.2f) % 3)]; size = 47f; rotation = p.velocity.angleDeg() - 92f; scale += MathUtils.sin(p.phase * 4.4f) * .07f; break;
            default: return;
        }
        batch.setColor(1f, 1f, 1f, p.safeFor > 0 ? .72f : 1f);
        batch.draw(sprite, p.position.x - size * .5f, p.position.y - size * .5f, size * .5f, size * .5f, size, size, scale, scale, rotation, 0, 0, sprite.getWidth(), sprite.getHeight(), false, false);
        batch.setColor(Color.WHITE);
    }

    private void drawImpact(Impact impact) {
        float progress = impact.age / .86f;
        float alpha = 1f - progress;
        for (int i = 0; i < impact.angles.length; i++) {
            float x = impact.position.x + MathUtils.cos(impact.angles[i]) * impact.launchSpeeds[i] * impact.age;
            float y = impact.position.y + MathUtils.sin(impact.angles[i]) * impact.launchSpeeds[i] * impact.age - .5f * impact.gravities[i] * impact.age * impact.age;
            float flicker = .58f + .42f * MathUtils.sin(impact.age * 31f + impact.flickers[i]);
            Color c = i % 3 == 0 ? Color.WHITE : impact.kind.bright;
            shapes.setColor(c.r, c.g, c.b, alpha * flicker * (i % 3 == 0 ? .95f : .72f));
            shapes.circle(x, y, i % 3 == 0 ? 2.7f : 1.65f);
            // A tiny, detached glitter mote makes the arc read as fireworks, without a streak line.
            if (i % 2 == 0 && progress < .8f) {
                shapes.setColor(Color.WHITE.r, Color.WHITE.g, Color.WHITE.b, alpha * flicker * .46f);
                shapes.circle(x + MathUtils.sin(impact.flickers[i] + impact.age * 17f) * 3.4f, y - 3.2f, .8f);
            }
        }
        if (progress < .14f) {
            shapes.setColor(Color.WHITE.r, Color.WHITE.g, Color.WHITE.b, (1f - progress / .14f) * .9f);
            shapes.circle(impact.position.x, impact.position.y, 7f + progress * 20f);
        }
    }

    private void drawInterface() {
        if (simulationMode == SimulationMode.BLACK_HOLE) { drawBlackHoleInterface(); return; }
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.035f, .08f, .17f, .92f); shapes.rect(panelX, 86, width, Gdx.graphics.getHeight() - 172);
        shapes.setColor(.12f, .42f, .67f, .72f); shapes.rect(panelX, Gdx.graphics.getHeight() - 132, width, 2);
        if (editingKind != null) {
            float editRow = Gdx.graphics.getHeight() - 361 - editingKind.ordinal() * 38f;
            shapes.setColor(.10f, .33f, .53f, 1f); shapes.rect(panelX + 84, editRow - 27, 49, 34);
        }
        shapes.end();
        batch.begin();
        font.setColor(.72f, .92f, 1f, 1f); font.getData().setScale(2.05f); text("RPS ARENA", panelX + 22, Gdx.graphics.getHeight() - 84);
        font.getData().setScale(1.02f); font.setColor(.5f, .68f, .84f, 1f); text("LAST TYPE STANDING WINS", panelX + 23, Gdx.graphics.getHeight() - 115);
        font.getData().setScale(1.45f);
        float row = Gdx.graphics.getHeight() - 177;
        for (Kind kind : Kind.values()) {
            drawCounterSprite(kind, panelX + 31, row - 8);
            font.setColor(Color.WHITE); text(kind.label, panelX + 55, row);
            font.setColor(Color.WHITE); text(String.valueOf(count(kind)), panelX + width - 47, row);
            row -= 47;
        }
        font.getData().setScale(1.0f); font.setColor(.52f, .7f, .86f, 1f); text("CONFIGURE STARTING COUNT", panelX + 22, row - 8);
        row -= 43;
        for (Kind kind : Kind.values()) {
            font.setColor(Color.WHITE); text(kind.label.substring(0, 1), panelX + 22, row);
            text("-", panelX + 61, row);
            text(editingKind == kind ? (editingValue.isEmpty() ? "|" : editingValue + "|") : String.valueOf(start.get(kind)), panelX + 93, row);
            text("+", panelX + 150, row);
            row -= 38;
        }
        font.getData().setScale(1.03f); font.setColor(.52f, .7f, .86f, 1f); text("MOTION SPEED  " + String.format("%.1fx", speed), panelX + 22, row - 2);
        batch.end();
        float sliderY = row - 24;
        shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.setColor(.09f, .23f, .39f, 1f); shapes.rect(panelX+23, sliderY, width-46, 9); shapes.setColor(.14f, .8f, 1f, 1f); shapes.rect(panelX+23, sliderY, (width-46) * ((speed-.35f)/2.15f), 9); shapes.circle(panelX+23+(width-46)*((speed-.35f)/2.15f), sliderY+4.5f, 13); shapes.end();
        batch.begin(); font.getData().setScale(1.12f); font.setColor(Color.WHITE); text(paused ? "▶  RESUME" : "Ⅱ  PAUSE", panelX+24, sliderY-37); text("↻  RESET", panelX+153, sliderY-37);
        font.setColor(.82f, .62f, 1f, 1f); font.getData().setScale(1.02f); text("SIMULATION MENU", panelX+24, 157);
        font.setColor(.45f, .7f, .9f, 1f); font.getData().setScale(.93f); text("PIECES " + pieces.size + "   •   CLASHES " + collisions, panelX+24, 114);
        batch.end();
    }

    private void drawBlackHoleInterface() {
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float sliderY = Gdx.graphics.getHeight() - 405f;
        float countRow = Gdx.graphics.getHeight() - 332f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.06f, .025f, .14f, .95f); shapes.rect(panelX, 86, width, Gdx.graphics.getHeight() - 172);
        shapes.setColor(.61f, .24f, .95f, .82f); shapes.rect(panelX, Gdx.graphics.getHeight() - 132, width, 2);
        shapes.setColor(.15f, .07f, .29f, 1f); shapes.rect(panelX + 22, Gdx.graphics.getHeight() - 355, width - 44, 168);
        if (editingBlackHoleCount) { shapes.setColor(.29f, .12f, .48f, 1f); shapes.rect(panelX + 92, countRow - 27, 55, 34); }
        shapes.setColor(.16f, .11f, .35f, 1f); shapes.rect(panelX+23, sliderY, width-46, 9);
        shapes.setColor(.74f, .35f, 1f, 1f); shapes.rect(panelX+23, sliderY, (width-46) * ((speed-.35f)/2.15f), 9);
        shapes.circle(panelX+23+(width-46)*((speed-.35f)/2.15f), sliderY+4.5f, 13);
        shapes.end();
        batch.begin();
        font.setColor(.88f, .72f, 1f, 1f); font.getData().setScale(1.92f); text("BLACK HOLE", panelX + 22, Gdx.graphics.getHeight() - 84);
        font.setColor(.62f, .48f, .86f, 1f); font.getData().setScale(1.0f); text("RAINBOW PARTICLE ACCRETION", panelX + 23, Gdx.graphics.getHeight() - 115);
        font.setColor(Color.WHITE); font.getData().setScale(1.16f); text("LIVE PARTICLES", panelX + 42, Gdx.graphics.getHeight() - 220);
        font.setColor(.94f, .78f, 1f, 1f); font.getData().setScale(2.2f); text(String.valueOf(cosmicParticles.size), panelX + 42, Gdx.graphics.getHeight() - 258);
        font.setColor(.56f, .42f, .76f, 1f); font.getData().setScale(.94f); text("ABSORBED  " + absorbedParticles, panelX + 150, Gdx.graphics.getHeight() - 237);
        font.setColor(.56f, .42f, .76f, 1f); font.getData().setScale(.82f); text("MASS  " + (int)blackHoleMass, panelX + 150, Gdx.graphics.getHeight() - 258);
        font.setColor(.7f, .62f, .92f, 1f); font.getData().setScale(.94f); text("PARTICLE SEED", panelX + 42, Gdx.graphics.getHeight() - 298);
        font.setColor(Color.WHITE); font.getData().setScale(1.1f); text("-", panelX + 62, countRow);
        text(editingBlackHoleCount ? (blackHoleCountValue.isEmpty() ? "|" : blackHoleCountValue + "|") : String.valueOf(blackHoleStartCount), panelX + 101, countRow);
        text("+", panelX + 164, countRow);
        font.setColor(.7f, .62f, .92f, 1f); font.getData().setScale(1.03f); text("TIME SCALE  " + String.format("%.1fx", speed), panelX + 22, sliderY + 35);
        font.setColor(Color.WHITE); font.getData().setScale(1.08f); text(paused ? "▶  RESUME" : "Ⅱ  PAUSE", panelX+24, sliderY-38); text("↻  NEW CLOUD", panelX+153, sliderY-38);
        font.setColor(.56f, .78f, 1f, 1f); font.getData().setScale(1.02f); text("SIMULATION MENU", panelX + 24, 157);
        font.setColor(.48f, .59f, .84f, 1f); font.getData().setScale(.86f); text("SPIRAL IN. CROSS THE EVENT HORIZON.", panelX + 24, 114);
        batch.end();
    }

    private void handleInput() {
        float x = Gdx.input.getX(), y = Gdx.graphics.getHeight() - Gdx.input.getY();
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        if (simulationMode == SimulationMode.BLACK_HOLE) { handleBlackHoleInput(x, y, panelX, width); return; }
        float row = Gdx.graphics.getHeight() - 361;
        float sliderY = row - 3 * 38 - 24;
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .1f);
        if (!Gdx.input.isTouched()) { draggingSpeed = false; heldStartKind = null; return; }
        if (draggingSpeed) { setSpeedFromX(x, panelX, width); return; }
        if (heldStartKind != null) {
            heldStartTime += dt;
            if (heldStartTime > .34f) {
                heldStartRepeat += dt;
                while (heldStartRepeat >= .065f) { adjustStart(heldStartKind, heldStartDelta); heldStartRepeat -= .065f; }
            }
            return;
        }
        if (!Gdx.input.justTouched()) return;
        if (x > panelX + 12 && x < panelX + width - 12 && y > 132 && y < 178) { openMenu(); return; }
        if (x > panelX+15 && x < panelX+width-15 && Math.abs(y-sliderY) < 24) {
            draggingSpeed = true;
            setSpeedFromX(x, panelX, width);
            return;
        }
        for (Kind kind : Kind.values()) {
            if (x > panelX + 45 && x < panelX + 87 && Math.abs(y-row) < 22) { startHold(kind, -1); return; }
            if (x > panelX + 130 && x < panelX + 180 && Math.abs(y-row) < 22) { startHold(kind, 1); return; }
            if (x > panelX + 84 && x < panelX + 138 && Math.abs(y-row) < 22) { beginEdit(kind); return; }
            row -= 38;
        }
        if (y > sliderY-62 && y < sliderY-15) { if (x < panelX+145) paused = !paused; else if (x < panelX+285) reset(); }
    }

    private void handleBlackHoleInput(float x, float y, float panelX, float width) {
        float sliderY = Gdx.graphics.getHeight() - 405f;
        float countRow = Gdx.graphics.getHeight() - 332f;
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .1f);
        if (!Gdx.input.isTouched()) { draggingSpeed = false; heldBlackHoleDelta = 0; return; }
        if (draggingSpeed) { setSpeedFromX(x, panelX, width); return; }
        if (heldBlackHoleDelta != 0) {
            heldBlackHoleTime += dt;
            if (heldBlackHoleTime > .34f) {
                heldBlackHoleRepeat += dt;
                while (heldBlackHoleRepeat >= .065f) { adjustBlackHoleCount(heldBlackHoleDelta); heldBlackHoleRepeat -= .065f; }
            }
            return;
        }
        if (!Gdx.input.justTouched()) return;
        if (x > panelX + 12 && x < panelX + width - 12 && y > 132 && y < 178) { openMenu(); return; }
        if (x > panelX + 45 && x < panelX + 92 && Math.abs(y - countRow) < 22) { startBlackHoleCountHold(-1); return; }
        if (x > panelX + 151 && x < panelX + 200 && Math.abs(y - countRow) < 22) { startBlackHoleCountHold(1); return; }
        if (x > panelX + 90 && x < panelX + 151 && Math.abs(y - countRow) < 22) { editingBlackHoleCount = true; blackHoleCountValue = ""; return; }
        if (x > panelX+15 && x < panelX+width-15 && Math.abs(y-sliderY) < 24) { draggingSpeed = true; setSpeedFromX(x, panelX, width); return; }
        if (y > sliderY-66 && y < sliderY-15) { if (x < panelX+145) paused = !paused; else if (x < panelX+310) resetBlackHole(); }
    }

    private void openMenu() {
        draggingSpeed = false; heldStartKind = null; heldBlackHoleDelta = 0; editingKind = null; editingValue = ""; editingBlackHoleCount = false; blackHoleCountValue = ""; paused = false;
        stopBlackHoleAmbience();
        simulationMode = SimulationMode.MENU;
    }

    private void startHold(Kind kind, int delta) {
        if (editingKind != null) commitEdit();
        adjustStart(kind, delta);
        heldStartKind = kind; heldStartDelta = delta; heldStartTime = 0f; heldStartRepeat = 0f;
    }

    private void adjustStart(Kind kind, int delta) { start.put(kind, MathUtils.clamp(start.get(kind) + delta, 0, 100)); }

    private void beginEdit(Kind kind) {
        editingKind = kind;
        editingValue = "";
    }

    private void commitEdit() {
        if (editingKind == null) return;
        try { start.put(editingKind, MathUtils.clamp(Integer.parseInt(editingValue), 0, 100)); }
        catch (NumberFormatException ignored) { }
        editingKind = null;
        editingValue = "";
    }

    private void startBlackHoleCountHold(int delta) {
        if (editingBlackHoleCount) commitBlackHoleCountEdit();
        adjustBlackHoleCount(delta);
        heldBlackHoleDelta = delta; heldBlackHoleTime = 0f; heldBlackHoleRepeat = 0f;
    }

    private void adjustBlackHoleCount(int delta) {
        blackHoleStartCount = MathUtils.clamp(blackHoleStartCount + delta, 10, 360);
    }

    private void commitBlackHoleCountEdit() {
        try { blackHoleStartCount = MathUtils.clamp(Integer.parseInt(blackHoleCountValue), 10, 360); }
        catch (NumberFormatException ignored) { }
        editingBlackHoleCount = false;
        blackHoleCountValue = "";
    }

    private void setSpeedFromX(float x, float panelX, float width) {
        speed = MathUtils.clamp(.35f + (x-(panelX+23))/(width-46)*2.15f, .35f, 2.5f);
    }

    private void drawCounterSprite(Kind kind, float x, float y) {
        Texture texture = kind == Kind.ROCK ? rockSprite : kind == Kind.PAPER ? paperSprite : scissorsSprites[(int)(elapsed * 4f) % scissorsSprites.length];
        float size = kind == Kind.SCISSORS ? 31f : 27f;
        batch.draw(texture, x - size * .5f, y - size * .5f, size, size);
    }

    private void playHit(Kind winner) {
        Sound sound = winner == Kind.ROCK ? rockKnock : winner == Kind.PAPER ? paperWrap : scissorsSnip;
        sound.play(.42f, .91f + random.nextFloat() * .16f, 0f);
    }

    /** A soft, mostly black event horizon with a narrow warped accretion ring. */
    private Texture createBlackHoleSprite() {
        final int size = 256;
        Pixmap map = transparentPixmap(size, size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float dx = x - size * .5f, dy = y - size * .5f;
            float radius = (float)Math.sqrt(dx * dx + dy * dy);
            if (radius > 124f) continue;
            float diskRadius = (float)Math.sqrt(dx * dx + (dy * 2.05f) * (dy * 2.05f));
            float ring = (float)Math.exp(-Math.pow((diskRadius - 73f) / 10f, 2));
            float outerGlow = (float)Math.exp(-Math.pow((radius - 93f) / 32f, 2));
            float angleLight = .36f + .64f * Math.max(0f, -dx / Math.max(radius, 1f));
            if (radius < 49f) {
                map.setColor(.001f, .001f, .004f, 1f);
            } else {
                float red = .006f + ring * (.40f + .53f * angleLight) + outerGlow * .028f;
                float green = .003f + ring * (.08f + .55f * angleLight);
                float blue = .018f + ring * (.04f + .49f * angleLight) + outerGlow * .12f;
                float alpha = MathUtils.clamp(ring * .96f + outerGlow * .26f, 0f, .98f);
                map.setColor(red, green, blue, alpha);
            }
            map.drawPixel(x, y);
        }
        return texture(map);
    }

    private int count(Kind kind) { int total = 0; for (Piece p : pieces) if (p.kind == kind) total++; return total; }
    private void text(String text, float x, float y) { font.draw(batch, text, x, y); }
    private void centered(String text, float x, float y) { layout.setText(font, text); font.draw(batch, text, x-layout.width*.5f, y); }

    private Texture createRockSprite() {
        Pixmap map = transparentPixmap(112, 112);
        for (int y = 3; y < 109; y++) for (int x = 3; x < 109; x++) {
            float dx = x - 56f, dy = y - 55f;
            float a = MathUtils.atan2(dy, dx);
            float edge = 42f + MathUtils.sin(a * 5f + .7f) * 4f + MathUtils.sin(a * 9f) * 2.4f;
            float distance = (float)Math.sqrt(dx*dx + dy*dy);
            if (distance > edge) continue;
            float grain = MathUtils.sin(x * 1.71f + y * 2.37f) * 10f + MathUtils.sin(x*.31f-y*.28f)*9f;
            float light = (-dx - dy) * .36f;
            float gray = MathUtils.clamp(89 + grain + light, 44, 157) / 255f;
            map.setColor(gray * .92f, gray * .98f, gray, distance > edge - 2f ? .85f : 1f);
            map.drawPixel(x, y);
        }
        for (int i = 0; i < 19; i++) { map.setColor(.10f, .12f, .14f, .20f); map.fillCircle(30 + (i*31)%47, 29 + (i*17)%43, 1 + i%3); }
        return texture(map);
    }

    private Texture createPaperSprite() {
        Pixmap map = transparentPixmap(112, 112);
        map.setColor(0f, 0f, 0f, .18f); map.fillRectangle(23, 15, 63, 79);
        map.setColor(.99f, .93f, .72f, 1f); map.fillRectangle(18, 20, 65, 79);
        map.setColor(.95f, .82f, .48f, 1f); map.fillTriangle(83, 99, 83, 80, 64, 99);
        map.setColor(1f, .98f, .84f, 1f); map.fillTriangle(82, 98, 82, 82, 66, 98);
        map.setColor(.74f, .61f, .37f, .36f);
        for (int y = 35; y < 83; y += 11) map.drawLine(29, y, 72 - (y % 3), y);
        return texture(map);
    }

    private Texture createScissorsSprite(float opening) {
        Pixmap map = transparentPixmap(128, 128);
        float pivotX = 64, pivotY = 57;
        map.setColor(.91f, .95f, .98f, 1f);
        blade(map, pivotX, pivotY, 64 - opening, 111);
        blade(map, pivotX, pivotY, 64 + opening, 111);
        map.setColor(.14f, .68f, .83f, 1f); map.fillCircle(45, 34, 16); map.fillCircle(83, 34, 16);
        map.setColor(.03f, .12f, .18f, 1f); map.fillCircle(45, 34, 8); map.fillCircle(83, 34, 8);
        map.setColor(.78f, .84f, .88f, 1f); map.fillCircle(64, 57, 7);
        return texture(map);
    }

    private void blade(Pixmap map, float x1, float y1, float x2, float y2) {
        map.setColor(.46f, .55f, .61f, 1f);
        for (int offset = -3; offset <= 3; offset++) map.drawLine((int)x1 + offset, (int)y1, (int)x2 + offset, (int)y2);
        map.setColor(.94f, .98f, 1f, 1f);
        for (int offset = -1; offset <= 1; offset++) map.drawLine((int)x1 + offset, (int)y1, (int)x2 + offset, (int)y2);
    }

    private Pixmap transparentPixmap(int width, int height) {
        Pixmap map = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        map.setBlending(Pixmap.Blending.None); map.setColor(0f, 0f, 0f, 0f); map.fill();
        map.setBlending(Pixmap.Blending.SourceOver);
        return map;
    }

    private Texture texture(Pixmap map) {
        Texture texture = new Texture(map); map.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    @Override public void dispose() {
        shapes.dispose(); batch.dispose(); font.dispose();
        stopBlackHoleAmbience();
        rockKnock.dispose(); paperWrap.dispose(); scissorsSnip.dispose(); blackHoleAbsorb.dispose(); blackHoleAmbience.dispose();
        rockSprite.dispose(); paperSprite.dispose(); blackHoleSprite.dispose(); for (Texture texture : scissorsSprites) texture.dispose();
    }
}
