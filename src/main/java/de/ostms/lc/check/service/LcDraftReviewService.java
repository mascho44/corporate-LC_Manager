package de.ostms.lc.check.service;

import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.swift.Mt700Parser;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/**
 * Checks the draft of a credit that a third party (usually the issuing bank) sends before the credit is issued: are the mandatory fields there, are the dates and amounts plausible,
 * and does the text contain clauses that are typical traps for the beneficiary. No comparison with a contract; nothing is stored.
 */
@Service
public class LcDraftReviewService {
 /** A place in the PDF of the draft: page, the line as printed and its box on the page (fractions 0..1, origin top left). */
 public record Evidence(int page, String quote, double x, double y, double w, double h) {}
 /** A page of the PDF as a picture (data URI), so the report can show the original with the findings marked in it. */
 public record PageImage(int page, String image) {}
 public record Finding(String severity, String code, String message, String advice, String messageEn, String adviceEn, List<Evidence> evidence) {
  public Finding(String severity, String code, String message, String advice, String messageEn, String adviceEn) { this(severity, code, message, advice, messageEn, adviceEn, List.of()); }
  public Finding withEvidence(List<Evidence> places) { return new Finding(severity, code, message, advice, messageEn, adviceEn, places); }
 }
 public record Result(String reference, String currency, java.math.BigDecimal amount, LocalDate expiryDate, LocalDate latestShipmentDate, int presentationDays,
                      long discrepancies, long warnings, List<Finding> findings, List<Finding> passed, List<PageImage> pages) {
  public Result withFindings(List<Finding> changed) { return new Result(reference, currency, amount, expiryDate, latestShipmentDate, presentationDays, discrepancies, warnings, changed, passed, pages); }
  public Result withPassed(List<Finding> changed) { return new Result(reference, currency, amount, expiryDate, latestShipmentDate, presentationDays, discrepancies, warnings, findings, changed, pages); }
  public Result withPages(List<PageImage> shown) { return new Result(reference, currency, amount, expiryDate, latestShipmentDate, presentationDays, discrepancies, warnings, findings, passed, shown); }
 }
 static final int DEFAULT_PRESENTATION_DAYS = 21;
 private static final Pattern FIELD = Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)", Pattern.DOTALL);
 private static final Pattern ON_APPLICANT = Pattern.compile("(signed|issued|countersigned|approved|authori[sz]ed|released|confirmed|certified|stamped)\\s+by\\s+(the\\s+)?(applicant|buyer|importer)|(applicant|buyer|importer)'?s?\\s+(approval|signature|acceptance|release|inspection|authori[sz]ation)", Pattern.CASE_INSENSITIVE);
 private static final Pattern VAGUE = Pattern.compile("to be advised|will be advised|as per contract|as agreed|to follow|to be agreed|subject to (the )?(applicant|buyer)", Pattern.CASE_INSENSITIVE);
 private static final Pattern LEGALISED = Pattern.compile("legali[sz]ed|legali[sz]ation|consular|embassy|apostille", Pattern.CASE_INSENSITIVE);
 private static final Pattern SHORT_DAYS = Pattern.compile("within\\s+(\\d{1,2})\\s+days", Pattern.CASE_INSENSITIVE);
 private final Mt700Parser parser;
 public LcDraftReviewService(Mt700Parser parser) { this.parser = parser; }

 public Result check(String text, LocalDate today) {
  if (text == null || text.isBlank()) throw new IllegalArgumentException("Bitte den Entwurf einfügen oder eine Datei wählen.");
  Map<String, String> fields = new LinkedHashMap<>();
  Matcher m = FIELD.matcher(text.strip());
  while (m.find()) fields.put(m.group(1), m.group(2).trim());
  if (!fields.containsKey("20") && !fields.containsKey("32B")) throw new IllegalArgumentException("Im Text wurde kein Akkreditiv im MT700-Aufbau erkannt (Felder wie :20: und :32B:).");
  List<Finding> out = new ArrayList<>();
  LetterOfCredit lc;
  try { lc = parser.parse(text); } catch (RuntimeException e) { lc = null; out.add(new Finding("DISCREPANCY", "DRAFT_UNREADABLE", "Der Entwurf konnte nicht vollständig gelesen werden: " + String.valueOf(e.getMessage()), "Formate der Felder :31D:, :32B:, :44C: im Entwurf prüfen.", "The draft could not be read completely: " + e.getMessage(), "Check the formats of fields :31D:, :32B: and :44C: in the draft.")); }

  required(out, fields, "20", "Referenz (:20:)", "Reference (:20:)");
  required(out, fields, "40A", "Form des Akkreditivs (:40A:)", "Form of credit (:40A:)");
  required(out, fields, "31D", "Verfalldatum und -ort (:31D:)", "Expiry date and place (:31D:)");
  required(out, fields, "32B", "Währung und Betrag (:32B:)", "Currency and amount (:32B:)");
  required(out, fields, "50", "Auftraggeber (:50:)", "Applicant (:50:)");
  required(out, fields, "59", "Begünstigter (:59:)", "Beneficiary (:59:)");
  required(out, fields, "45A", "Waren (:45A:)", "Goods (:45A:)");
  required(out, fields, "46A", "Geforderte Dokumente (:46A:)", "Required documents (:46A:)");
  if (!fields.containsKey("41A") && !fields.containsKey("41D")) out.add(finding("DISCREPANCY", "DRAFT_MISSING_41", "Es fehlt die Angabe, bei wem das Akkreditiv benutzbar ist (:41a:).", "Von der Bank ergänzen lassen.", "The field stating where the credit is available (:41a:) is missing.", "Ask the bank to add it."));
  if (!fields.containsKey("44C") && !fields.containsKey("44D")) out.add(finding("WARNING", "DRAFT_NO_SHIPMENT_DATE", "Es ist weder ein spätester Verladetag (:44C:) noch ein Verladezeitraum (:44D:) genannt.", "Klären, bis wann verladen werden muss.", "Neither a latest shipment date (:44C:) nor a shipment period (:44D:) is stated.", "Clarify by when the goods must be shipped."));
  if (!fields.containsKey("48")) out.add(finding("INFO", "DRAFT_NO_PRESENTATION_PERIOD", "Eine Vorlagefrist (:48:) fehlt; es gelten 21 Tage nach Verladung (UCP 600).", "Prüfen, ob 21 Tage für Ihre Abläufe reichen.", "No presentation period (:48:) is stated; 21 days after shipment apply (UCP 600).", "Check whether 21 days are enough for your processes."));

  String form = fields.getOrDefault("40A", "").toUpperCase(Locale.ROOT);
  if (form.contains("REVOCABLE") && !form.contains("IRREVOCABLE")) out.add(finding("DISCREPANCY", "DRAFT_REVOCABLE", "Das Akkreditiv ist als widerruflich (REVOCABLE) bezeichnet; nach UCP 600 sind Akkreditive unwiderruflich.", "Berichtigung verlangen.", "The credit is marked REVOCABLE; under UCP 600 credits are irrevocable.", "Ask for a correction."));

  int days = presentationDays(fields.get("48"));
  if (lc != null) {
   if (lc.getAmount() != null && lc.getAmount().signum() <= 0) out.add(finding("DISCREPANCY", "DRAFT_AMOUNT", "Der Betrag ist nicht positiv.", "Betrag in :32B: prüfen.", "The amount is not positive.", "Check the amount in :32B:."));
   LocalDate expiry = lc.getExpiryDate(), ship = lc.getLatestShipmentDate(), issue = lc.getIssueDate();
   if (expiry != null && expiry.isBefore(today)) out.add(finding("DISCREPANCY", "DRAFT_EXPIRED", "Das Verfalldatum " + expiry + " liegt in der Vergangenheit.", "Neues Verfalldatum verlangen.", "The expiry date " + expiry + " is in the past.", "Ask for a new expiry date."));
   if (ship != null && ship.isBefore(today)) out.add(finding("DISCREPANCY", "DRAFT_SHIPMENT_PAST", "Der späteste Verladetag " + ship + " liegt in der Vergangenheit.", "Neuen Verladetermin verlangen.", "The latest shipment date " + ship + " is in the past.", "Ask for a new shipment date."));
   if (expiry != null && ship != null) {
    if (expiry.isBefore(ship)) out.add(finding("DISCREPANCY", "DRAFT_EXPIRY_BEFORE_SHIPMENT", "Das Akkreditiv verfällt (" + expiry + ") vor dem spätesten Verladetag (" + ship + "): Dokumente könnten nie rechtzeitig vorgelegt werden.", "Verfalldatum oder Verladetag ändern lassen.", "The credit expires (" + expiry + ") before the latest shipment date (" + ship + "): documents could never be presented in time.", "Have the expiry or shipment date changed."));
    else if (java.time.temporal.ChronoUnit.DAYS.between(ship, expiry) < days) out.add(finding("WARNING", "DRAFT_SHORT_WINDOW", "Zwischen spätestem Verladetag (" + ship + ") und Verfall (" + expiry + ") liegen weniger Tage als die Vorlagefrist von " + days + " Tagen; bei später Verladung bleibt zu wenig Zeit.", "Verfalldatum später setzen lassen.", "Fewer days lie between the latest shipment date (" + ship + ") and expiry (" + expiry + ") than the presentation period of " + days + " days.", "Ask for a later expiry date."));
   }
   if (issue != null && expiry != null && issue.isAfter(expiry)) out.add(finding("DISCREPANCY", "DRAFT_ISSUE_AFTER_EXPIRY", "Das Ausstellungsdatum (" + issue + ") liegt nach dem Verfalldatum (" + expiry + ").", "Daten prüfen.", "The issue date (" + issue + ") is after the expiry date (" + expiry + ").", "Check the dates."));
  }
  if (days > 0 && days < 10) out.add(finding("WARNING", "DRAFT_SHORT_PRESENTATION", "Die Vorlagefrist von " + days + " Tagen ist sehr kurz (üblich sind 21).", "Verlängerung verlangen.", "A presentation period of " + days + " days is very short (21 is usual).", "Ask for an extension."));
  if (fields.containsKey("71D") && fields.get("71D").toUpperCase(Locale.ROOT).contains("BENEFICIARY")) out.add(finding("INFO", "DRAFT_CHARGES", "Gebühren gehen laut :71D: ganz oder teilweise zu Lasten des Begünstigten: " + oneLine(fields.get("71D")), "Mit dem Vertrag abgleichen.", "Charges are for the beneficiary's account according to :71D: in whole or in part: " + oneLine(fields.get("71D")), "Compare with the contract."));

  scan(out, "46A", fields.get("46A"));
  scan(out, "47A", fields.get("47A"));
  long d = out.stream().filter(f -> "DISCREPANCY".equals(f.severity())).count(), w = out.stream().filter(f -> "WARNING".equals(f.severity())).count();
  return new Result(lc == null ? fields.get("20") : lc.getReference(), lc == null ? null : lc.getCurrency(), lc == null ? null : lc.getAmount(), lc == null ? null : lc.getExpiryDate(), lc == null ? null : lc.getLatestShipmentDate(), days, d, w, out, passed(out, fields, lc, days), List.of());
 }

 private static void required(List<Finding> out, Map<String, String> fields, String tag, String de, String en) {
  if (fields.get(tag) == null || fields.get(tag).isBlank()) out.add(finding("DISCREPANCY", "DRAFT_MISSING_" + tag, "Pflichtangabe fehlt: " + de + ".", "Von der Bank ergänzen lassen.", "Mandatory field missing: " + en + ".", "Ask the bank to add it."));
 }
 /** The checks that found nothing wrong, so the report can show a tick next to each of them (severity OK). */
 private static List<Finding> passed(List<Finding> problems, Map<String, String> fields, LetterOfCredit lc, int days) {
  Set<String> hit = new HashSet<>(); for (Finding f : problems) hit.add(f.code());
  List<Finding> ok = new ArrayList<>();
  String[][] mandatory = {{"20", "Referenz (:20:) ist angegeben", "Reference (:20:) is stated"}, {"40A", "Form des Akkreditivs (:40A:) ist angegeben", "Form of credit (:40A:) is stated"}, {"31D", "Verfalldatum und -ort (:31D:) sind angegeben", "Expiry date and place (:31D:) are stated"},
   {"32B", "Währung und Betrag (:32B:) sind angegeben", "Currency and amount (:32B:) are stated"}, {"50", "Auftraggeber (:50:) ist angegeben", "Applicant (:50:) is stated"}, {"59", "Begünstigter (:59:) ist angegeben", "Beneficiary (:59:) is stated"},
   {"45A", "Waren (:45A:) sind beschrieben", "Goods (:45A:) are described"}, {"46A", "Geforderte Dokumente (:46A:) sind aufgeführt", "Required documents (:46A:) are listed"}};
  for (String[] m : mandatory) if (!hit.contains("DRAFT_MISSING_" + m[0])) ok.add(finding("OK", "DRAFT_OK_" + m[0], m[1], null, m[2], null));
  if (!hit.contains("DRAFT_MISSING_41")) ok.add(finding("OK", "DRAFT_OK_41", "Benutzbar bei (:41a:) ist angegeben", null, "Available with (:41a:) is stated", null));
  if (!hit.contains("DRAFT_NO_SHIPMENT_DATE")) ok.add(finding("OK", "DRAFT_OK_SHIPMENT", "Verladetag oder Verladezeitraum ist genannt", null, "Latest shipment date or period is stated", null));
  if (fields.containsKey("40A") && !hit.contains("DRAFT_REVOCABLE")) ok.add(finding("OK", "DRAFT_OK_IRREVOCABLE", "Das Akkreditiv ist unwiderruflich", null, "The credit is irrevocable", null));
  if (lc != null && lc.getExpiryDate() != null && lc.getLatestShipmentDate() != null && !hit.contains("DRAFT_EXPIRY_BEFORE_SHIPMENT") && !hit.contains("DRAFT_SHORT_WINDOW"))
   ok.add(finding("OK", "DRAFT_OK_WINDOW", "Verfall und Verladetag passen zusammen, genug Zeit zur Vorlage", null, "Expiry and shipment date fit together with enough time to present", null));
  if (fields.containsKey("48") && !hit.contains("DRAFT_SHORT_PRESENTATION")) ok.add(finding("OK", "DRAFT_OK_PRESENTATION", "Vorlagefrist von " + days + " Tagen ist ausreichend", null, "Presentation period of " + days + " days is sufficient", null));
  boolean text = fields.containsKey("46A") || fields.containsKey("47A");
  if (text && !hit.contains("DRAFT_APPLICANT_DEPENDENT")) ok.add(finding("OK", "DRAFT_OK_APPLICANT", "Keine Bedingung hängt vom Auftraggeber ab", null, "No condition depends on the applicant", null));
  if (text && !hit.contains("DRAFT_VAGUE")) ok.add(finding("OK", "DRAFT_OK_VAGUE", "Keine unbestimmten Bedingungen", null, "No vague conditions", null));
  return ok;
 }
 private static String oneLine(String s) { return s.replaceAll("\\s+", " ").trim(); }
 static int presentationDays(String field48) {
  if (field48 == null) return DEFAULT_PRESENTATION_DAYS;
  Matcher m = Pattern.compile("(\\d{1,3})").matcher(field48);
  return m.find() ? Integer.parseInt(m.group(1)) : DEFAULT_PRESENTATION_DAYS;
 }
 private static Finding finding(String sev, String code, String de, String adviceDe, String en, String adviceEn) { return new Finding(sev, code, de, adviceDe, en, adviceEn); }

 /** Risk clauses in the free text of :46A: and :47A: (one hint per clause line). */
 private static void scan(List<Finding> out, String tag, String text) {
  if (text == null) return;
  for (String line : text.split("\\R")) {
   String clause = line.trim(); if (clause.length() < 8) continue;
   String shown = clause.length() > 160 ? clause.substring(0, 160) + " …" : clause;
   if (ON_APPLICANT.matcher(clause).find()) out.add(finding("WARNING", "DRAFT_APPLICANT_DEPENDENT", ":" + tag + ": Bedingung hängt vom Auftraggeber ab (Unterschrift, Freigabe oder Zustimmung): „" + shown + "“", "Streichen oder durch ein Dokument ersetzen lassen, das Sie selbst oder Dritte ausstellen.", ":" + tag + ": Condition depends on the applicant (signature, release or approval): \"" + shown + "\"", "Ask for it to be removed or replaced by a document you or a third party can issue."));
   if (VAGUE.matcher(clause).find()) out.add(finding("WARNING", "DRAFT_VAGUE", ":" + tag + ": Unbestimmte Bedingung („noch festzulegen“, „wie vereinbart“): „" + shown + "“", "Konkret formulieren lassen.", ":" + tag + ": Vague condition (\"to be advised\", \"as agreed\"): \"" + shown + "\"", "Ask for a precise wording."));
   if (LEGALISED.matcher(clause).find()) out.add(finding("INFO", "DRAFT_LEGALISATION", ":" + tag + ": Beglaubigung oder Legalisation verlangt (lange Beschaffungszeit): „" + shown + "“", "Zeit einplanen oder Verzicht verlangen.", ":" + tag + ": Legalisation or consular certification required (long lead time): \"" + shown + "\"", "Plan enough time or ask for a waiver."));
   Matcher s = SHORT_DAYS.matcher(clause);
   if (s.find() && Integer.parseInt(s.group(1)) < 7) out.add(finding("WARNING", "DRAFT_SHORT_DEADLINE", ":" + tag + ": Sehr kurze Frist (" + s.group(1) + " Tage): „" + shown + "“", "Längere Frist verlangen.", ":" + tag + ": Very short deadline (" + s.group(1) + " days): \"" + shown + "\"", "Ask for a longer deadline."));
  }
 }
}
