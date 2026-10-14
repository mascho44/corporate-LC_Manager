package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Document types learned from human confirmations: each confirmed page or document stores its distinctive words; a new text is compared with them
 * (overlap of the word sets). A match is only ever a suggestion for review, never a silent decision, and a single clear type must win.
 */
@Service
public class DocumentTypeTrainingService {
 public record Match(DocumentType type, double similarity, int examples) {}
 public record Example(UUID id, DocumentType type, String sample, String confirmedBy, OffsetDateTime confirmedAt) {}
 static final int MIN_WORDS = 8, MAX_WORDS = 150, MAX_EXAMPLES_PER_TYPE = 60;
 static final double THRESHOLD = 0.6, MARGIN = 0.08, DUPLICATE = 0.95;

 private final JdbcTemplate jdbc;
 public DocumentTypeTrainingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

 /** Distinct lower-case words of three letters or more, numbers and punctuation ignored, in order of first occurrence, bounded. */
 static LinkedHashSet<String> words(String text) {
  var out = new LinkedHashSet<String>();
  if (text == null) return out;
  var matcher = java.util.regex.Pattern.compile("\\p{L}{3,}").matcher(text);
  while (matcher.find() && out.size() < MAX_WORDS) out.add(matcher.group().toLowerCase(Locale.ROOT));
  return out;
 }
 /** Overlap of two word sets: shared words divided by the geometric mean of both sizes (1 = same words). */
 static double similarity(Set<String> a, Set<String> b) {
  if (a.isEmpty() || b.isEmpty()) return 0;
  long shared = a.stream().filter(b::contains).count();
  return shared / Math.sqrt((double) a.size() * b.size());
 }
 private static String sample(String text) {
  String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
  return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
 }

 /** Stores the text as an example of the type; too little text, the unspecific type OTHER and near-duplicates are skipped. Returns whether it was stored. */
 @Transactional
 public boolean learn(String text, DocumentType type, String actor) {
  if (type == null || type == DocumentType.OTHER) return false;
  var terms = words(text);
  if (terms.size() < MIN_WORDS) return false;
  UUID tenant = TenantContext.currentId();
  var existing = jdbc.queryForList("select terms from document_type_example where tenant_id=? and document_type=?", String.class, tenant, type.name());
  for (String known : existing) if (similarity(terms, new HashSet<>(Arrays.asList(known.split(" ")))) >= DUPLICATE) return false;
  if (existing.size() >= MAX_EXAMPLES_PER_TYPE) return false;
  jdbc.update("insert into document_type_example(id,tenant_id,document_type,terms,sample,confirmed_by) values (?,?,?,?,?,?)",
   UUID.randomUUID(), tenant, type.name(), String.join(" ", terms), sample(text), actor == null ? "unknown" : actor);
  return true;
 }

 /** The one type whose examples resemble the text clearly (and clearly more than any other type); empty otherwise. */
 @Transactional(readOnly = true)
 public Optional<Match> match(String text) {
  var terms = words(text);
  if (terms.size() < MIN_WORDS) return Optional.empty();
  var best = new HashMap<String, double[]>();
  for (var row : jdbc.queryForList("select document_type, terms from document_type_example where tenant_id=?", TenantContext.currentId())) {
   double score = similarity(terms, new HashSet<>(Arrays.asList(((String) row.get("terms")).split(" "))));
   var entry = best.computeIfAbsent((String) row.get("document_type"), k -> new double[]{0, 0});
   entry[0] = Math.max(entry[0], score); entry[1]++;
  }
  var ranked = best.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0])).toList();
  if (ranked.isEmpty() || ranked.get(0).getValue()[0] < THRESHOLD) return Optional.empty();
  if (ranked.size() > 1 && ranked.get(0).getValue()[0] - ranked.get(1).getValue()[0] < MARGIN) return Optional.empty();
  var top = ranked.get(0);
  try { return Optional.of(new Match(DocumentType.valueOf(top.getKey()), top.getValue()[0], (int) top.getValue()[1])); }
  catch (IllegalArgumentException unknownType) { return Optional.empty(); }
 }

 /**
  * Fills in the types of parts the fixed headings could not recognise (type OTHER) from the learned examples; the page results say where the type came from.
  * Parts with a recognised type are left alone.
  */
 @Transactional(readOnly = true)
 public PdfDocumentSplitter.Proposal apply(PdfDocumentSplitter.Proposal baseline, List<String> pageTexts) {
  var parts = new ArrayList<PdfDocumentSplitter.Part>();var pages = new ArrayList<>(baseline.pages());boolean changed = false;
  for (var part : baseline.parts()) {
   if (part.documentType() != DocumentType.OTHER || part.toPage() > pageTexts.size()) { parts.add(part); continue; }
   var match = match(String.join("\n", pageTexts.subList(part.fromPage() - 1, part.toPage())));
   if (match.isEmpty()) { parts.add(part); continue; }
   changed = true;
   parts.add(new PdfDocumentSplitter.Part(part.fromPage(), part.toPage(), match.get().type(), part.copyNumber()));
   String evidence = "Aus Training erkannt (Ähnlichkeit " + Math.round(match.get().similarity() * 100) + " % zu " + match.get().examples() + " bestätigten Beispiel(en)) – bitte prüfen";
   for (int number = part.fromPage(); number <= part.toPage(); number++) {
    int index = number - 1; var old = pages.get(index);
    pages.set(index, new PdfDocumentSplitter.Page(old.number(), new DocumentClassifier.Classification(match.get().type(), .8, "REVIEW", "TRAINED_TYPE_V1", List.of(evidence)), old.textSource(), old.copyHint()));
   }
  }
  return changed ? new PdfDocumentSplitter.Proposal(baseline.pageCount(), List.copyOf(pages), List.copyOf(parts)) : baseline;
 }

 @Transactional(readOnly = true)
 public List<Example> examples() {
  var out = new ArrayList<Example>();
  for (var row : jdbc.queryForList("select id, document_type, sample, confirmed_by, confirmed_at from document_type_example where tenant_id=? order by document_type, confirmed_at desc", TenantContext.currentId())) {
   try { out.add(new Example((UUID) row.get("id"), DocumentType.valueOf((String) row.get("document_type")), (String) row.get("sample"), (String) row.get("confirmed_by"),
    at(row.get("confirmed_at")))); } catch (IllegalArgumentException | ClassCastException skip) { /* unknown type or timestamp shape: not listed */ }
  }
  return out;
 }
 private static OffsetDateTime at(Object value) {
  if (value instanceof OffsetDateTime offset) return offset;
  if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
  throw new ClassCastException();
 }
 @Transactional
 public boolean delete(UUID id) { return jdbc.update("delete from document_type_example where id=? and tenant_id=?", id, TenantContext.currentId()) > 0; }
}
