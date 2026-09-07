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
        float speedSliderY = Gdx.graphics.getHeight() - 384f;
        float growthSliderY = Gdx.graphics.getHeight() - 454f;
        float countRow = Gdx.graphics.getHeight() - 304f;
        float speed = world.getSpeed();
        float growth = world.getGrowthRate();
        float H = Gdx.graphics.getHeight();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.018f, .029f, .047f, .97f);
        shapes.rect(panelX, 76, width, H - 152);
        shapes.setColor(.72f, .43f, .16f, .88f);
        shapes.rect(panelX, H - 132, width, 2);
        shapes.setColor(.035f, .055f, .078f, 1f);
        shapes.rect(panelX + 18, H - 336, width - 36, 186);
        if (editingCount) {
            shapes.setColor(.11f, .19f, .27f, 1f);
            shapes.rect(panelX + 96, countRow - 28, 70, 38);
        }

        // 1. Time scale slider (0.1x - 10.0x)
        float speedFrac = MathUtils.clamp((speed - 0.10f) / 9.90f, 0f, 1f);
        shapes.setColor(.08f, .13f, .19f, 1f);
        shapes.rect(panelX + 23, speedSliderY, width - 46, 9);
        shapes.setColor(.43f, .72f, .91f, 1f);
        shapes.rect(panelX + 23, speedSliderY, (width - 46) * speedFrac, 9);
        shapes.circle(panelX + 23 + (width - 46) * speedFrac, speedSliderY + 4.5f, 13);

        // 2. Growth rate slider (0.2x - 8.0x)
        float growthFrac = MathUtils.clamp((growth - 0.20f) / 7.80f, 0f, 1f);
        shapes.setColor(.08f, .13f, .19f, 1f);
        shapes.rect(panelX + 23, growthSliderY, width - 46, 9);
        shapes.setColor(.95f, .62f, .32f, 1f);
        shapes.rect(panelX + 23, growthSliderY, (width - 46) * growthFrac, 9);
        shapes.circle(panelX + 23 + (width - 46) * growthFrac, growthSliderY + 4.5f, 13);

        shapes.end();

        batch.begin();
        font.setColor(1f, .82f, .57f, 1f);
        font.getData().setScale(1.75f);
        text(font, batch, "BLACK HOLE", panelX + 22, H - 76);
        font.setColor(.52f, .69f, .80f, 1f);
        font.getData().setScale(0.88f);
        text(font, batch, "CONTINUOUS STELLAR ACCRETION", panelX + 23, H - 114);
        font.setColor(Color.WHITE);
        font.getData().setScale(0.92f);
        text(font, batch, "LIVE PARTICLES", panelX + 36, H - 168);
        font.setColor(.95f, .79f, .53f, 1f);
        font.getData().setScale(1.85f);
        text(font, batch, String.valueOf(world.getCosmicParticles().size), panelX + 36, H - 212);
        font.setColor(.44f, .62f, .73f, 1f);
        font.getData().setScale(0.82f);
        text(font, batch, "ABSORBED  " + world.getAbsorbedParticles(), panelX + 175, H - 186);
        text(font, batch, "MASS  " + (int)world.getMass(), panelX + 175, H - 216);
        font.setColor(.55f, .71f, .81f, 1f);
        font.getData().setScale(0.88f);
        text(font, batch, "PARTICLE SEED", panelX + 36, H - 262);
        font.setColor(Color.WHITE);
        font.getData().setScale(1.02f);
        text(font, batch, "-", panelX + 54, countRow);
        text(font, batch, editingCount ? (countValue.isEmpty() ? "|" : countValue + "|") : String.valueOf(world.getStartCount()), panelX + 112, countRow);
        text(font, batch, "+", panelX + 185, countRow);

        // Sliders labels
        font.setColor(.55f, .71f, .81f, 1f);
        font.getData().setScale(0.88f);
        text(font, batch, "TIME SCALE  " + String.format("%.1fx", speed), panelX + 22, speedSliderY + 30);
        font.setColor(.92f, .75f, .54f, 1f);
        text(font, batch, "GROWTH RATE  " + String.format("%.1fx", growth), panelX + 22, growthSliderY + 30);

        // Control buttons
        font.setColor(Color.WHITE);
        font.getData().setScale(0.96f);
        text(font, batch, world.isPaused() ? "▶  RESUME" : "Ⅱ  PAUSE", panelX + 24, growthSliderY - 38);
        text(font, batch, "↻  NEW CLOUD", panelX + 185, growthSliderY - 38);
        font.setColor(.56f, .78f, 1f, 1f);
        font.getData().setScale(0.95f);
        text(font, batch, "SIMULATION MENU", panelX + 24, 154);
        font.setColor(.48f, .59f, .84f, 1f);
        font.getData().setScale(0.76f);
        text(font, batch, "SPIRAL IN. CROSS THE EVENT HORIZON.", panelX + 24, 112);
        batch.end();
    }

    public void handleInput(BlackHoleWorld world) {
        float arenaX = world.getArenaX();
        float arenaRadius = world.getArenaRadius();
        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();
        float panelX = arenaX + arenaRadius + 42f;
        float width = Gdx.graphics.getWidth() - panelX - 32f;
        float speedSliderY = Gdx.graphics.getHeight() - 384f;
        float growthSliderY = Gdx.graphics.getHeight() - 454f;
        float countRow = Gdx.graphics.getHeight() - 304f;
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
        if (x > panelX + 12 && x < panelX + width - 12 && y > 120 && y < 172) {
            menuRequested = true;
            return;
        }
        if (x > panelX + 35 && x < panelX + 90 && Math.abs(y - countRow) < 26) {
            startCountHold(world, -1);
            return;
        }
        if (x > panelX + 165 && x < panelX + 225 && Math.abs(y - countRow) < 26) {
            startCountHold(world, 1);
            return;
        }
        if (x > panelX + 90 && x < panelX + 165 && Math.abs(y - countRow) < 26) {
            editingCount = true;
            countValue = "";
            return;
        }
        if (x > panelX + 15 && x < panelX + width - 15 && Math.abs(y - speedSliderY) < 22) {
            draggingSpeed = true;
            setSpeedFromX(world, x, panelX, width);
            return;
        }
        if (x > panelX + 15 && x < panelX + width - 15 && Math.abs(y - growthSliderY) < 22) {
            draggingGrowth = true;
            setGrowthFromX(world, x, panelX, width);
            return;
        }
        if (y > growthSliderY - 58 && y < growthSliderY - 14) {
            if (x < panelX + 175) {
                world.togglePaused();
            } else if (x < panelX + 345) {
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
