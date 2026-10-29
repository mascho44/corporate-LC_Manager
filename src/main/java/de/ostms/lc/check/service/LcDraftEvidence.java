package de.ostms.lc.check.service;

import de.ostms.lc.check.service.LcDraftReviewService.Evidence;
import de.ostms.lc.check.service.LcDraftReviewService.Finding;
import de.ostms.lc.check.service.LcDraftReviewService.PageImage;
import de.ostms.lc.document.service.OcrEvidence;
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

 public static LcDraftReviewService.Result attach(LcDraftReviewService.Result result, String text, byte[] pdfBytes) { return attach(result, text, pdfBytes, null); }

 /** With the OCR words of a scanned PDF (pixels at the OCR resolution), the lines are taken from them as well as from the text layer. */
 public static LcDraftReviewService.Result attach(LcDraftReviewService.Result result, String text, byte[] pdfBytes, OcrEvidence ocr) {
  try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
   if (pdf.getNumberOfPages() > MAX_PAGES) return result;
   List<Line> lines = new ArrayList<>(lines(pdf));
   lines.addAll(ocrLines(pdf, ocr));
   var fields = LcFlowService.fields(text);
   SortedSet<Integer> marked = new TreeSet<>();
   List<Finding> out = new ArrayList<>(), ticks = new ArrayList<>();
   for (Finding f : result.findings()) out.add(locate(f, anchors(f, fields), lines, marked));
   for (Finding f : result.passed()) ticks.add(locate(f, passedAnchors(f.code(), fields), lines, marked));
   // all pages when the PDF is short, otherwise only the pages that carry a marking
   List<Integer> show = new ArrayList<>();
   if (pdf.getNumberOfPages() <= MAX_IMAGES) for (int p = 1; p <= pdf.getNumberOfPages(); p++) show.add(p);
   else show.addAll(marked.stream().limit(MAX_IMAGES).toList());
   var renderer = new PDFRenderer(pdf);
   List<PageImage> images = new ArrayList<>();
   for (int page : show) { String image = picture(renderer, page); if (image != null) images.add(new PageImage(page, image)); }
   return result.withFindings(out).withPassed(ticks).withPages(images);
  } catch (IOException | RuntimeException unreadable) { return result; }
 }

 private static Finding locate(Finding f, List<Anchor> anchors, List<Line> lines, SortedSet<Integer> marked) {
  List<Evidence> places = new ArrayList<>();
  for (Anchor anchor : anchors) {
   Line line = best(lines, anchor);
   if (line == null) continue;
   places.add(new Evidence(line.page(), shorten(line.text()), line.x(), line.y(), line.w(), line.h()));
   marked.add(line.page());
  }
  return places.isEmpty() ? f : f.withEvidence(places);
 }

 /** The field(s) a met check is about, so it can be ticked in the PDF. */
 static List<Anchor> passedAnchors(String code, Map<String, String> fields) {
  String[] tags = switch (code) {
   case "DRAFT_OK_41" -> new String[] {fields.containsKey("41A") ? "41A" : "41D"};
   case "DRAFT_OK_SHIPMENT" -> new String[] {fields.containsKey("44C") ? "44C" : "44D"};
   case "DRAFT_OK_IRREVOCABLE" -> new String[] {"40A"};
   case "DRAFT_OK_WINDOW" -> new String[] {"31D", "44C"};
   case "DRAFT_OK_PRESENTATION" -> new String[] {"48"};
   case "DRAFT_OK_APPLICANT", "DRAFT_OK_VAGUE" -> new String[] {"46A", "47A"};
   default -> code.startsWith("DRAFT_OK_") ? new String[] {code.substring("DRAFT_OK_".length())} : new String[] {};
  };
  List<Anchor> out = new ArrayList<>();
  for (String tag : tags) {
   String value = fields.get(tag);
   if (value == null) continue;
   String first = "";
   for (String l : value.split("\\R")) if (l.trim().length() >= 4) { first = l.trim(); break; }
   out.add(new Anchor(first, tag));
  }
  return out;
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
  if (anchor.tag() != null) {
   Pattern start = Pattern.compile("^\\s*:?" + Pattern.quote(anchor.tag()) + "(:|\\s|$)", Pattern.CASE_INSENSITIVE);
   for (Line l : lines) if (start.matcher(l.text()).find()) return l;
  }
  Set<String> want = tokens(anchor.text());
  if (anchor.text().trim().length() < 4 || want.isEmpty()) return null;
  Line best = null; double score = 0;
  for (Line l : lines) {
   Set<String> have = tokens(l.text());
   double s = (double) want.stream().filter(have::contains).count() / want.size();
   if (s > score) { score = s; best = l; }
  }
  return score >= 0.65 ? best : null;
 }

 /** Lines from the OCR words of scanned pages (not for pages the OCR had to straighten). */
 private static List<Line> ocrLines(PDDocument pdf, OcrEvidence ocr) {
  List<Line> out = new ArrayList<>();
  if (ocr == null || ocr.words().isEmpty() || ocr.dpi() <= 0) return out;
  Set<Integer> straightened = new HashSet<>();
  if (ocr.pages() != null) for (var p : ocr.pages()) if (Math.abs(p.correctionDegrees()) > 0.01) straightened.add(p.page());
  Map<Integer, List<OcrEvidence.Word>> byPage = new TreeMap<>();
  for (var w : ocr.words()) if (!straightened.contains(w.page()) && w.page() >= 1 && w.page() <= pdf.getNumberOfPages()) byPage.computeIfAbsent(w.page(), k -> new ArrayList<>()).add(w);
  double scale = ocr.dpi() / 72.0;
  byPage.forEach((page, words) -> {
   var box = pdf.getPage(page - 1).getCropBox();
   double pw = box.getWidth() * scale, ph = box.getHeight() * scale;
   words.sort(Comparator.comparingDouble(w -> w.top() + w.height() / 2.0));
   List<OcrEvidence.Word> current = new ArrayList<>();
   for (var w : words) {
    if (!current.isEmpty()) {
     var last = current.get(current.size() - 1);
     if (Math.abs((w.top() + w.height() / 2.0) - (last.top() + last.height() / 2.0)) > Math.max(4, last.height() / 2.0)) { out.add(ocrLine(page, current, pw, ph)); current = new ArrayList<>(); }
    }
    current.add(w);
   }
   if (!current.isEmpty()) out.add(ocrLine(page, current, pw, ph));
  });
  return out;
 }

 private static Line ocrLine(int page, List<OcrEvidence.Word> words, double pw, double ph) {
  int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = 0, y1 = 0; StringBuilder text = new StringBuilder();
  for (var w : words.stream().sorted(Comparator.comparingInt(OcrEvidence.Word::left)).toList()) {
   if (text.length() > 0) text.append(' ');
   text.append(w.text());
   x0 = Math.min(x0, w.left()); y0 = Math.min(y0, w.top()); x1 = Math.max(x1, w.left() + w.width()); y1 = Math.max(y1, w.top() + w.height());
  }
  double px = 0.006, py = 0.004, left = Math.max(0, x0 / pw - px), top = Math.max(0, y0 / ph - py);
  return new Line(page, text.toString(), left, top, Math.min(1 - left, (x1 - x0) / pw + 2 * px), Math.min(1 - top, (y1 - y0) / ph + 2 * py));
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
