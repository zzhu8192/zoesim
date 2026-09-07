package zoesim.blackhole;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/** User interface and input handling: telemetry, steppers, speed slider, and buttons. */
public final class BlackHoleUI {
    private boolean draggingSpeed;
    private boolean draggingGrowth;
    private boolean editingCount;
    private boolean menuRequested;
    private String countValue = "";
    private int heldCountDelta;
    private float heldCountTime;
    private float heldCountRepeat;

    public void render(BlackHoleWorld world, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font) {
        float arenaX = world.getArenaX();
        float arenaRadius = world.getArenaRadius();
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float H = Gdx.graphics.getHeight();
        float speed = world.getSpeed();
        float growth = world.getGrowthRate();

        float panelBottom = 68f;
        float panelTop = H - 48f;

        // Structured vertical bands anchored cleanly
        float headerY = panelTop - 18f;
        float subtitleY = panelTop - 58f;
        float separatorY = panelTop - 82f;

        // Telemetry Card
        float cardTop = panelTop - 98f;
        float cardHeight = 148f;
        float cardBottom = cardTop - cardHeight;
        float countRow = cardBottom + 26f;

        // Sliders
        float speedLabelY = cardBottom - 24f;
        float speedSliderY = speedLabelY - 26f;
        float growthLabelY = speedSliderY - 28f;
        float growthSliderY = growthLabelY - 26f;

        // Buttons
        float buttonY = growthSliderY - 38f;

        // Menu
        float menuBtnY = panelBottom + 62f;
        float taglineY = menuBtnY - 32f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.018f, .029f, .047f, .97f);
        shapes.rect(panelX, panelBottom, width, panelTop - panelBottom);
        shapes.setColor(.72f, .43f, .16f, .88f);
        shapes.rect(panelX, separatorY, width, 2);

        // Card background
        shapes.setColor(.035f, .055f, .078f, 1f);
        shapes.rect(panelX + 16, cardBottom, width - 32, cardHeight);

        // Subtle divider inside card between live count and seed
        shapes.setColor(1f, 1f, 1f, 0.06f);
        shapes.rect(panelX + 24, cardBottom + 64f, width - 48, 1);

        if (editingCount) {
            shapes.setColor(.11f, .19f, .27f, 1f);
            shapes.rect(panelX + 96, countRow - 22, 68, 30);
        }

        // 1. Time scale slider (0.1x - 10.0x)
        float speedFrac = MathUtils.clamp((speed - 0.10f) / 9.90f, 0f, 1f);
        shapes.setColor(.08f, .13f, .19f, 1f);
        shapes.rect(panelX + 23, speedSliderY, width - 46, 8);
        shapes.setColor(.43f, .72f, .91f, 1f);
        shapes.rect(panelX + 23, speedSliderY, (width - 46) * speedFrac, 8);
        shapes.circle(panelX + 23 + (width - 46) * speedFrac, speedSliderY + 4f, 12);

        // 2. Growth rate slider (0.2x - 8.0x)
        float growthFrac = MathUtils.clamp((growth - 0.20f) / 7.80f, 0f, 1f);
        shapes.setColor(.08f, .13f, .19f, 1f);
        shapes.rect(panelX + 23, growthSliderY, width - 46, 8);
        shapes.setColor(.95f, .62f, .32f, 1f);
        shapes.rect(panelX + 23, growthSliderY, (width - 46) * growthFrac, 8);
        shapes.circle(panelX + 23 + (width - 46) * growthFrac, growthSliderY + 4f, 12);

        shapes.end();

        batch.begin();

        // 1. Title & Subtitle
        font.setColor(1f, .82f, .57f, 1f);
        font.getData().setScale(1.35f);
        text(font, batch, "BLACK HOLE", panelX + 22, headerY);

        font.setColor(.52f, .69f, .80f, 1f);
        font.getData().setScale(0.64f);
        text(font, batch, "CONTINUOUS STELLAR ACCRETION", panelX + 23, subtitleY);

        // 2. Inside Telemetry Card:
        // Top row - Live Particles
        font.setColor(.62f, .84f, 1f, 1f);
        font.getData().setScale(0.68f);
        text(font, batch, "LIVE PARTICLES", panelX + 28, cardTop - 18f);

        font.setColor(1f, .84f, .54f, 1f);
        font.getData().setScale(1.35f);
        text(font, batch, String.valueOf(world.getCosmicParticles().size), panelX + 28, cardTop - 46f);

        // Right side stats: Absorbed & Mass
        float rightColX = panelX + Math.max(185f, width * 0.50f);
        font.setColor(.46f, .66f, .78f, 1f);
        font.getData().setScale(0.64f);
        text(font, batch, "ABSORBED  " + world.getAbsorbedParticles(), rightColX, cardTop - 20f);
        text(font, batch, "MASS  " + (int)world.getMass(), rightColX, cardTop - 46f);

        // Seed controls
        font.setColor(.55f, .71f, .81f, 1f);
        font.getData().setScale(0.68f);
        text(font, batch, "PARTICLE SEED", panelX + 28, cardBottom + 52f);

        font.setColor(Color.WHITE);
        font.getData().setScale(0.85f);
        text(font, batch, "-", panelX + 54, countRow);
        text(font, batch, editingCount ? (countValue.isEmpty() ? "|" : countValue + "|") : String.valueOf(world.getStartCount()), panelX + 112, countRow);
        text(font, batch, "+", panelX + 185, countRow);

        // 3. Sliders labels
        font.setColor(.55f, .71f, .81f, 1f);
        font.getData().setScale(0.72f);
        text(font, batch, "TIME SCALE  " + String.format("%.1fx", speed), panelX + 22, speedLabelY);

        font.setColor(.92f, .75f, .54f, 1f);
        text(font, batch, "GROWTH RATE  " + String.format("%.1fx", growth), panelX + 22, growthLabelY);

        // 4. Control buttons
        font.setColor(Color.WHITE);
        font.getData().setScale(0.80f);
        text(font, batch, world.isPaused() ? "▶  RESUME" : "Ⅱ  PAUSE", panelX + 24, buttonY);
        text(font, batch, "↻  NEW CLOUD", panelX + Math.max(180f, width * 0.48f), buttonY);

        // 5. Menu & Tagline
        font.setColor(.56f, .78f, 1f, 1f);
        font.getData().setScale(0.80f);
        text(font, batch, "SIMULATION MENU", panelX + 24, menuBtnY);

        font.setColor(.48f, .59f, .84f, 1f);
        font.getData().setScale(0.60f);
        text(font, batch, "SPIRAL IN. CROSS THE EVENT HORIZON.", panelX + 24, taglineY);

        batch.end();
    }

    public void handleInput(BlackHoleWorld world) {
        float arenaX = world.getArenaX();
        float arenaRadius = world.getArenaRadius();
        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float H = Gdx.graphics.getHeight();

        float panelBottom = 68f;
        float panelTop = H - 48f;
        float cardTop = panelTop - 98f;
        float cardHeight = 148f;
        float cardBottom = cardTop - cardHeight;
        float countRow = cardBottom + 26f;

        float speedLabelY = cardBottom - 24f;
        float speedSliderY = speedLabelY - 26f;
        float growthLabelY = speedSliderY - 28f;
        float growthSliderY = growthLabelY - 26f;

        float buttonY = growthSliderY - 38f;
        float menuBtnY = panelBottom + 62f;
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .1f);

        if (!Gdx.input.isTouched()) {
            draggingSpeed = false;
            draggingGrowth = false;
            heldCountDelta = 0;
            return;
        }
        if (draggingSpeed) {
            setSpeedFromX(world, x, panelX, width);
            return;
        }
        if (draggingGrowth) {
            setGrowthFromX(world, x, panelX, width);
            return;
        }
        if (heldCountDelta != 0) {
            heldCountTime += dt;
            if (heldCountTime > .34f) {
                heldCountRepeat += dt;
                while (heldCountRepeat >= .065f) {
                    adjustCount(world, heldCountDelta);
                    heldCountRepeat -= .065f;
                }
            }
            return;
        }
        if (!Gdx.input.justTouched()) return;
        if (x > panelX + 12 && x < panelX + width - 12 && Math.abs(y - menuBtnY) < 22) {
            menuRequested = true;
            return;
        }
        if (x > panelX + 35 && x < panelX + 85 && Math.abs(y - countRow) < 22) {
            startCountHold(world, -1);
            return;
        }
        if (x > panelX + 165 && x < panelX + 215 && Math.abs(y - countRow) < 22) {
            startCountHold(world, 1);
            return;
        }
        if (x > panelX + 85 && x < panelX + 165 && Math.abs(y - countRow) < 22) {
            editingCount = true;
            countValue = "";
            return;
        }
        if (x > panelX + 15 && x < panelX + width - 15 && Math.abs(y - speedSliderY) < 18) {
            draggingSpeed = true;
            setSpeedFromX(world, x, panelX, width);
            return;
        }
        if (x > panelX + 15 && x < panelX + width - 15 && Math.abs(y - growthSliderY) < 18) {
            draggingGrowth = true;
            setGrowthFromX(world, x, panelX, width);
            return;
        }
        if (Math.abs(y - buttonY) < 22) {
            if (x >= panelX + 15 && x < panelX + Math.max(170f, width * 0.46f)) {
                world.togglePaused();
            } else if (x >= panelX + Math.max(170f, width * 0.46f) && x < panelX + width - 10) {
                world.reset();
            }
        }
    }

    public boolean keyTyped(BlackHoleWorld world, char character) {
        if (!editingCount) return false;
        if (character >= '0' && character <= '9' && countValue.length() < 3) {
            countValue += character;
        } else if (character == '\b' && !countValue.isEmpty()) {
            countValue = countValue.substring(0, countValue.length() - 1);
        } else if (character == '\r' || character == '\n') {
            commitCountEdit(world);
        }
        return true;
    }

    public boolean keyDown(BlackHoleWorld world, int keycode) {
        if (!editingCount) return false;
        if (keycode == Input.Keys.ESCAPE) {
            editingCount = false;
            countValue = "";
            return true;
        }
        if (keycode == Input.Keys.ENTER) {
            commitCountEdit(world);
            return true;
        }
        return false;
    }

    private void startCountHold(BlackHoleWorld world, int delta) {
        if (editingCount) commitCountEdit(world);
        adjustCount(world, delta);
        heldCountDelta = delta;
        heldCountTime = 0f;
        heldCountRepeat = 0f;
    }

    private void adjustCount(BlackHoleWorld world, int delta) {
        world.setStartCount(BlackHolePhysics.clampSeedCount(world.getStartCount() + delta));
    }

    private void commitCountEdit(BlackHoleWorld world) {
        try {
            world.setStartCount(BlackHolePhysics.clampSeedCount(Integer.parseInt(countValue)));
        } catch (NumberFormatException ignored) { }
        editingCount = false;
        countValue = "";
    }

    private void setSpeedFromX(BlackHoleWorld world, float x, float panelX, float width) {
        float frac = MathUtils.clamp((x - (panelX + 23)) / (width - 46), 0f, 1f);
        world.setSpeed(BlackHolePhysics.clampSpeed(0.10f + frac * 9.90f));
    }

    private void setGrowthFromX(BlackHoleWorld world, float x, float panelX, float width) {
        float frac = MathUtils.clamp((x - (panelX + 23)) / (width - 46), 0f, 1f);
        world.setGrowthRate(BlackHolePhysics.clampGrowthRate(0.20f + frac * 7.80f));
    }

    private static void text(BitmapFont font, SpriteBatch batch, String value, float x, float y) {
        Color cur = font.getColor();
        float r = cur.r, g = cur.g, b = cur.b, a = cur.a;
        font.setColor(0f, 0.01f, 0.03f, a * 0.72f);
        font.draw(batch, value, x + 1.8f, y - 1.8f);
        font.setColor(r, g, b, a);
        font.draw(batch, value, x, y);
    }

    public boolean consumeMenuRequest() {
        boolean result = menuRequested;
        menuRequested = false;
        return result;
    }

    public void leave() {
        draggingSpeed = false;
        draggingGrowth = false;
        heldCountDelta = 0;
        editingCount = false;
        countValue = "";
        menuRequested = false;
    }
}
