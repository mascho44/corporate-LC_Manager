package de.ostms.lc.document.service;

import de.ostms.lc.document.api.DocumentInboxAttachRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.imports.api.SwiftImportPreview;
import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.lc.domain.LetterOfCredit;
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
    public record Preview(SwiftImportPreview credit, boolean creditTextFound) {}
    public record Created(UUID lcId, String reference) {}

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
        return HAS_CREDIT_FIELDS.matcher(raw).find() ? raw : "";
    }
    private static int firstFieldOffset(String text) {
        var m = Pattern.compile("(?m)^.*?:\\d{2}[A-Z]?:|^\\s*\\d{2}[A-Z]?\\s+\\S").matcher(text);
        return m.find() ? m.start() : 0;
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID inboxId) {
        var item = inbox.openItem(inboxId);
        String raw = creditText(normalizer, item.getExtractedText());
        if (raw.isEmpty()) return new Preview(null, false);
        return new Preview(swift.preview(new SwiftImportRequest(item.getOriginalFilename(), raw)), true);
    }

    @Transactional
    public Created create(UUID inboxId) {
        var item = inbox.openItem(inboxId);
        String raw = creditText(normalizer, item.getExtractedText());
        if (raw.isEmpty()) throw new IllegalArgumentException("Im Avis wurde kein Akkreditivtext erkannt (Felder wie :20:, :32B:, :31D:).");
        Object imported = swift.execute(new SwiftImportRequest(item.getOriginalFilename(), raw));
        if (!(imported instanceof LetterOfCredit lc)) throw new IllegalArgumentException("Der Text ist kein Akkreditiv (MT700).");
        inbox.attach(inboxId, new DocumentInboxAttachRequest(lc.getId(), DocumentType.ADVISING_LETTER, null, null));
        return new Created(lc.getId(), lc.getReference());
    }
}
