package zoesim;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates ultra-sharp, anti-aliased, high-resolution BitmapFonts
 * using Java 2D TrueType rasterization and vector icon glyphs.
 */
public final class CrispFont {

    public static final int BASE_FONT_SIZE = 32;
    public static final int ATLAS_SIZE = 1024;

    public static final char CHAR_PLAY = '▶';     // U+25B6
    public static final char CHAR_PAUSE = 'Ⅱ';    // U+2161
    public static final char CHAR_RESET = '↻';    // U+21BB
    public static final char CHAR_BULLET = '•';   // U+2022

    public static final class FontAtlasResult {
        public final BitmapFontData fontData;
        public final BufferedImage bufferedImage;

        public FontAtlasResult(BitmapFontData fontData, BufferedImage bufferedImage) {
            this.fontData = fontData;
            this.bufferedImage = bufferedImage;
        }
    }

    private static class GlyphEntry {
        char ch;
        int x, y, width, height, xoffset, yoffset, xadvance;
        boolean custom;
    }

    public static FontAtlasResult buildAtlas(int fontSize) {
        Font font = selectFont(fontSize);
        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gTemp = tempImg.createGraphics();
        gTemp.setFont(font);
        gTemp.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gTemp.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontMetrics fm = gTemp.getFontMetrics();
        FontRenderContext frc = gTemp.getFontRenderContext();
        gTemp.dispose();

        int ascent = fm.getAscent();
        int descent = fm.getDescent();
        int lineHeight = fm.getHeight();

        List<GlyphEntry> entries = new ArrayList<>();

        // 1. Space
        GlyphEntry space = new GlyphEntry();
        space.ch = ' ';
        space.x = 0;
        space.y = 0;
        space.width = 0;
        space.height = 0;
        space.xoffset = 0;
        space.yoffset = 0;
        space.xadvance = fm.charWidth(' ');
        entries.add(space);

        // 2. Printable ASCII 33..126
        for (char c = 33; c <= 126; c++) {
            GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
            Rectangle bounds = gv.getPixelBounds(null, 0, 0);
            GlyphEntry e = new GlyphEntry();
            e.ch = c;
            e.width = Math.max(1, bounds.width);
            e.height = Math.max(1, bounds.height);
            e.xoffset = bounds.x;
            e.yoffset = ascent + bounds.y;
            e.xadvance = Math.max(e.width, fm.charWidth(c));
            entries.add(e);
        }

        // 3. Custom symbols: ▶, Ⅱ, ↻, •
        int iconSize = Math.round(fontSize * 0.82f);

        // Play: ▶
        GlyphEntry play = new GlyphEntry();
        play.ch = CHAR_PLAY;
        play.width = iconSize;
        play.height = iconSize;
        play.xoffset = 1;
        play.yoffset = ascent - iconSize + Math.round(fontSize * 0.08f);
        play.xadvance = iconSize + Math.round(fontSize * 0.22f);
        play.custom = true;
        entries.add(play);

        // Pause: Ⅱ
        GlyphEntry pause = new GlyphEntry();
        pause.ch = CHAR_PAUSE;
        pause.width = iconSize;
        pause.height = iconSize;
        pause.xoffset = 1;
        pause.yoffset = ascent - iconSize + Math.round(fontSize * 0.08f);
        pause.xadvance = iconSize + Math.round(fontSize * 0.22f);
        pause.custom = true;
        entries.add(pause);

        // Reset / Reload: ↻
        GlyphEntry reset = new GlyphEntry();
        reset.ch = CHAR_RESET;
        reset.width = iconSize;
        reset.height = iconSize;
        reset.xoffset = 1;
        reset.yoffset = ascent - iconSize + Math.round(fontSize * 0.08f);
        reset.xadvance = iconSize + Math.round(fontSize * 0.22f);
        reset.custom = true;
        entries.add(reset);

        // Bullet: •
        GlyphEntry bullet = new GlyphEntry();
        int bulletSize = Math.max(6, Math.round(fontSize * 0.32f));
        bullet.ch = CHAR_BULLET;
        bullet.width = bulletSize;
        bullet.height = bulletSize;
        bullet.xoffset = Math.round(fontSize * 0.10f);
        bullet.yoffset = ascent - Math.round(fontSize * 0.55f);
        bullet.xadvance = bulletSize + Math.round(fontSize * 0.25f);
        bullet.custom = true;
        entries.add(bullet);

        // Pack into atlas image
        BufferedImage atlas = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int currentX = 4;
        int currentY = 4;
        int rowHeight = 0;
        final int padding = 3;

        StringBuilder fnt = new StringBuilder(4096);
        fnt.append("info face=\"CrispFont\" size=").append(fontSize)
           .append(" bold=1 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0\n");
        fnt.append("common lineHeight=").append(lineHeight)
           .append(" base=").append(ascent)
           .append(" scaleW=").append(ATLAS_SIZE)
           .append(" scaleH=").append(ATLAS_SIZE)
           .append(" pages=1 packed=0\n");
        fnt.append("page id=0 file=\"crisp_font.png\"\n");
        fnt.append("chars count=").append(entries.size()).append("\n");

        for (GlyphEntry e : entries) {
            if (e.width > 0 && e.height > 0) {
                if (currentX + e.width + padding > ATLAS_SIZE - 4) {
                    currentX = 4;
                    currentY += rowHeight + padding;
                    rowHeight = 0;
                }
                e.x = currentX;
                e.y = currentY;
                rowHeight = Math.max(rowHeight, e.height);
                currentX += e.width + padding;

                if (e.custom) {
                    drawCustomGlyph(g2d, e.ch, e.x, e.y, e.width, e.height);
                } else {
                    GlyphVector gv = font.createGlyphVector(frc, String.valueOf(e.ch));
                    Rectangle bounds = gv.getPixelBounds(null, 0, 0);
                    g2d.drawString(String.valueOf(e.ch), e.x - bounds.x, e.y - bounds.y);
                }
            }

            fnt.append("char id=").append((int)e.ch)
               .append(" x=").append(e.x)
               .append(" y=").append(e.y)
               .append(" width=").append(e.width)
               .append(" height=").append(e.height)
               .append(" xoffset=").append(e.xoffset)
               .append(" yoffset=").append(e.yoffset)
               .append(" xadvance=").append(e.xadvance)
               .append(" page=0 chnl=15\n");
        }

        g2d.dispose();

        File tempFnt = null;
        try {
            tempFnt = File.createTempFile("crisp_font_", ".fnt");
            Files.writeString(tempFnt.toPath(), fnt.toString());
            FileHandle handle = new FileHandle(tempFnt);
            BitmapFontData fontData = new BitmapFontData(handle, false);
            return new FontAtlasResult(fontData, atlas);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to generate CrispFont data", ex);
        } finally {
            if (tempFnt != null) {
                tempFnt.delete();
            }
        }
    }

    public static BitmapFont createDefault() {
        FontAtlasResult result = buildAtlas(BASE_FONT_SIZE);
        BufferedImage img = result.bufferedImage;

        Pixmap pixmap = new Pixmap(ATLAS_SIZE, ATLAS_SIZE, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);

        int[] rowBuffer = new int[ATLAS_SIZE];
        for (int y = 0; y < ATLAS_SIZE; y++) {
            img.getRGB(0, y, ATLAS_SIZE, 1, rowBuffer, 0, ATLAS_SIZE);
            for (int x = 0; x < ATLAS_SIZE; x++) {
                int argb = rowBuffer[x];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha > 0) {
                    // Force clean solid white with anti-aliased alpha
                    int rgba = (0xFF << 24) | (0xFF << 16) | (0xFF << 8) | alpha;
                    pixmap.drawPixel(x, y, rgba);
                } else {
                    pixmap.drawPixel(x, y, 0);
                }
            }
        }

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        BitmapFont font = new BitmapFont(result.fontData, new TextureRegion(texture), false);
        font.setOwnsTexture(true);
        return font;
    }

    private static Font selectFont(int fontSize) {
        String[] preferred = { "Segoe UI", "SF Pro Display", "Roboto", "Helvetica Neue", "Arial", Font.SANS_SERIF };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFamilies = ge.getAvailableFontFamilyNames();

        for (String family : preferred) {
            for (String avail : availableFamilies) {
                if (family.equalsIgnoreCase(avail)) {
                    return new Font(avail, Font.BOLD, fontSize);
                }
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
    }

    private static void drawCustomGlyph(Graphics2D g, char ch, int x, int y, int w, int h) {
        g.setColor(Color.WHITE);
        if (ch == CHAR_PLAY) {
            Path2D.Float triangle = new Path2D.Float();
            float padX = w * 0.15f;
            float padY = h * 0.15f;
            triangle.moveTo(x + padX, y + padY);
            triangle.lineTo(x + w - padX * 0.5f, y + h * 0.5f);
            triangle.lineTo(x + padX, y + h - padY);
            triangle.closePath();
            g.fill(triangle);
        } else if (ch == CHAR_PAUSE) {
            int barW = Math.max(3, Math.round(w * 0.28f));
            int gap = Math.max(3, Math.round(w * 0.22f));
            int startX = x + (w - (barW * 2 + gap)) / 2;
            int padY = Math.round(h * 0.12f);
            int barH = h - padY * 2;
            int arc = Math.max(2, Math.round(barW * 0.4f));
            g.fillRoundRect(startX, y + padY, barW, barH, arc, arc);
            g.fillRoundRect(startX + barW + gap, y + padY, barW, barH, arc, arc);
        } else if (ch == CHAR_RESET) {
            Stroke oldStroke = g.getStroke();
            float strokeW = Math.max(2.5f, w * 0.14f);
            g.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = Math.round(w * 0.18f);
            g.drawArc(x + pad, y + pad, w - pad * 2, h - pad * 2, 45, 275);
            // Arrowhead at the top right of the arc
            float arrowSize = w * 0.32f;
            float arrowX = x + w - pad - 1f;
            float arrowY = y + pad + (h - pad * 2) * 0.25f;
            Path2D.Float arrow = new Path2D.Float();
            arrow.moveTo(arrowX - arrowSize * 0.8f, arrowY - arrowSize * 0.5f);
            arrow.lineTo(arrowX + arrowSize * 0.4f, arrowY);
            arrow.lineTo(arrowX - arrowSize * 0.2f, arrowY + arrowSize * 0.8f);
            arrow.closePath();
            g.fill(arrow);
            g.setStroke(oldStroke);
        } else if (ch == CHAR_BULLET) {
            g.fillOval(x, y, w, h);
        }
    }
}
