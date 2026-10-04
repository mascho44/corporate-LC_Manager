package de.corporate.lc.lc.service;

import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LcDeadlineService {
    private static final Pattern PRESENTATION_DAYS = Pattern.compile("(?i)(\\d{1,3})\\s*(?:calendar\\s+days|days|tage)");
    private static final Pattern SHIPMENT_TENOR = Pattern.compile("(?i)(\\d{1,3})\\s*(?:calendar\\s+days|days|tage)\\s+after\\s+(?:the\\s+)?(?:bill\\s+of\\s+lading|b/?l|shipment|dispatch)(?:\\s+date)?");
    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDocumentRepository documents;

    public LcDeadlineService(LetterOfCreditRepository lettersOfCredit, LcDocumentRepository documents) {
        this.lettersOfCredit = lettersOfCredit;
        this.documents = documents;
    }

    public List<LcDeadlineView> forLc(UUID lcId) {
        LetterOfCredit lc = lettersOfCredit.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        List<LcDeadlineView> result = new ArrayList<>();
        add(result, "EXPIRY", "Ablauf des Akkreditivs", lc.getExpiryDate(), "SWIFT-Feld :31D:", "Verbindliches Ablaufdatum des Akkreditivs.");
        add(result, "LATEST_SHIPMENT", "Spätester Versand", lc.getLatestShipmentDate(), "SWIFT-Feld :44C:", "Im Akkreditiv angegebener spätester Versandtermin.");
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

        String maturityTerm = firstNonBlank(field(lc, "42C"), field(lc, "42P"), field(lc, "42M"));
        Matcher tenor = SHIPMENT_TENOR.matcher(Objects.toString(maturityTerm, ""));
        if (tenor.find()) {
            Integer days = parseDays(tenor.group(1));
            if (days != null) lcDocuments.stream()
                    .filter(doc -> isTransport(doc.getDocumentType()) && doc.getDocumentDate() != null)
                    .forEach(doc -> add(result, "MATURITY_ESTIMATE", "Voraussichtliche Fälligkeit – " + doc.getOriginalFilename(), doc.getDocumentDate().plusDays(days),
                            "SWIFT-Zahlungsklausel: " + maturityTerm, "Automatische Kalender-Tage-Schätzung ab Transportdatum; Zahlungsbedingungen fachlich prüfen."));
        }
        result.sort(Comparator.comparing(LcDeadlineView::date).thenComparing(LcDeadlineView::type));
        return result;
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
