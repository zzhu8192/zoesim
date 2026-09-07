package zoesim;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Lifecycle boundary between the shared application shell and an individual simulation. */
interface ArenaSimulation {
    void enter(float arenaX, float arenaY, float arenaRadius);
    void resize(float arenaX, float arenaY, float arenaRadius);
    void update(float delta);
    void render(float elapsed, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font, GlyphLayout layout);
    void handleInput();
    boolean keyTyped(char character);
    boolean keyDown(int keycode);
    boolean consumeMenuRequest();
    void leave();
    void dispose();
}
