package zoesim;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Desktop entry point for the Rock / Paper / Scissors arena. */
public final class DesktopLauncher {
    private DesktopLauncher() { }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("RPS Arena");
        config.setWindowedMode(1440, 900);
        config.setWindowSizeLimits(980, 650, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(120);
        new Lwjgl3Application(new RpsArena(), config);
    }
}
