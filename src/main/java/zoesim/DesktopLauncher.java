package zoesim;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import zoesim.blackhole.BlackHoleSimulation;

/** Desktop entry point for the Rock / Paper / Scissors arena. */
public final class DesktopLauncher {
    private DesktopLauncher() { }

    public static void main(String[] args) {
        if (isHeadlessRequested(args)) {
            System.setProperty("java.awt.headless", "true");
            runHeadlessChecks();
            return;
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("RPS Arena");
        config.setWindowedMode(1440, 900);
        config.setWindowSizeLimits(980, 650, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(120);
        new Lwjgl3Application(new SimArena(), config);
    }

    static boolean isHeadlessRequested(String[] args) {
        if (args == null) return false;
        for (String argument : args) if ("--headless".equalsIgnoreCase(argument)) return true;
        return false;
    }

    static void runHeadlessChecks() {
        if (!RpsSimulation.beats(0, 2) || !RpsSimulation.beats(1, 0) || !RpsSimulation.beats(2, 1)) {
            throw new IllegalStateException("RPS rules failed their headless check");
        }
        float initialHorizon = BlackHoleSimulation.eventHorizon(BlackHoleSimulation.INITIAL_MASS);
        float grownHorizon = BlackHoleSimulation.eventHorizon(BlackHoleSimulation.INITIAL_MASS + 1000f);
        if (!(grownHorizon > initialHorizon)) {
            throw new IllegalStateException("Black-hole growth failed its headless check");
        }
        System.out.println("Headless simulation checks passed.");
    }
}
