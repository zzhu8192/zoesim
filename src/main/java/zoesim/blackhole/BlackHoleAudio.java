package zoesim.blackhole;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

/** Audio manager for tidal absorption sounds and ambient space drone. */
public final class BlackHoleAudio {
    private final Sound absorbSound;
    private final Sound ambience;
    private long ambientLoopId = -1;
    private float nextAbsorbSound;

    public BlackHoleAudio() {
        if (Gdx.audio != null && Gdx.files != null) {
            absorbSound = Gdx.audio.newSound(Gdx.files.internal("sounds/black-hole-absorb.ogg"));
            ambience = Gdx.audio.newSound(Gdx.files.internal("sounds/space-ambience.ogg"));
        } else {
            absorbSound = null;
            ambience = null;
        }
    }

    public void update(float dt) {
        if (nextAbsorbSound > 0f) {
            nextAbsorbSound -= dt;
        }
    }

    public void playAbsorb(float particleSize) {
        if (absorbSound != null && nextAbsorbSound <= 0f) {
            absorbSound.play(.44f, .38f + Math.min(.26f, particleSize * .04f), 0f);
            nextAbsorbSound = .12f;
        }
    }

    public void startAmbience() {
        if (ambience != null && ambientLoopId < 0) {
            ambientLoopId = ambience.loop(.15f, .52f, 0f);
        }
    }

    public void stopAmbience() {
        if (ambience != null && ambientLoopId >= 0) {
            ambience.stop(ambientLoopId);
            ambientLoopId = -1;
        }
    }

    public void reset() {
        nextAbsorbSound = 0f;
    }

    public void dispose() {
        stopAmbience();
        if (absorbSound != null) absorbSound.dispose();
        if (ambience != null) ambience.dispose();
    }
}
