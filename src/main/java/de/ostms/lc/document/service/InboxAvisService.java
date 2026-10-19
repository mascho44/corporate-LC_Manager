package de.ostms.lc.document.service;

import de.ostms.lc.document.api.DocumentInboxAttachRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.imports.api.SwiftImportPreview;
import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.swift.LabelledCreditNormalizer;
import de.ostms.lc.swift.PrintedSwiftNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An advice from the bank is a cover letter followed by the text of the documentary credit. The credit text is taken from the extracted text of the
 * inbox PDF (printed SWIFT fields in colon or table form), checked like any MT700 import and turned into a new LC file; the advice itself is stored in it.
 */
@Service
public class InboxAvisService {
    /** `cover` carries what the letter itself says (reference, amount, expiry, banks, applicant) when the advice has no credit text of its own. */
    public record Preview(SwiftImportPreview credit, boolean creditTextFound, AdvisingLetterExtractor.Proposal cover) {}
    public record Created(UUID lcId, String reference, String kind) {
        public Created(UUID lcId, String reference) { this(lcId, reference, "MT700"); }
    }

    private static final Pattern CLOSING = Pattern.compile("(?im)^\\s*(mit freundlichen gr(?:ü|ue)(?:ß|ss)en|freundliche gr(?:ü|ue)(?:ß|ss)e|hochachtungsvoll|best regards|kind regards|yours faithfully|yours sincerely|sincerely|with kind regards)\\b.*$");
    private static final Pattern HAS_CREDIT_FIELDS = Pattern.compile("(?m)^:(?:20|32B|31D):");

    private final DocumentInboxService inbox;
    private final PrintedSwiftNormalizer normalizer;
    private final SwiftImportService swift;

    public InboxAvisService(DocumentInboxService inbox, PrintedSwiftNormalizer normalizer, SwiftImportService swift) {
        this.inbox = inbox; this.normalizer = normalizer; this.swift = swift;
    }

    /** The credit text of the advice as MT700 fields; the closing of the letter after the last field is cut off. */
    static String creditText(PrintedSwiftNormalizer normalizer, String extracted) {
        if (extracted == null || extracted.isBlank()) return "";
        String text = extracted;
        var closing = CLOSING.matcher(text);
        int first = firstFieldOffset(text);
        while (closing.find()) if (closing.start() > first) { text = text.substring(0, closing.start()); break; }
        String raw = normalizer.normalize(text);
        if (HAS_CREDIT_FIELDS.matcher(raw).find()) return raw;
        return labelled(extracted);
    }

    /**
     * A credit printed with field names but without tag numbers. The cover letter before it ends with a greeting, so the text is also read
     * in the parts between the greetings; the reading with the most fields wins.
     */
    static String labelled(String extracted) {
        String best = LabelledCreditNormalizer.normalize(extracted);
        for (String part : CLOSING.split(extracted)) {
            String candidate = LabelledCreditNormalizer.normalize(part);
            if (candidate.lines().count() > best.lines().count()) best = candidate;
        }
        return best;
    }
    /** Offset of the first line that opens a SWIFT field in any printed form the normalizer understands (":20:", "20 Label", "27: LABEL", OCR variants). */
    static int firstFieldOffset(String text) {
        int offset = 0;
        for (String line : text.split("\\n", -1)) {
            if (PrintedSwiftNormalizer.field(line) != null) return offset;
            offset += line.length() + 1;
        }
        return 0;
    }

    /** The inbox recognised or a person confirmed the file as SWIFT MT700 (not an advice with cover letter). */
    static boolean swiftMessage(de.ostms.lc.document.domain.DocumentInboxItem item) {
        try {
            var selected = ClassificationHistory.selectedType(item.getClassificationHistoryJson());
            if (selected != null) return selected == DocumentType.SWIFT_MT700;
            var suggestion = ClassificationHistory.suggestion(item.getClassificationHistoryJson());
            return suggestion != null && suggestion.suggestedType() == DocumentType.SWIFT_MT700;
        } catch (RuntimeException unreadable) { return false; }
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID inboxId) {
        var item = inbox.openItem(inboxId);
        String raw = creditText(normalizer, item.getExtractedText());
        if (raw.isEmpty()) return new Preview(null, false, AdvisingLetterExtractor.extract(item.getExtractedText()));
        return new Preview(swift.preview(new SwiftImportRequest(item.getOriginalFilename(), raw)), true, null);
    }

    @Transactional
    public Created create(UUID inboxId) { return create(inboxId, null, null); }
    /** With corrections of applicant/beneficiary when :50:/:59: only refer to another field. */
    @Transactional
    public Created create(UUID inboxId, String applicant, String beneficiary) {
        var item = inbox.openItem(inboxId);
        String raw = creditText(normalizer, item.getExtractedText());
        if (raw.isEmpty()) throw new IllegalArgumentException("Im Avis wurde kein Akkreditivtext erkannt (Felder wie :20:, :32B:, :31D:).");
        Object imported = swift.execute(new SwiftImportRequest(item.getOriginalFilename(), raw, applicant, beneficiary));
        // An amendment (MT707) belongs to its existing file; the received message is kept there as a document.
        if (imported instanceof de.ostms.lc.lc.domain.Amendment amendment) {
            var lc = amendment.getLetterOfCredit();
            inbox.attach(inboxId, new DocumentInboxAttachRequest(lc.getId(), DocumentType.OTHER, null, null));
            return new Created(lc.getId(), lc.getReference(), "MT707");
        }
        if (!(imported instanceof LetterOfCredit lc)) throw new IllegalArgumentException("Der Text ist keine importierbare SWIFT-Nachricht (MT700, MT707, MT760).");
        // A pure SWIFT message is filed as such; an advice with a cover letter as advising letter.
        inbox.attach(inboxId, new DocumentInboxAttachRequest(lc.getId(), swiftMessage(item) ? DocumentType.SWIFT_MT700 : DocumentType.ADVISING_LETTER, null, null));
        return new Created(lc.getId(), lc.getReference(), "MT700");
    }
}
