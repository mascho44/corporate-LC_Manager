package de.ostms.lc.check.service;

import de.ostms.lc.check.service.LcDraftReviewService.Evidence;
import de.ostms.lc.check.service.LcDraftReviewService.Finding;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Points the findings of a draft check to the place in the PDF: page, the line as printed and a small picture of that line with a marking. Only for PDFs with a text layer; nothing is stored. */
public final class LcDraftEvidence {
 private record Line(int page, String text, double x, double y, double w, double h) {}
 private static final int MAX_PAGES = 30, MAX_IMAGES = 12, DPI = 110;
 private static final Pattern QUOTED = Pattern.compile("„(.+?)“");
 private LcDraftEvidence() {}

 public static LcDraftReviewService.Result attach(LcDraftReviewService.Result result, String text, byte[] pdfBytes) {
  try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
   if (pdf.getNumberOfPages() > MAX_PAGES) return result;
   List<Line> lines = lines(pdf);
   if (lines.isEmpty()) return result;
   var fields = LcFlowService.fields(text);
   var renderer = new PDFRenderer(pdf);
   Map<Integer, BufferedImage> pages = new HashMap<>();
   int images = 0;
   List<Finding> out = new ArrayList<>();
   for (Finding f : result.findings()) {
    List<Evidence> places = new ArrayList<>();
    for (String anchor : anchors(f, fields)) {
     Line line = best(lines, anchor);
     if (line == null) continue;
     String image = images < MAX_IMAGES ? picture(renderer, pages, line) : null;
     if (image != null) images++;
     places.add(new Evidence(line.page(), shorten(line.text()), image));
    }
    out.add(places.isEmpty() ? f : f.withEvidence(places));
   }
   return result.withFindings(out);
  } catch (IOException | RuntimeException unreadable) { return result; }
 }

 /** What to look for in the PDF: the quoted clause of the message, or the field(s) the finding is about. */
 static List<String> anchors(Finding f, Map<String, String> fields) {
  List<String> out = new ArrayList<>();
  Matcher quoted = QUOTED.matcher(f.message() == null ? "" : f.message());
  String clause = null;
  while (quoted.find()) clause = quoted.group(1); // the clause is the last quotation of the message
  if (clause != null) { out.add(clause.replaceFirst("^[+\\-*\\s]+", "")); return out; }
  String[] tags = switch (f.code()) {
   case "DRAFT_EXPIRY_BEFORE_SHIPMENT", "DRAFT_SHORT_WINDOW" -> new String[] {"31D", "44C"};
   case "DRAFT_EXPIRED" -> new String[] {"31D"};
   case "DRAFT_SHIPMENT_PAST" -> new String[] {"44C"};
   case "DRAFT_SHORT_PRESENTATION" -> new String[] {"48"};
   case "DRAFT_REVOCABLE" -> new String[] {"40A"};
   case "DRAFT_CHARGES" -> new String[] {"71D"};
   case "DRAFT_AMOUNT" -> new String[] {"32B"};
   case "DRAFT_ISSUE_AFTER_EXPIRY" -> new String[] {"31C", "31D"};
   default -> new String[] {};
  };
  for (String tag : tags) {
   String value = fields.get(tag);
   if (value == null) continue;
   for (String l : value.split("\\R")) if (l.trim().length() >= 4) { out.add(l.trim()); break; }
  }
  return out;
 }

 private static Set<String> tokens(String s) {
  Set<String> t = new LinkedHashSet<>();
  for (String x : s.toUpperCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (!x.isBlank()) t.add(x);
  return t;
 }

 /** The line that shares most of the words with the anchor (at least 70 %, at least four characters). */
 static Line best(List<Line> lines, String anchor) {
  if (anchor == null || anchor.trim().length() < 4) return null;
  Set<String> want = tokens(anchor);
  if (want.isEmpty()) return null;
  Line best = null; double score = 0;
  for (Line l : lines) {
   Set<String> have = tokens(l.text());
   long hit = want.stream().filter(have::contains).count();
   double s = (double) hit / want.size();
   if (s > score) { score = s; best = l; }
  }
  return score >= 0.7 ? best : null;
 }

 private static String shorten(String s) { String t = s.replaceAll("\\s+", " ").trim(); return t.length() > 220 ? t.substring(0, 220) + " …" : t; }

 private static String picture(PDFRenderer renderer, Map<Integer, BufferedImage> pages, Line line) {
  try {
   BufferedImage page = pages.get(line.page());
   if (page == null) { page = renderer.renderImageWithDPI(line.page() - 1, DPI); pages.put(line.page(), page); }
   int w = page.getWidth(), h = page.getHeight();
   int top = (int) Math.max(0, (line.y() - 0.045) * h), bottom = (int) Math.min(h, (line.y() + line.h() + 0.045) * h);
   if (bottom - top < 20) return null;
   BufferedImage crop = new BufferedImage(w, bottom - top, BufferedImage.TYPE_INT_RGB);
   var g = crop.createGraphics();
   g.drawImage(page, 0, -top, null);
   int bx = (int) (line.x() * w), by = (int) (line.y() * h) - top, bw = (int) (line.w() * w), bh = (int) (line.h() * h);
   g.setColor(new Color(255, 214, 0, 90)); g.fillRect(bx, by, bw, bh);
   g.setColor(new Color(235, 150, 0)); g.setStroke(new BasicStroke(2f)); g.drawRect(bx, by, bw, bh);
   g.dispose();
   var png = new ByteArrayOutputStream();
   ImageIO.write(crop, "png", png);
   return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
  } catch (IOException | RuntimeException e) { return null; }
 }

 /** Text lines of the PDF with their position as fractions of the page (origin top left). */
 private static List<Line> lines(PDDocument pdf) throws IOException {
  List<Line> out = new ArrayList<>();
  var stripper = new PDFTextStripper() {
   int page; double pw, ph; final List<TextPosition> line = new ArrayList<>(); final StringBuilder text = new StringBuilder();
   @Override protected void startPage(org.apache.pdfbox.pdmodel.PDPage p) { var b = p.getCropBox(); pw = b.getWidth(); ph = b.getHeight(); page = getCurrentPageNo(); }
   @Override protected void writeString(String s, List<TextPosition> positions) { if (text.length() > 0) text.append(' '); text.append(s); line.addAll(positions); }
   @Override protected void writeLineSeparator() { flush(); }
   @Override protected void endPage(org.apache.pdfbox.pdmodel.PDPage p) { flush(); }
   void flush() {
    if (!line.isEmpty() && pw > 0 && ph > 0) {
     double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = 0, y1 = 0;
     for (var t : line) { x0 = Math.min(x0, t.getXDirAdj()); x1 = Math.max(x1, t.getXDirAdj() + t.getWidthDirAdj()); y0 = Math.min(y0, t.getYDirAdj() - t.getHeightDir()); y1 = Math.max(y1, t.getYDirAdj()); }
     double px = 0.006, py = 0.004, left = Math.max(0, x0 / pw - px), top = Math.max(0, y0 / ph - py);
     out.add(new Line(page, text.toString(), left, top, Math.min(1 - left, (x1 - x0) / pw + 2 * px), Math.min(1 - top, (y1 - y0) / ph + 2 * py)));
    }
    line.clear(); text.setLength(0);
   }
  };
  stripper.setSortByPosition(true);
  stripper.getText(pdf);
  return out;
 }
}
