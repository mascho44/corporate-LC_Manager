package de.ostms.lc.check.service;

import de.ostms.lc.check.service.LcDraftReviewService.Evidence;
import de.ostms.lc.check.service.LcDraftReviewService.Finding;
import de.ostms.lc.check.service.LcDraftReviewService.PageImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Shows the original PDF of a draft with the findings marked in it: the place of each finding (page, line, box) and the pages as pictures. Nothing is stored. */
public final class LcDraftEvidence {
 private record Line(int page, String text, double x, double y, double w, double h) {}
 record Anchor(String text, String tag) {}
 private static final int MAX_PAGES = 30, MAX_IMAGES = 12, DPI = 100;
 private static final Pattern QUOTED = Pattern.compile("„(.+?)“");
 private LcDraftEvidence() {}

 public static LcDraftReviewService.Result attach(LcDraftReviewService.Result result, String text, byte[] pdfBytes) {
  try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
   if (pdf.getNumberOfPages() > MAX_PAGES) return result;
   List<Line> lines = lines(pdf);
   var fields = LcFlowService.fields(text);
   List<Finding> out = new ArrayList<>();
   SortedSet<Integer> marked = new TreeSet<>();
   for (Finding f : result.findings()) {
    List<Evidence> places = new ArrayList<>();
    for (Anchor anchor : anchors(f, fields)) {
     Line line = best(lines, anchor);
     if (line == null) continue;
     places.add(new Evidence(line.page(), shorten(line.text()), line.x(), line.y(), line.w(), line.h()));
     marked.add(line.page());
    }
    out.add(places.isEmpty() ? f : f.withEvidence(places));
   }
   // all pages when the PDF is short, otherwise only the pages that carry a marking
   List<Integer> show = new ArrayList<>();
   if (pdf.getNumberOfPages() <= MAX_IMAGES) for (int p = 1; p <= pdf.getNumberOfPages(); p++) show.add(p);
   else show.addAll(marked.stream().limit(MAX_IMAGES).toList());
   var renderer = new PDFRenderer(pdf);
   List<PageImage> images = new ArrayList<>();
   for (int page : show) { String image = picture(renderer, page); if (image != null) images.add(new PageImage(page, image)); }
   return result.withFindings(out).withPages(images);
  } catch (IOException | RuntimeException unreadable) { return result; }
 }

 /** What to look for in the PDF: the quoted clause of the message, or the field(s) the finding is about. */
 static List<Anchor> anchors(Finding f, Map<String, String> fields) {
  List<Anchor> out = new ArrayList<>();
  Matcher quoted = QUOTED.matcher(f.message() == null ? "" : f.message());
  String clause = null;
  while (quoted.find()) clause = quoted.group(1); // the clause is the last quotation of the message
  if (clause != null) { out.add(new Anchor(clause.replaceFirst("^[+\\-*\\s]+", ""), null)); return out; }
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
   String first = "";
   for (String l : value.split("\\R")) if (l.trim().length() >= 4) { first = l.trim(); break; }
   out.add(new Anchor(first, tag));
  }
  return out;
 }

 private static Set<String> tokens(String s) {
  Set<String> t = new LinkedHashSet<>();
  for (String x : s.toUpperCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (!x.isBlank()) t.add(x);
  return t;
 }

 /** The line that shares most words with the anchor (at least 70 %); failing that, the line that starts with the field tag. */
 static Line best(List<Line> lines, Anchor anchor) {
  if (anchor == null) return null;
  Line best = null; double score = 0;
  Set<String> want = tokens(anchor.text());
  if (anchor.text().trim().length() >= 4 && !want.isEmpty()) {
   for (Line l : lines) {
    Set<String> have = tokens(l.text());
    double s = (double) want.stream().filter(have::contains).count() / want.size();
    if (s > score) { score = s; best = l; }
   }
   if (score >= 0.7) return best;
  }
  if (anchor.tag() != null) {
   Pattern start = Pattern.compile("^\\s*:?" + Pattern.quote(anchor.tag()) + ":?(\\s|$)", Pattern.CASE_INSENSITIVE);
   for (Line l : lines) if (start.matcher(l.text()).find()) return l;
  }
  return null;
 }

 private static String shorten(String s) { String t = s.replaceAll("\\s+", " ").trim(); return t.length() > 220 ? t.substring(0, 220) + " …" : t; }

 private static String picture(PDFRenderer renderer, int page) {
  try {
   BufferedImage image = renderer.renderImageWithDPI(page - 1, DPI, ImageType.RGB);
   var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
   var param = writer.getDefaultWriteParam();
   param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); param.setCompressionQuality(0.75f);
   var bytes = new ByteArrayOutputStream();
   try (var out = ImageIO.createImageOutputStream(bytes)) { writer.setOutput(out); writer.write(null, new IIOImage(image, null, null), param); }
   writer.dispose();
   return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
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
