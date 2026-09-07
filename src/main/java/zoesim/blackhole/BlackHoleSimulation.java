package zoesim.blackhole;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import zoesim.ArenaSimulation;

/**
 * Complete black-hole accretion simulation facade coordinating physics, state, rendering, and UI.
 */
public final class BlackHoleSimulation implements ArenaSimulation {
    public static final float GRAVITATIONAL_CONSTANT = BlackHolePhysics.GRAVITATIONAL_CONSTANT;
    public static final float INITIAL_MASS = BlackHolePhysics.INITIAL_MASS;

    private final BlackHoleAudio audio;
    private final BlackHoleWorld world;
    private final BlackHoleRenderer renderer;
    private final BlackHoleUI ui;

    public BlackHoleSimulation() {
        audio = new BlackHoleAudio();
        world = new BlackHoleWorld(audio);
        renderer = new BlackHoleRenderer();
        ui = new BlackHoleUI();
    }

    public static float gravitationalParameter(float mass) {
        return BlackHolePhysics.gravitationalParameter(mass);
    }

    public static float gravitationalAcceleration(float mass, float distanceSquared, float softeningSquared) {
        return BlackHolePhysics.gravitationalAcceleration(mass, distanceSquared, softeningSquared);
    }

    public static float eventHorizon(float mass) {
        return BlackHolePhysics.eventHorizon(mass);
    }

    public static float particleMass(float radius) {
        return BlackHolePhysics.particleMass(radius);
    }

    public static float lensedImageRadius(float sourceRadius, float einsteinRadius) {
        return BlackHolePhysics.lensedImageRadius(sourceRadius, einsteinRadius);
    }

    public static float spatialDistanceSquared(float dx, float dy, float dz) {
        return BlackHolePhysics.spatialDistanceSquared(dx, dy, dz);
    }

    public static float projectedBodyScale(float depth, float arenaRadius) {
        return BlackHolePhysics.projectedBodyScale(depth, arenaRadius);
    }

    public static boolean fillsField(float mass, float arenaRadius) {
        return BlackHolePhysics.fillsField(mass, arenaRadius);
    }

    public static int clampSeedCount(int count) {
        return BlackHolePhysics.clampSeedCount(count);
    }

    @Override
    public void enter(float arenaX, float arenaY, float arenaRadius) {
        world.resize(arenaX, arenaY, arenaRadius);
        ui.leave();
        world.reset();
    }

    @Override
    public void resize(float arenaX, float arenaY, float arenaRadius) {
        world.resize(arenaX, arenaY, arenaRadius);
    }

    @Override
    public void update(float dt) {
        world.update(dt);
    }

    @Override
    public void render(float elapsed, ShapeRenderer shapes, SpriteBatch batch, BitmapFont font, GlyphLayout layout) {
        renderer.render(world, elapsed, shapes, batch, font, layout);
        ui.render(world, shapes, batch, font);
    }

    @Override
    public void handleInput() {
        ui.handleInput(world);
    }

    @Override
    public boolean keyTyped(char character) {
        return ui.keyTyped(world, character);
    }

    @Override
    public boolean keyDown(int keycode) {
        return ui.keyDown(world, keycode);
    }

    @Override
    public boolean consumeMenuRequest() {
        return ui.consumeMenuRequest();
    }

    @Override
    public void leave() {
        ui.leave();
        world.setPaused(false);
        audio.stopAmbience();
    }

    @Override
    public void dispose() {
        audio.dispose();
        renderer.dispose();
    }
}
