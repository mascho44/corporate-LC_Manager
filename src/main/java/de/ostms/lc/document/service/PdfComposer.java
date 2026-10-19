package de.ostms.lc.document.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Small page layout toolkit on top of PDFBox: text with wrapping, rules, filled boxes, tables that continue on the next page with their header,
 * and a footer with page numbers. All positions are measured from the top of the page.
 */
final class PdfComposer implements AutoCloseable {
    static final Color NAVY = new Color(0x1F, 0x3A, 0x5F), ACCENT = new Color(0x2F, 0x6D, 0xB5), GREY = new Color(0x6B, 0x72, 0x80), LINE = new Color(0xC9, 0xD1, 0xDE),
        ZEBRA = new Color(0xF5, 0xF7, 0xFB), SUM = new Color(0xDC, 0xE5, 0xF2), INK = new Color(0x11, 0x18, 0x27), WHITE = Color.WHITE;
    static final float LEFT = 50, WIDTH = 495, TOP = 48, BOTTOM = 92;
    static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA), BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    final PDDocument doc = new PDDocument();
    private PDPage page;
    private PDPageContentStream out;
    /** Distance of the next line from the top edge of the page. */
    float y;

    PdfComposer() throws IOException { newPage(); }

    void newPage() throws IOException {
        if (out != null) out.close();
        page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        out = new PDPageContentStream(doc, page);
        y = TOP;
    }

    /** Starts a new page when less than the given height is left above the footer. */
    void ensure(float height) throws IOException {
        if (y + height > PDRectangle.A4.getHeight() - BOTTOM) newPage();
    }

    static String safe(PDFont font, String text) {
        if (text == null) return "";
        var clean = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == ' ') c = ' ';
            if (c < 32 || c == 127) continue;
            try { font.encode(String.valueOf(c)); clean.append(c); } catch (IllegalArgumentException | IOException unsupported) { clean.append('?'); }
        }
        return clean.toString();
    }

    static float width(PDFont font, float size, String text) {
        try { return font.getStringWidth(safe(font, text)) / 1000f * size; } catch (IOException e) { return 0; }
    }

    /** Wraps text (with explicit line breaks) into lines no wider than the given width. */
    static List<String> wrap(PDFont font, float size, String text, float maxWidth) {
        var lines = new ArrayList<String>();
        if (text == null) return lines;
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isBlank()) { lines.add(""); continue; }
            var line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(font, size, candidate) <= maxWidth) { line.setLength(0); line.append(candidate); continue; }
                if (line.length() > 0) { lines.add(line.toString()); line.setLength(0); }
                while (width(font, size, word) > maxWidth && word.length() > 1) {
                    int cut = word.length() - 1;
                    while (cut > 1 && width(font, size, word.substring(0, cut)) > maxWidth) cut--;
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    static float lineHeight(float size) { return size * 1.3f; }

    /** One line of text whose baseline is at the given distance from the top. */
    void textAt(float x, float top, PDFont font, float size, Color color, String text) throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.setNonStrokingColor(color);
        out.newLineAtOffset(x, PDRectangle.A4.getHeight() - top);
        out.showText(safe(font, text));
        out.endText();
    }

    void textRight(float right, float top, PDFont font, float size, Color color, String text) throws IOException {
        textAt(right - width(font, size, text), top, font, size, color, text);
    }

    /** Wrapped paragraph at the cursor; moves the cursor below it and continues on the next page when needed. */
    void paragraph(PDFont font, float size, Color color, String text, float x, float maxWidth, float after) throws IOException {
        float lh = lineHeight(size);
        for (String line : wrap(font, size, text, maxWidth)) {
            ensure(lh);
            y += size;
            textAt(x, y, font, size, color, line);
            y += lh - size;
        }
        y += after;
    }

    void paragraph(PDFont font, float size, Color color, String text, float after) throws IOException { paragraph(font, size, color, text, LEFT, WIDTH, after); }

    void rule(float top, float x1, float x2, float thickness, Color color) throws IOException {
        out.setStrokingColor(color);
        out.setLineWidth(thickness);
        float yy = PDRectangle.A4.getHeight() - top;
        out.moveTo(x1, yy);
        out.lineTo(x2, yy);
        out.stroke();
    }

    void box(float x, float top, float w, float h, Color fill, Color stroke) throws IOException {
        float yy = PDRectangle.A4.getHeight() - top - h;
        if (fill != null) { out.setNonStrokingColor(fill); out.addRect(x, yy, w, h); out.fill(); }
        if (stroke != null) { out.setStrokingColor(stroke); out.setLineWidth(0.6f); out.addRect(x, yy, w, h); out.stroke(); }
    }

    /** Image scaled to fit the box; returns the drawn height, or 0 when the bytes are no image. */
    float image(byte[] bytes, float x, float top, float maxWidth, float maxHeight, boolean alignRight) throws IOException {
        if (bytes == null || bytes.length == 0) return 0;
        try {
            PDImageXObject image = PDImageXObject.createFromByteArray(doc, bytes, "logo");
            float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
            float w = image.getWidth() * scale, h = image.getHeight() * scale;
            out.drawImage(image, alignRight ? x + maxWidth - w : x, PDRectangle.A4.getHeight() - top - h, w, h);
            return h;
        } catch (IOException | RuntimeException unreadable) {
            return 0;
        }
    }

    /**
     * A table over the full text width. Column widths are given as relative weights; the header repeats on every page; numbers are right aligned
     * where {@code right[i]} is set. Rows with {@code emphasis} (the total) are drawn bold on a shaded background.
     */
    void table(float[] weights, String[] header, List<String[]> rows, boolean[] right, List<Boolean> emphasis) throws IOException {
        float sum = 0;
        for (float w : weights) sum += w;
        float[] widths = new float[weights.length];
        for (int i = 0; i < widths.length; i++) widths[i] = WIDTH * weights[i] / sum;
        final float size = 8.6f, pad = 4, lh = lineHeight(size);
        drawRow(widths, header, null, size, pad, lh, true, null, false, false);
        for (int r = 0; r < rows.size(); r++) {
            boolean total = emphasis != null && emphasis.get(r);
            float height = rowHeight(widths, rows.get(r), size, pad, lh, total);
            if (y + height > PDRectangle.A4.getHeight() - BOTTOM) { newPage(); drawRow(widths, header, null, size, pad, lh, true, null, false, false); }
            drawRow(widths, rows.get(r), right, size, pad, lh, false, total ? SUM : (r % 2 == 1 ? ZEBRA : null), total, true);
        }
    }

    private float rowHeight(float[] widths, String[] cells, float size, float pad, float lh, boolean bold) {
        int lines = 1;
        for (int i = 0; i < cells.length; i++) lines = Math.max(lines, wrap(bold ? BOLD : REGULAR, size, cells[i], widths[i] - 2 * pad).size());
        return lines * lh + 2 * pad;
    }

    private void drawRow(float[] widths, String[] cells, boolean[] right, float size, float pad, float lh, boolean header, Color fill, boolean bold, boolean grid) throws IOException {
        float height = rowHeight(widths, cells, size, pad, lh, header || bold);
        if (!header && y + height > PDRectangle.A4.getHeight() - BOTTOM) newPage();
        float x = LEFT;
        for (int i = 0; i < cells.length; i++) {
            box(x, y, widths[i], height, header ? NAVY : fill, LINE);
            PDFont font = header || bold ? BOLD : REGULAR;
            float ty = y + pad;
            for (String line : wrap(font, size, cells[i], widths[i] - 2 * pad)) {
                ty += size;
                float tx = header ? x + (widths[i] - width(font, size, line)) / 2 : right != null && right[i] ? x + widths[i] - pad - width(font, size, line) : x + pad;
                textAt(tx, ty, font, size, header ? WHITE : INK, line);
                ty += lh - size;
            }
            x += widths[i];
        }
        y += height;
    }

    /** Footer lines and "Page i of n" on every page; call once after everything else is drawn. */
    void footers(List<String> lines) throws IOException {
        out.close();
        out = null;
        int pages = doc.getNumberOfPages();
        for (int i = 0; i < pages; i++) {
            try (var stream = new PDPageContentStream(doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                out = stream;
                float top = PDRectangle.A4.getHeight() - BOTTOM + 22;
                rule(top - 10, LEFT, LEFT + WIDTH, 0.6f, LINE);
                float line = top;
                for (String text : lines) { if (text == null || text.isBlank()) continue; textAt(LEFT, line + 6.5f, REGULAR, 7f, GREY, text); line += 9.5f; }
                textRight(LEFT + WIDTH, line + 6.5f, REGULAR, 7f, GREY, "Page " + (i + 1) + " of " + pages);
            }
        }
        out = null;
    }

    byte[] bytes() throws IOException {
        if (out != null) { out.close(); out = null; }
        try (var buffer = new ByteArrayOutputStream()) { doc.save(buffer); return buffer.toByteArray(); }
    }

    @Override public void close() throws IOException {
        if (out != null) { out.close(); out = null; }
        doc.close();
    }
}
