package zoesim;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesktopLauncherTest {
    @Test public void headlessFlagIsRecognizedWithoutStartingGraphics() {
        assertTrue(DesktopLauncher.isHeadlessRequested(new String[] { "--headless" }));
        assertTrue(DesktopLauncher.isHeadlessRequested(new String[] { "--HEADLESS" }));
        assertFalse(DesktopLauncher.isHeadlessRequested(new String[0]));
        assertFalse(DesktopLauncher.isHeadlessRequested(null));
    }

    @Test public void headlessSmokeChecksRunWithoutCreatingAWindow() {
        DesktopLauncher.runHeadlessChecks();
    }
}
