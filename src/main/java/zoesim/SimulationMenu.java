package zoesim;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Start screen and future-friendly simulation chooser. */
public final class SimulationMenu {
    public enum Choice { NONE, RPS, BLACK_HOLE }

    public Choice render(ShapeRenderer shapes, SpriteBatch batch, BitmapFont font, OrthographicCamera camera) {
        float width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        float cardWidth = Math.min(420f, width * .42f), cardX = width * .5f - cardWidth * .5f;
        float rpsY = height * .49f, blackHoleY = height * .30f;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.018f, .028f, .08f, 1f); shapes.rect(0, 0, width, height);
        shapes.setColor(.05f, .13f, .24f, .58f); shapes.circle(width * .5f, height * .58f, Math.min(width, height) * .43f);
        card(shapes, cardX, rpsY, cardWidth, 124, new Color(.05f, .20f, .32f, 1f));
        card(shapes, cardX, blackHoleY, cardWidth, 124, new Color(.13f, .045f, .25f, 1f));
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(.82f, .94f, 1f, 1f); font.getData().setScale(2.5f); centered(font, batch, "SIMULATION LAB", width * .5f, height * .80f);
        font.setColor(.52f, .69f, .86f, 1f); font.getData().setScale(1.08f); centered(font, batch, "CHOOSE A SIMULATION", width * .5f, height * .755f);
        font.setColor(.65f, .92f, 1f, 1f); font.getData().setScale(1.55f); centered(font, batch, "ROCK / PAPER / SCISSORS", width * .5f, rpsY + 70);
        font.setColor(.57f, .72f, .84f, 1f); font.getData().setScale(1f); centered(font, batch, "SELF-REPLICATING BATTLE ARENA", width * .5f, rpsY + 38);
        font.setColor(1f, .82f, .57f, 1f); font.getData().setScale(1.55f); centered(font, batch, "BLACK HOLE", width * .5f, blackHoleY + 70);
        font.setColor(.55f, .72f, .84f, 1f); font.getData().setScale(1f); centered(font, batch, "STELLAR ACCRETION AND RELATIVISTIC JETS", width * .5f, blackHoleY + 38);
        font.getData().setScale(1.0f);
        batch.end();
        if (!Gdx.input.justTouched()) return Choice.NONE;
        float x = Gdx.input.getX(), y = height - Gdx.input.getY();
        if (x < cardX || x > cardX + cardWidth) return Choice.NONE;
        if (y >= rpsY && y <= rpsY + 124) return Choice.RPS;
        if (y >= blackHoleY && y <= blackHoleY + 124) return Choice.BLACK_HOLE;
        return Choice.NONE;
    }

    private static void card(ShapeRenderer shapes, float x, float y, float width, float height, Color color) {
        shapes.setColor(color); shapes.rect(x, y, width, height);
        shapes.setColor(1f, 1f, 1f, .08f); shapes.rect(x, y + height - 2, width, 2);
    }

    private static void centered(BitmapFont font, SpriteBatch batch, String text, float x, float y) {
        float width = font.getRegion().getRegionWidth() * 0; // keeps this helper allocation-free aside from GlyphLayout-free approximation below
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout(font, text);
        font.draw(batch, text, x - layout.width * .5f, y);
    }
}
