package zoesim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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

/** Complete Rock/Paper/Scissors simulation: rules, state, input, rendering, and assets. */
public final class RpsSimulation implements ArenaSimulation {
    static final int MAX_PIECES = 360;
    private static final float PIECE_RADIUS = 17f;

    enum Kind {
        ROCK("ROCK", new Color(.05f, .82f, 1f, 1f)),
        PAPER("PAPER", new Color(1f, .70f, .23f, 1f)),
        SCISSORS("SCISSORS", new Color(.95f, .44f, 1f, 1f));

        final String label;
        final Color bright;
        Kind(String label, Color bright) { this.label = label; this.bright = bright; }
        boolean beats(Kind other) { return RpsSimulation.beats(ordinal(), other.ordinal()); }
    }

    private static final class Piece {
        final Kind kind;
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        float phase, safeFor, cruiseSpeed, currentSpeed, boost;
        Piece(Kind kind, float x, float y, float vx, float vy, float phase) {
            this.kind = kind;
            position.set(x, y);
            velocity.set(vx, vy);
            cruiseSpeed = velocity.len();
            currentSpeed = cruiseSpeed;
            velocity.nor();
            this.phase = phase;
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
            this.kind = kind;
            position.set(x, y);
            for (int i = 0; i < angles.length; i++) {
                angles[i] = random.nextFloat() * MathUtils.PI2;
                launchSpeeds[i] = 95f + random.nextFloat() * 94f;
                gravities[i] = 145f + random.nextFloat() * 90f;
                flickers[i] = random.nextFloat() * MathUtils.PI2;
            }
        }
    }

    static final class VictoryConfetti {
        float x, y;
        float vx, vy;
        final float width, height;
        float rotation, rotSpeed;
        float flutterPhase;
        final float flutterSpeed;
        final Color color;
        float age;
        final float maxAge;

        VictoryConfetti(float x, float y, float vx, float vy, float width, float height,
                        float rotation, float rotSpeed, float flutterPhase, float flutterSpeed,
                        Color color, float maxAge) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.rotSpeed = rotSpeed;
            this.flutterPhase = flutterPhase;
            this.flutterSpeed = flutterSpeed;
            this.color = color;
            this.age = 0f;
            this.maxAge = maxAge;
        }
    }

    private final Random random = new Random();
    private final Array<Piece> pieces = new Array<>();
    private final Array<Impact> impacts = new Array<>();
    private final Array<VictoryConfetti> confetti = new Array<>();
    private final EnumMap<Kind, Integer> start = new EnumMap<>(Kind.class);
    private final Sound rockKnock;
    private final Sound paperWrap;
    private final Sound scissorsSnip;
    private final Sound victorySound;
    private final Texture rockSprite;
    private final Texture paperSprite;
    private final Texture[] scissorsSprites;
    private float arenaX, arenaY, arenaRadius, speed = 1f, elapsed;
    private boolean paused, draggingSpeed, menuRequested;
    private Kind heldStartKind, editingKind;
    private int heldStartDelta, collisions;
    private float heldStartTime, heldStartRepeat;
    private String editingValue = "";
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private Kind winnerKind;
    private float victoryTimer;
    private boolean battleActive;

    public RpsSimulation() {
        for (Kind kind : Kind.values()) start.put(kind, 22);
        rockKnock = Gdx.audio.newSound(Gdx.files.internal("sounds/rock-knock.wav"));
        paperWrap = Gdx.audio.newSound(Gdx.files.internal("sounds/paper-wrap.wav"));
        scissorsSnip = Gdx.audio.newSound(Gdx.files.internal("sounds/scissors-snip.wav"));
        Sound vs = null;
        try {
            if (Gdx.audio != null && Gdx.files != null) {
                vs = Gdx.audio.newSound(Gdx.files.internal("sounds/victory.wav"));
            }
        } catch (Exception ignored) { }
        victorySound = vs;
        rockSprite = createRockSprite();
        paperSprite = createPaperSprite();
        scissorsSprites = new Texture[] { createScissorsSprite(18), createScissorsSprite(32), createScissorsSprite(46) };
    }

    public static boolean beats(int attacker, int defender) {
        return (attacker == 0 && defender == 2)
                || (attacker == 1 && defender == 0)
                || (attacker == 2 && defender == 1);
    }

    static int clampStartingCount(int value) { return MathUtils.clamp(value, 0, 100); }
    static float clampSpeed(float value) { return MathUtils.clamp(value, .35f, 2.5f); }

    @Override public void enter(float arenaX, float arenaY, float arenaRadius) {
        resize(arenaX, arenaY, arenaRadius);
        paused = false;
        menuRequested = false;
        reset();
    }

    @Override public void resize(float arenaX, float arenaY, float arenaRadius) {
        this.arenaX = arenaX;
        this.arenaY = arenaY;
        this.arenaRadius = arenaRadius;
    }

    @Override public void update(float dt) {
        if (paused) return;
        for (Piece p : pieces) {
            p.safeFor -= dt;
            p.phase += dt * 3f;
            p.boost = Math.max(0f, p.boost - dt);
            float naturalSpeed = p.cruiseSpeed * (.92f + .11f * MathUtils.sin(p.phase * .63f));
            float desiredSpeed = naturalSpeed * (p.boost > 0 ? 1.31f : 1f);
            p.currentSpeed = MathUtils.lerp(p.currentSpeed, desiredSpeed,
                    Math.min(1f, dt * (p.currentSpeed < desiredSpeed ? 2.9f : .85f)));
            p.position.mulAdd(p.velocity, dt * p.currentSpeed * speed);
            float dx = p.position.x - arenaX, dy = p.position.y - arenaY;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance > arenaRadius - PIECE_RADIUS) {
                float nx = dx / distance, ny = dy / distance;
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

        if (battleActive && winnerKind == null) {
            Kind survivor = null;
            int distinct = 0;
            for (int i = 0; i < pieces.size; i++) {
                Kind k = pieces.get(i).kind;
                if (survivor == null) {
                    survivor = k;
                    distinct = 1;
                } else if (survivor != k) {
                    distinct = 2;
                    break;
                }
            }
            if (distinct == 1 && survivor != null) {
                winnerKind = survivor;
                battleActive = false;
                victoryTimer = 0f;
                spawnVictoryBurst(winnerKind);
                playVictory();
            }
        }

        if (winnerKind != null) {
            victoryTimer += dt;
            if (victoryTimer > 0.4f && confetti.size < 180) {
                if (random.nextFloat() < dt * 10f) {
                    spawnTrickleConfetti(winnerKind);
                }
            }
        }

        for (Iterator<VictoryConfetti> it = confetti.iterator(); it.hasNext();) {
            VictoryConfetti c = it.next();
            c.age += dt;
            if (c.age >= c.maxAge) {
                it.remove();
                continue;
            }
            c.vy -= 140f * dt;
            c.vx *= (float)Math.pow(0.96, dt * 60f);
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            c.rotation += c.rotSpeed * dt;
            c.flutterPhase += c.flutterSpeed * dt;
        }
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
        spawn(winner, x - base.y * .035f, y + base.x * .035f,
                base.x - base.y * .35f, base.y + base.x * .35f, .40f, .54f);
        spawn(winner, x + base.y * .035f, y - base.x * .035f,
                base.x + base.y * .35f, base.y - base.x * .35f, .40f, .54f);
        impacts.add(new Impact(winner, x, y, random));
        collisions++;
        playHit(winner);
    }

    private void reset() {
        pieces.clear();
        impacts.clear();
        confetti.clear();
        collisions = 0;
        winnerKind = null;
        victoryTimer = 0f;
        int activeKinds = 0;
        for (Kind kind : Kind.values()) {
            int count = start.get(kind);
            if (count > 0) activeKinds++;
            for (int i = 0; i < count; i++) spawnRandom(kind);
        }
        battleActive = (activeKinds >= 2);
    }

    private void spawnRandom(Kind kind) {
        float angle = random.nextFloat() * MathUtils.PI2;
        float radius = (float)Math.sqrt(random.nextFloat()) * (arenaRadius > 10 ? arenaRadius - 25 : 240);
        float direction = random.nextFloat() * MathUtils.PI2;
        spawn(kind, arenaX + MathUtils.cos(angle) * radius, arenaY + MathUtils.sin(angle) * radius,
                MathUtils.cos(direction) * (45 + random.nextFloat() * 60),
                MathUtils.sin(direction) * (45 + random.nextFloat() * 60), .65f, 0f);
    }

    private void spawn(Kind kind, float x, float y, float vx, float vy, float safe, float boost) {
        Piece piece = new Piece(kind, x, y, vx, vy, random.nextFloat() * MathUtils.PI2);
        piece.safeFor = safe; piece.boost = boost; pieces.add(piece);
    }

    @Override public void render(float elapsed, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font, GlyphLayout layout) {
        this.elapsed = elapsed;
        drawBackground(shapes);
        drawArena(shapes, batch);
        drawConfetti(shapes);
        drawVictoryBanner(shapes, batch, font);
        drawInterface(shapes, batch, font);
    }

    private void drawBackground(ShapeRenderer shapes) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.025f, .05f, .13f, 1f); shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.setColor(.06f, .13f, .24f, .20f); shapes.circle(arenaX, arenaY, arenaRadius + 80);
        shapes.setColor(.11f, .28f, .44f, .12f); shapes.circle(arenaX, arenaY, arenaRadius + 28);
        shapes.setColor(.01f, .025f, .075f, 1f); shapes.circle(arenaX, arenaY, arenaRadius);
        for (Impact impact : impacts) drawImpact(impact, shapes);
        shapes.end();
    }

    private void drawArena(ShapeRenderer shapes, SpriteBatch batch) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(.45f, .92f, 1f, .75f); shapes.circle(arenaX, arenaY, arenaRadius);
        shapes.setColor(.16f, .49f, .85f, .3f); shapes.circle(arenaX, arenaY, arenaRadius - 7);
        shapes.end();
        batch.begin();
        for (Piece piece : pieces) drawSprite(piece, batch);
        batch.end();
    }

    private void drawSprite(Piece piece, SpriteBatch batch) {
        Texture sprite;
        float size, rotation, scale = 1f;
        switch (piece.kind) {
            case ROCK:
                sprite = rockSprite; size = 42f; rotation = piece.phase * 4f;
                scale += MathUtils.sin(piece.phase * 1.2f) * .035f; break;
            case PAPER:
                sprite = paperSprite; size = 43f; rotation = -14f + MathUtils.sin(piece.phase * 1.7f) * 13f;
                scale += MathUtils.sin(piece.phase * 2f) * .06f; break;
            case SCISSORS:
                sprite = scissorsSprites[(int)((piece.phase * 2.2f) % 3)]; size = 47f;
                rotation = piece.velocity.angleDeg() - 92f; scale += MathUtils.sin(piece.phase * 4.4f) * .07f; break;
            default: return;
        }
        batch.setColor(1f, 1f, 1f, piece.safeFor > 0 ? .72f : 1f);
        batch.draw(sprite, piece.position.x - size * .5f, piece.position.y - size * .5f,
                size * .5f, size * .5f, size, size, scale, scale, rotation,
                0, 0, sprite.getWidth(), sprite.getHeight(), false, false);
        batch.setColor(Color.WHITE);
    }

    private void drawImpact(Impact impact, ShapeRenderer shapes) {
        float progress = impact.age / .86f;
        float alpha = 1f - progress;
        for (int i = 0; i < impact.angles.length; i++) {
            float x = impact.position.x + MathUtils.cos(impact.angles[i]) * impact.launchSpeeds[i] * impact.age;
            float y = impact.position.y + MathUtils.sin(impact.angles[i]) * impact.launchSpeeds[i] * impact.age
                    - .5f * impact.gravities[i] * impact.age * impact.age;
            float flicker = .58f + .42f * MathUtils.sin(impact.age * 31f + impact.flickers[i]);
            Color color = i % 3 == 0 ? Color.WHITE : impact.kind.bright;
            shapes.setColor(color.r, color.g, color.b, alpha * flicker * (i % 3 == 0 ? .95f : .72f));
            shapes.circle(x, y, i % 3 == 0 ? 2.7f : 1.65f);
            if (i % 2 == 0 && progress < .8f) {
                shapes.setColor(1f, 1f, 1f, alpha * flicker * .46f);
                shapes.circle(x + MathUtils.sin(impact.flickers[i] + impact.age * 17f) * 3.4f, y - 3.2f, .8f);
            }
        }
        if (progress < .14f) {
            shapes.setColor(1f, 1f, 1f, (1f - progress / .14f) * .9f);
            shapes.circle(impact.position.x, impact.position.y, 7f + progress * 20f);
        }
    }

    private void drawInterface(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float H = Gdx.graphics.getHeight();

        float panelBottom = 68f;
        float panelTop = H - 48f;
        float headerY = panelTop - 18f;
        float subtitleY = panelTop - 58f;
        float separatorY = panelTop - 82f;

        float startCountBaseY = panelTop - 262f;
        float startCountRowSpacing = 36f;
        float btnW = 34f;
        float btnH = 28f;
        float btnMinusX = panelX + 52f;
        float boxX = panelX + 92f;
        float boxW = 68f;
        float btnPlusX = panelX + 166f;

        float speedLabelY = startCountBaseY - 2 * startCountRowSpacing - 38f;
        float sliderY = speedLabelY - 24f;
        float buttonY = sliderY - 38f;
        float menuBtnY = panelBottom + 62f;
        float taglineY = menuBtnY - 32f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.035f, .08f, .17f, .92f); shapes.rect(panelX, panelBottom, width, panelTop - panelBottom);
        shapes.setColor(.12f, .42f, .67f, .72f); shapes.rect(panelX, separatorY, width, 2);

        // Render 3D tactile buttons and input boxes for starting counts
        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            float rowCenterY = startCountBaseY - k * startCountRowSpacing;
            float btnY = rowCenterY - 14f;

            // 1. Minus Button (3D beveled)
            shapes.setColor(.03f, .09f, .16f, 1f); shapes.rect(btnMinusX, btnY - 2f, btnW, btnH + 2f);
            shapes.setColor(.07f, .22f, .34f, 1f); shapes.rect(btnMinusX, btnY, btnW, btnH);
            shapes.setColor(1f, 1f, 1f, .18f); shapes.rect(btnMinusX, btnY + btnH - 2f, btnW, 2f);
            shapes.setColor(0f, 0f, 0f, .35f); shapes.rect(btnMinusX, btnY, btnW, 2f);

            // 2. Input Box (recessed inset with glowing active border)
            shapes.setColor(.02f, .05f, .09f, 1f); shapes.rect(boxX, btnY, boxW, btnH);
            shapes.setColor(0f, 0f, 0f, .45f); shapes.rect(boxX, btnY + btnH - 2f, boxW, 2f);
            if (editingKind == kind) shapes.setColor(.18f, .82f, 1f, .95f);
            else shapes.setColor(.09f, .26f, .38f, .75f);
            shapes.rect(boxX, btnY, boxW, 1f);
            shapes.rect(boxX, btnY + btnH - 1f, boxW, 1f);
            shapes.rect(boxX, btnY, 1f, btnH);
            shapes.rect(boxX + boxW - 1f, btnY, 1f, btnH);

            // 3. Plus Button (3D beveled)
            shapes.setColor(.03f, .09f, .16f, 1f); shapes.rect(btnPlusX, btnY - 2f, btnW, btnH + 2f);
            shapes.setColor(.07f, .22f, .34f, 1f); shapes.rect(btnPlusX, btnY, btnW, btnH);
            shapes.setColor(1f, 1f, 1f, .18f); shapes.rect(btnPlusX, btnY + btnH - 2f, btnW, 2f);
            shapes.setColor(0f, 0f, 0f, .35f); shapes.rect(btnPlusX, btnY, btnW, 2f);
        }

        // Motion speed slider track
        shapes.setColor(.09f, .23f, .39f, 1f); shapes.rect(panelX + 23, sliderY, width - 46, 8);
        shapes.setColor(.14f, .8f, 1f, 1f); shapes.rect(panelX + 23, sliderY, (width - 46) * ((speed - .35f) / 2.15f), 8);
        shapes.circle(panelX + 23 + (width - 46) * ((speed - .35f) / 2.15f), sliderY + 4f, 12);
        shapes.end();

        batch.begin();
        font.setColor(.72f, .92f, 1f, 1f); font.getData().setScale(1.35f); text(font, batch, "RPS ARENA", panelX + 22, headerY);
        font.getData().setScale(0.64f); font.setColor(.5f, .68f, .84f, 1f); text(font, batch, "LAST TYPE STANDING WINS", panelX + 23, subtitleY);
        font.getData().setScale(0.92f);
        float row = panelTop - 110f;
        for (Kind kind : Kind.values()) {
            drawCounterSprite(kind, panelX + 31, row - 6, batch);
            font.setColor(Color.WHITE); text(font, batch, kind.label, panelX + 58, row);
            text(font, batch, String.valueOf(count(kind)), panelX + width - 56, row);
            row -= 38;
        }
        font.getData().setScale(0.68f); font.setColor(.52f, .7f, .86f, 1f); text(font, batch, "CONFIGURE STARTING COUNT", panelX + 22, panelTop - 232f);

        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            float rowCenterY = startCountBaseY - k * startCountRowSpacing;
            float textY = rowCenterY + 7f;

            font.setColor(kind.bright);
            font.getData().setScale(0.85f);
            text(font, batch, kind.label.substring(0, 1), panelX + 26f, textY);

            font.setColor(Color.WHITE);
            font.getData().setScale(0.88f);
            centeredText(font, batch, "-", btnMinusX + btnW * 0.5f, textY);

            String val = editingKind == kind ? (editingValue.isEmpty() ? "|" : editingValue + "|") : String.valueOf(start.get(kind));
            font.setColor(editingKind == kind ? Color.CYAN : Color.WHITE);
            font.getData().setScale(0.82f);
            centeredText(font, batch, val, boxX + boxW * 0.5f, textY);

            font.setColor(Color.WHITE);
            font.getData().setScale(0.88f);
            centeredText(font, batch, "+", btnPlusX + btnW * 0.5f, textY);
        }

        font.getData().setScale(0.72f); font.setColor(.52f, .7f, .86f, 1f); text(font, batch, "MOTION SPEED  " + String.format("%.1fx", speed), panelX + 22, speedLabelY);

        font.getData().setScale(0.80f); font.setColor(Color.WHITE); text(font, batch, paused ? "▶  RESUME" : "Ⅱ  PAUSE", panelX + 24, buttonY); text(font, batch, "↻  RESET", panelX + Math.max(170f, width * 0.48f), buttonY);
        font.setColor(.82f, .62f, 1f, 1f); font.getData().setScale(0.80f); text(font, batch, "SIMULATION MENU", panelX + 24, menuBtnY);
        font.setColor(.45f, .7f, .9f, 1f); font.getData().setScale(0.60f); text(font, batch, "PIECES " + pieces.size + "   •   CLASHES " + collisions, panelX + 24, taglineY);
        batch.end();
    }

    @Override public void handleInput() {
        float x = Gdx.input.getX(), y = Gdx.graphics.getHeight() - Gdx.input.getY();
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float H = Gdx.graphics.getHeight();

        float panelBottom = 68f;
        float panelTop = H - 48f;
        float startCountBaseY = panelTop - 262f;
        float startCountRowSpacing = 36f;

        float speedLabelY = startCountBaseY - 2 * startCountRowSpacing - 38f;
        float sliderY = speedLabelY - 24f;
        float buttonY = sliderY - 38f;
        float menuBtnY = panelBottom + 62f;

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
        if (x > panelX + 12 && x < panelX + width - 12 && Math.abs(y - menuBtnY) < 22) { menuRequested = true; return; }
        if (x > panelX + 15 && x < panelX + width - 15 && Math.abs(y - sliderY) < 18) {
            draggingSpeed = true; setSpeedFromX(x, panelX, width); return;
        }
        for (Kind kind : Kind.values()) {
            int k = kind.ordinal();
            float rowCenterY = startCountBaseY - k * startCountRowSpacing;
            if (Math.abs(y - rowCenterY) < 18) {
                // Minus button hit zone:
                if (x >= panelX + 44 && x <= panelX + 88) { startHold(kind, -1); return; }
                // Input box hit zone:
                if (x > panelX + 88 && x < panelX + 162) { beginEdit(kind); return; }
                // Plus button hit zone:
                if (x >= panelX + 162 && x <= panelX + 210) { startHold(kind, 1); return; }
            }
        }
        if (Math.abs(y - buttonY) < 22) {
            if (x >= panelX + 15 && x < panelX + Math.max(160f, width * 0.46f)) paused = !paused;
            else if (x >= panelX + Math.max(160f, width * 0.46f) && x < panelX + width - 10) reset();
        }
    }

    @Override public boolean keyTyped(char character) {
        if (editingKind == null) return false;
        if (character >= '0' && character <= '9' && editingValue.length() < 3) editingValue += character;
        else if (character == '\b' && !editingValue.isEmpty()) editingValue = editingValue.substring(0, editingValue.length() - 1);
        else if (character == '\r' || character == '\n') commitEdit();
        return true;
    }

    @Override public boolean keyDown(int keycode) {
        if (editingKind == null) return false;
        if (keycode == Input.Keys.ESCAPE) { editingKind = null; editingValue = ""; return true; }
        if (keycode == Input.Keys.ENTER) { commitEdit(); return true; }
        return false;
    }

    private void startHold(Kind kind, int delta) {
        if (editingKind != null) commitEdit();
        adjustStart(kind, delta);
        heldStartKind = kind; heldStartDelta = delta; heldStartTime = 0f; heldStartRepeat = 0f;
    }

    private void adjustStart(Kind kind, int delta) { start.put(kind, clampStartingCount(start.get(kind) + delta)); }
    private void beginEdit(Kind kind) { editingKind = kind; editingValue = ""; }
    private void commitEdit() {
        if (editingKind == null) return;
        try { start.put(editingKind, clampStartingCount(Integer.parseInt(editingValue))); }
        catch (NumberFormatException ignored) { }
        editingKind = null; editingValue = "";
    }

    private void setSpeedFromX(float x, float panelX, float width) {
        speed = clampSpeed(.35f + (x - (panelX + 23)) / (width - 46) * 2.15f);
    }

    private void drawCounterSprite(Kind kind, float x, float y, SpriteBatch batch) {
        Texture texture = kind == Kind.ROCK ? rockSprite : kind == Kind.PAPER
                ? paperSprite : scissorsSprites[(int)(elapsed * 4f) % scissorsSprites.length];
        float size = kind == Kind.SCISSORS ? 31f : 27f;
        batch.draw(texture, x - size * .5f, y - size * .5f, size, size);
    }

    private void playHit(Kind winner) {
        Sound sound = winner == Kind.ROCK ? rockKnock : winner == Kind.PAPER ? paperWrap : scissorsSnip;
        sound.play(.42f, .91f + random.nextFloat() * .16f, 0f);
    }

    private void playVictory() {
        if (victorySound != null) {
            victorySound.play(0.85f);
        }
    }

    private void spawnVictoryBurst(Kind winner) {
        confetti.clear();
        Color[] celebrationPalette = new Color[] {
            winner.bright,
            Color.WHITE,
            new Color(1f, 0.85f, 0.18f, 1f),
            new Color(0.20f, 0.95f, 1f, 1f),
            new Color(1f, 0.35f, 0.78f, 1f),
            new Color(0.40f, 1f, 0.45f, 1f)
        };

        for (int i = 0; i < 140; i++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float speedMag = 90f + random.nextFloat() * 320f;
            float vx = MathUtils.cos(angle) * speedMag;
            float vy = MathUtils.sin(angle) * speedMag * 0.75f + (80f + random.nextFloat() * 160f);

            float w = 6f + random.nextFloat() * 7f;
            float h = 10f + random.nextFloat() * 12f;
            float rot = random.nextFloat() * 360f;
            float rotSpd = (random.nextFloat() - 0.5f) * 420f;
            float flutterPh = random.nextFloat() * MathUtils.PI2;
            float flutterSpd = 5f + random.nextFloat() * 9f;
            Color col = celebrationPalette[random.nextInt(celebrationPalette.length)];
            float maxAge = 2.5f + random.nextFloat() * 2.2f;

            confetti.add(new VictoryConfetti(
                arenaX + (random.nextFloat() - 0.5f) * 100f,
                arenaY + (random.nextFloat() - 0.5f) * 40f,
                vx, vy, w, h, rot, rotSpd, flutterPh, flutterSpd, col, maxAge
            ));
        }
    }

    private void spawnTrickleConfetti(Kind winner) {
        Color[] celebrationPalette = new Color[] {
            winner.bright,
            Color.WHITE,
            new Color(1f, 0.85f, 0.18f, 1f),
            new Color(0.20f, 0.95f, 1f, 1f),
            new Color(1f, 0.35f, 0.78f, 1f)
        };
        float x = arenaX + (random.nextFloat() - 0.5f) * arenaRadius * 1.5f;
        float y = arenaY + arenaRadius * 0.85f + random.nextFloat() * 30f;
        float vx = (random.nextFloat() - 0.5f) * 70f;
        float vy = -(45f + random.nextFloat() * 65f);
        float w = 6f + random.nextFloat() * 6f;
        float h = 9f + random.nextFloat() * 10f;
        float rot = random.nextFloat() * 360f;
        float rotSpd = (random.nextFloat() - 0.5f) * 280f;
        float flutterPh = random.nextFloat() * MathUtils.PI2;
        float flutterSpd = 4f + random.nextFloat() * 7f;
        Color col = celebrationPalette[random.nextInt(celebrationPalette.length)];
        float maxAge = 3.0f + random.nextFloat() * 2.0f;

        confetti.add(new VictoryConfetti(x, y, vx, vy, w, h, rot, rotSpd, flutterPh, flutterSpd, col, maxAge));
    }

    private void drawConfetti(ShapeRenderer shapes) {
        if (confetti.size == 0) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (VictoryConfetti c : confetti) {
            float fadeProgress = c.age / c.maxAge;
            float alpha = (fadeProgress < 0.12f)
                    ? (fadeProgress / 0.12f)
                    : (1f - (fadeProgress - 0.12f) / 0.88f);
            alpha = MathUtils.clamp(alpha, 0f, 1f);

            float flutter = MathUtils.sin(c.flutterPhase);
            shapes.setColor(c.color.r, c.color.g, c.color.b, alpha * 0.92f);
            shapes.rect(c.x, c.y, c.width * 0.5f, c.height * 0.5f,
                    c.width, c.height, 1f, flutter, c.rotation);
        }
        shapes.end();
    }

    private void drawVictoryBanner(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        if (winnerKind == null) return;

        float t = Math.min(1f, victoryTimer / 0.48f);
        float p = t - 1f;
        float scale = (t < 1f) ? (1f + p * p * (2.6f * p + 1.6f)) : 1f;
        scale = Math.max(0.01f, scale);

        float bannerW = Math.min(480f, arenaRadius * 1.68f) * scale;
        float bannerH = 124f * scale;
        float bannerX = arenaX - bannerW * 0.5f;
        float bannerY = arenaY - bannerH * 0.5f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 1. Ambient pulsing aura behind the banner
        float pulse = 0.5f + 0.5f * MathUtils.sin(victoryTimer * 4.2f);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(winnerKind.bright.r, winnerKind.bright.g, winnerKind.bright.b, (0.12f + 0.09f * pulse) * Math.min(1f, scale));
        shapes.rect(bannerX - 16f * scale, bannerY - 14f * scale, bannerW + 32f * scale, bannerH + 28f * scale);

        // 2. Glassmorphic plaque backdrop
        shapes.setColor(.018f, .045f, .10f, 0.93f);
        shapes.rect(bannerX, bannerY, bannerW, bannerH);

        // 3. Top / Bottom champion neon accent bars
        shapes.setColor(winnerKind.bright.r, winnerKind.bright.g, winnerKind.bright.b, 0.90f);
        shapes.rect(bannerX, bannerY + bannerH - 4f * scale, bannerW, 4f * scale);
        shapes.setColor(winnerKind.bright.r, winnerKind.bright.g, winnerKind.bright.b, 0.42f);
        shapes.rect(bannerX, bannerY, bannerW, 2.5f * scale);
        shapes.end();

        // 4. Subtle crisp border
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(winnerKind.bright.r, winnerKind.bright.g, winnerKind.bright.b, 0.60f);
        shapes.rect(bannerX, bannerY, bannerW, bannerH);
        shapes.setColor(1f, 1f, 1f, 0.15f);
        shapes.rect(bannerX + 1.5f, bannerY + 1.5f, bannerW - 3f, bannerH - 3f);
        shapes.end();

        // 5. Draw icons and typography
        batch.begin();
        Texture winnerSprite = (winnerKind == Kind.ROCK) ? rockSprite :
                (winnerKind == Kind.PAPER) ? paperSprite :
                scissorsSprites[(int)(victoryTimer * 7f) % scissorsSprites.length];

        float iconSize = 48f * scale;
        float bobbing = MathUtils.sin(victoryTimer * 5.0f) * 4.5f * scale;
        float iconY = bannerY + bannerH * 0.5f - iconSize * 0.5f + bobbing;

        // Left winner mascot
        float leftIconX = bannerX + 24f * scale;
        batch.draw(winnerSprite, leftIconX, iconY, iconSize, iconSize);

        // Right winner mascot
        float rightIconX = bannerX + bannerW - 24f * scale - iconSize;
        batch.draw(winnerSprite, rightIconX, iconY, iconSize, iconSize);

        // Header Title
        String title = winnerKind.label + " WINS!";
        font.setColor(winnerKind.bright);
        font.getData().setScale(1.32f * scale);
        centeredText(font, batch, title, arenaX, bannerY + bannerH - 22f * scale);

        // Subtitle / Battle stats
        int survivors = count(winnerKind);
        String sub = survivors + " SURVIVORS   •   " + collisions + " CLASHES";
        font.setColor(.88f, .94f, 1f, 0.95f);
        font.getData().setScale(0.70f * scale);
        centeredText(font, batch, sub, arenaX, bannerY + bannerH * 0.48f);

        // Status / prompt
        String prompt = "SIMULATION RUNNING   •   PRESS RESET TO PLAY AGAIN";
        font.setColor(.45f, .70f, .90f, 0.82f);
        font.getData().setScale(0.52f * scale);
        centeredText(font, batch, prompt, arenaX, bannerY + 22f * scale);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    Kind getWinnerKind() { return winnerKind; }
    boolean isBattleActive() { return battleActive; }
    int getConfettiCount() { return confetti.size; }
    float getVictoryTimer() { return victoryTimer; }

    static Kind determineWinner(int rocks, int papers, int scissors) {
        int nonZero = 0;
        Kind candidate = null;
        if (rocks > 0) { nonZero++; candidate = Kind.ROCK; }
        if (papers > 0) { nonZero++; candidate = Kind.PAPER; }
        if (scissors > 0) { nonZero++; candidate = Kind.SCISSORS; }
        return (nonZero == 1) ? candidate : null;
    }

    private int count(Kind kind) { int total = 0; for (Piece piece : pieces) if (piece.kind == kind) total++; return total; }
    private static void text(BitmapFont font, SpriteBatch batch, String value, float x, float y) {
        Color cur = font.getColor();
        float r = cur.r, g = cur.g, b = cur.b, a = cur.a;
        font.setColor(0f, 0.01f, 0.03f, a * 0.72f);
        font.draw(batch, value, x + 1.8f, y - 1.8f);
        font.setColor(r, g, b, a);
        font.draw(batch, value, x, y);
    }

    private void centeredText(BitmapFont font, SpriteBatch batch, String value, float centerX, float y) {
        glyphLayout.setText(font, value);
        text(font, batch, value, centerX - glyphLayout.width * 0.5f, y);
    }

    private Texture createRockSprite() {
        Pixmap map = transparentPixmap(112, 112);
        for (int y = 3; y < 109; y++) for (int x = 3; x < 109; x++) {
            float dx = x - 56f, dy = y - 55f;
            float angle = MathUtils.atan2(dy, dx);
            float edge = 42f + MathUtils.sin(angle * 5f + .7f) * 4f + MathUtils.sin(angle * 9f) * 2.4f;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance > edge) continue;
            float grain = MathUtils.sin(x * 1.71f + y * 2.37f) * 10f + MathUtils.sin(x * .31f - y * .28f) * 9f;
            float light = (-dx - dy) * .36f;
            float gray = MathUtils.clamp(89 + grain + light, 44, 157) / 255f;
            map.setColor(gray * .92f, gray * .98f, gray, distance > edge - 2f ? .85f : 1f);
            map.drawPixel(x, y);
        }
        for (int i = 0; i < 19; i++) { map.setColor(.10f, .12f, .14f, .20f); map.fillCircle(30 + (i * 31) % 47, 29 + (i * 17) % 43, 1 + i % 3); }
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

    @Override public boolean consumeMenuRequest() { boolean result = menuRequested; menuRequested = false; return result; }
    @Override public void leave() { draggingSpeed = false; heldStartKind = null; editingKind = null; editingValue = ""; paused = false; }
    @Override public void dispose() {
        rockKnock.dispose(); paperWrap.dispose(); scissorsSnip.dispose();
        if (victorySound != null) victorySound.dispose();
        rockSprite.dispose(); paperSprite.dispose(); for (Texture texture : scissorsSprites) texture.dispose();
    }
}
