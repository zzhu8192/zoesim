package zoesim;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import zoesim.blackhole.BlackHoleSimulation;

/** Shared application shell: window lifecycle, menu selection, layout, and simulation dispatch. */
public final class SimArena extends ApplicationAdapter {
    private enum Mode { MENU, RPS, BLACK_HOLE }

    private final SimulationMenu menu = new SimulationMenu();
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private GlyphLayout layout;
    private OrthographicCamera camera;
    private RpsSimulation rps;
    private BlackHoleSimulation blackHole;
    private ArenaSimulation active;
    private Mode mode = Mode.MENU;
    private float arenaX, arenaY, arenaRadius, elapsed;

    @Override public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = CrispFont.createDefault();
        layout = new GlyphLayout();
        camera = new OrthographicCamera();
        rps = new RpsSimulation();
        blackHole = new BlackHoleSimulation();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyTyped(char character) {
                return active != null && active.keyTyped(character);
            }

            @Override public boolean keyDown(int keycode) {
                return active != null && active.keyDown(keycode);
            }
        });
        resizeWorld();
    }

    @Override public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 25f);
        elapsed += delta;
        resizeWorld();
        prepareFrame();
        if (mode == Mode.MENU) {
            SimulationMenu.Choice choice = menu.render(shapes, batch, font, camera);
            if (choice == SimulationMenu.Choice.RPS) activate(Mode.RPS, rps);
            else if (choice == SimulationMenu.Choice.BLACK_HOLE) activate(Mode.BLACK_HOLE, blackHole);
            return;
        }
        active.resize(arenaX, arenaY, arenaRadius);
        active.update(delta);
        active.render(elapsed, shapes, batch, font, layout);
        active.handleInput();
        if (active.consumeMenuRequest()) {
            active.leave();
            active = null;
            mode = Mode.MENU;
        }
    }

    private void activate(Mode nextMode, ArenaSimulation simulation) {
        mode = nextMode;
        active = simulation;
        active.enter(arenaX, arenaY, arenaRadius);
    }

    private void prepareFrame() {
        Gdx.gl.glClearColor(.003f, .006f, .014f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
    }

    private void resizeWorld() {
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.update();
        float panelWidth = MathUtils.clamp(Gdx.graphics.getWidth() * .31f, 360f, 480f);
        float left = 42f;
        float usable = Gdx.graphics.getWidth() - panelWidth - left - 60f;
        arenaRadius = Math.min(usable * .5f, Gdx.graphics.getHeight() * .40f);
        arenaX = left + usable * .5f;
        arenaY = Gdx.graphics.getHeight() * .52f;
    }

    @Override public void dispose() {
        if (active != null) active.leave();
        rps.dispose();
        blackHole.dispose();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
