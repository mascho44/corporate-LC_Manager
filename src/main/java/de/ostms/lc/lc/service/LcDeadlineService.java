package de.ostms.lc.lc.service;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import de.ostms.lc.sla.BusinessCalendar;

@Service
public class LcDeadlineService {
    private static final Pattern PRESENTATION_DAYS = Pattern.compile("(?i)(\\d{1,3})\\s*(?:calendar\\s+days|days|tage)");
    /** "60 days after B/L date", "90 banking days after sight", "30 Tage nach Rechnungsdatum": number, kind of days and the event the term runs from. */
    private static final Pattern TENOR = Pattern.compile("(?i)(\\d{1,3})\\s*(calendar\\s+days|banking\\s+days|business\\s+days|working\\s+days|bank(?:arbeits)?tage|werktage|days|tage)\\s+(?:after|from|ab|nach)\\s+(?:the\\s+|dem\\s+|der\\s+)?(?:date\\s+of\\s+|datum\\s+(?:des|der)\\s+)?(bill\\s+of\\s+lading|b/?l|shipment|dispatch|invoice|rechnung|sight|presentation|vorlage|einreichung|sicht)");
    private static final Pattern AT_SIGHT = Pattern.compile("(?i)\\bat\\s+sight\\b|\\bbei\\s+sicht\\b|\\bsight\\s+payment\\b");
    /** UCP 600 art. 14(b): the bank has at most five banking days after presentation to examine; payment at sight follows within that time. */
    static final int SIGHT_EXAMINATION_BANKING_DAYS = 5;
    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDocumentRepository documents;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private de.ostms.lc.guarantee.GuaranteeDetailRepository guarantees;

    public LcDeadlineService(LetterOfCreditRepository lettersOfCredit, LcDocumentRepository documents) {
        this.lettersOfCredit = lettersOfCredit;
        this.documents = documents;
    }

    public List<LcDeadlineView> forLc(UUID lcId) {
        LetterOfCredit lc = lettersOfCredit.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        List<LcDeadlineView> result = new ArrayList<>();
        boolean guarantee = "GUARANTEE".equals(lc.getInstrumentType());
        if (guarantee) guaranteeDeadlines(lc, result);
        else add(result, "EXPIRY", "Ablauf des Akkreditivs", lc.getExpiryDate(), "SWIFT-Feld :31D:", "Verbindliches Ablaufdatum des Akkreditivs.");
        if (!guarantee) add(result, "LATEST_SHIPMENT", "Spätester Versand", lc.getLatestShipmentDate(), "SWIFT-Feld :44C:", "Im Akkreditiv angegebener spätester Versandtermin.");
        add(result, "FOLLOW_UP", "Wiedervorlage", lc.getFollowUpDate(), "Manuell erfasst", "Interner Bearbeitungstermin.");

        String field48 = field(lc, "48");
        Integer presentationDays = days(PRESENTATION_DAYS, field48);
        var lcDocuments = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        if (presentationDays != null) {
            lcDocuments.stream()
                    .filter(doc -> isTransport(doc.getDocumentType()) && doc.getDocumentDate() != null)
                    .forEach(doc -> add(result, "PRESENTATION", "Vorlagefrist – " + doc.getOriginalFilename(), doc.getDocumentDate().plusDays(presentationDays),
                            "SWIFT-Feld :48: " + field48, "Berechnet ab Transportdokumentdatum " + doc.getDocumentDate() + "; Kalendertage, ohne Feiertagsanpassung."));
        }

        maturityEstimates(lc, lcDocuments, result);
        result.sort(Comparator.comparing(LcDeadlineView::date).thenComparing(LcDeadlineView::type));
        return result;
    }

    /**
     * The expected date of the payment, estimated from the payment clause (:42C:, :42P:, :42M:): a term of days after the bill of lading / shipment, the invoice
     * or the presentation (sight), or payment at sight (examination time after presentation). Needs the dated document or the presentation date; nothing is guessed without it.
     */
    private void maturityEstimates(LetterOfCredit lc, List<de.ostms.lc.document.domain.LcDocument> lcDocuments, List<LcDeadlineView> result) {
        String clause = firstNonBlank(field(lc, "42C"), field(lc, "42P"), field(lc, "42M"));
        if (clause == null) return;
        LocalDate presentation = presentationDate(lc);
        Matcher tenor = TENOR.matcher(clause);
        if (tenor.find()) {
            Integer days = parseDays(tenor.group(1));
            if (days == null) return;
            boolean banking = Pattern.compile("(?i)banking|business|working|bank|werk").matcher(tenor.group(2)).find();
            String anchor = tenor.group(3).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            String kind = banking ? "Bankarbeitstage" : "Kalendertage";
            if (anchor.contains("invoice") || anchor.contains("rechnung")) {
                lcDocuments.stream().filter(doc -> doc.getDocumentType() == DocumentType.COMMERCIAL_INVOICE && doc.getDocumentDate() != null)
                        .forEach(doc -> add(result, "MATURITY_ESTIMATE", "Voraussichtliche Fälligkeit – " + doc.getOriginalFilename(), plus(doc.getDocumentDate(), days, banking),
                                "SWIFT-Zahlungsklausel: " + clause, "Automatische Schätzung: " + days + " " + kind + " ab Rechnungsdatum " + doc.getDocumentDate() + "; Zahlungsbedingungen fachlich prüfen."));
            } else if (anchor.contains("sight") || anchor.contains("sicht") || anchor.contains("presentation") || anchor.contains("vorlage") || anchor.contains("einreichung")) {
                if (presentation != null) add(result, "MATURITY_ESTIMATE", "Voraussichtliche Fälligkeit – ab Einreichung", plus(presentation, days, banking),
                        "SWIFT-Zahlungsklausel: " + clause, "Automatische Schätzung: " + days + " " + kind + " ab Einreichung der Dokumente am " + presentation + "; Zahlungsbedingungen fachlich prüfen.");
            } else {
                lcDocuments.stream().filter(doc -> isTransport(doc.getDocumentType()) && doc.getDocumentDate() != null)
                        .forEach(doc -> add(result, "MATURITY_ESTIMATE", "Voraussichtliche Fälligkeit – " + doc.getOriginalFilename(), plus(doc.getDocumentDate(), days, banking),
                                "SWIFT-Zahlungsklausel: " + clause, "Automatische Schätzung: " + days + " " + kind + " ab Transportdatum " + doc.getDocumentDate() + "; Zahlungsbedingungen fachlich prüfen."));
            }
        } else if (AT_SIGHT.matcher(clause).find() && presentation != null) {
            add(result, "MATURITY_ESTIMATE", "Späteste Zahlung bei Sicht", BusinessCalendar.addBusinessDays(presentation, SIGHT_EXAMINATION_BANKING_DAYS),
                    "SWIFT-Zahlungsklausel: " + clause, "Automatische Schätzung: Einreichung am " + presentation + " plus höchstens " + SIGHT_EXAMINATION_BANKING_DAYS + " Bankarbeitstage Prüfzeit (UCP 600 Art. 14); die Bank kann früher zahlen.");
        }
    }
    private static LocalDate plus(LocalDate from, int days, boolean banking) { return banking ? BusinessCalendar.addBusinessDays(from, days) : from.plusDays(days); }
    /** The date the documents were presented (set when the file reaches that status or entered by hand), or null. */
    private LocalDate presentationDate(LetterOfCredit lc) {
        try {
            String value = de.ostms.lc.rulepack.RuleFacts.read(lc.getRuleFactsJson()).get(de.ostms.lc.rulepack.PackDefinition.Field.LC_PRESENTATION_DATE);
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (RuntimeException unreadable) { return null; }
    }

    /** Deadlines of a guarantee of the company: expiry, end of the claim period and the date by which to decide on extension or return. Open guarantees only. */
    private void guaranteeDeadlines(LetterOfCredit lc, List<LcDeadlineView> result) {
        var detail = guarantees == null ? java.util.Optional.<de.ostms.lc.guarantee.GuaranteeDetail>empty() : guarantees.byLc(lc.getId());
        boolean open = detail.map(d -> "REQUESTED".equals(d.getStatus()) || "ACTIVE".equals(d.getStatus())).orElse(true);
        if (!open) return;
        add(result, "EXPIRY", "Ablauf der Garantie", lc.getExpiryDate(), "Garantie", "Gültig bis; danach erlischt die Garantie, sofern sie nicht verlängert wird.");
        detail.ifPresent(d -> {
            add(result, "CLAIM_UNTIL", "Ende der Frist für die Inanspruchnahme", d.getClaimUntil(), "Garantie", "Bis dahin kann der Begünstigte noch Ansprüche stellen; die Garantie ist erst danach sicher erledigt.");
            if (d.decisionDate(lc.getExpiryDate()) != null)
                add(result, "EXTENSION_DECISION", d.isAutoExtension() ? "Entscheiden: Verlängerung verhindern oder Rückgabe verlangen" : "Entscheiden: Verlängerung beantragen oder Rückgabe verlangen", d.decisionDate(lc.getExpiryDate()), "Garantie",
                    d.getExtensionNoticeDays() + " Tage vor Ablauf" + (d.isAutoExtension() ? "; sonst verlängert sich die Garantie automatisch." : "."));
        });
    }

    private String field(LetterOfCredit lc, String tag) {
        if (lc.getAdditionalFields() != null) {
            for (var entry : lc.getAdditionalFields().entrySet())
                if (entry.getKey().replace(":", "").equalsIgnoreCase(tag) && entry.getValue() != null && !entry.getValue().isBlank()) return entry.getValue();
        }
        String raw = lc.getRawMessage();
        if (raw == null) return null;
        Matcher matcher = Pattern.compile("(?s):" + Pattern.quote(tag) + ":(.*?)(?=\\r?\\n?:\\d{2}[A-Z]?:|\\z)").matcher(raw);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private Integer days(Pattern pattern, String value) {
        if (value == null) return null;
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? parseDays(matcher.group(1)) : null;
    }
    private Integer parseDays(String value) { try { int days = Integer.parseInt(value); return days > 0 && days <= 999 ? days : null; } catch (NumberFormatException ignored) { return null; } }
    private String firstNonBlank(String... values) { return Arrays.stream(values).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null); }
    private boolean isTransport(DocumentType type) { return type == DocumentType.BILL_OF_LADING || type == DocumentType.AIR_WAYBILL || type == DocumentType.ROAD_CONSIGNMENT_NOTE; }
    private void add(List<LcDeadlineView> list, String type, String title, LocalDate date, String source, String note) { if (date != null) list.add(new LcDeadlineView(type, title, date, source, note, !"MATURITY_ESTIMATE".equals(type))); }

    public record LcDeadlineView(String type, String title, LocalDate date, String source, String note, boolean confirmedBasis) {}
}
