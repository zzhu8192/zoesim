package zoesim;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CrispFontTest {

    @Test
    public void testBuildAtlas() {
        CrispFont.FontAtlasResult result = CrispFont.buildAtlas(32);
        assertNotNull(result);
        assertNotNull(result.fontData);
        assertNotNull(result.bufferedImage);
        assertEquals(CrispFont.ATLAS_SIZE, result.bufferedImage.getWidth());
        assertEquals(CrispFont.ATLAS_SIZE, result.bufferedImage.getHeight());

        BitmapFontData data = result.fontData;
        assertTrue("lineHeight should be > 30", data.lineHeight > 30);
        assertTrue("capHeight should be > 18", data.capHeight > 18);

        // Verify ASCII letters and digits
        for (char c = '0'; c <= '9'; c++) {
            Glyph g = data.getGlyph(c);
            assertNotNull("Missing glyph for digit " + c, g);
            assertTrue(g.width > 0);
            assertTrue(g.height > 0);
            assertTrue(g.xadvance > 0);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            Glyph g = data.getGlyph(c);
            assertNotNull("Missing glyph for uppercase " + c, g);
            assertTrue(g.width > 0);
            assertTrue(g.height > 0);
            assertTrue(g.xadvance > 0);
        }

        // Verify custom symbols
        Glyph gPlay = data.getGlyph(CrispFont.CHAR_PLAY);
        assertNotNull("Missing glyph for PLAY", gPlay);
        assertTrue(gPlay.width > 0 && gPlay.height > 0);

        Glyph gPause = data.getGlyph(CrispFont.CHAR_PAUSE);
        assertNotNull("Missing glyph for PAUSE", gPause);
        assertTrue(gPause.width > 0 && gPause.height > 0);

        Glyph gReset = data.getGlyph(CrispFont.CHAR_RESET);
        assertNotNull("Missing glyph for RESET", gReset);
        assertTrue(gReset.width > 0 && gReset.height > 0);

        Glyph gBullet = data.getGlyph(CrispFont.CHAR_BULLET);
        assertNotNull("Missing glyph for BULLET", gBullet);
        assertTrue(gBullet.width > 0 && gBullet.height > 0);
    }
}
