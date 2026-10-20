package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.service.DocumentExtractionService;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a draft from a third party (for example the forwarder's bill of lading draft) against the credit before the original is issued.
 * The draft is read and checked together with the documents already in the file, in place of a document of the same type; nothing is stored.
 */
@Service
public class DraftCheckService {
    static final String PREFIX = "Entwurf: ";
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    public record Finding(String severity, String code, String message, String advice, String lcCondition, String evidence, String messageEn, String adviceEn) {}
    public record Result(String filename, DocumentType type, boolean textFound, String extractedReference, LocalDate extractedDate, BigDecimal extractedAmount, String extractedCurrency,
                         long discrepancies, long warnings, List<Finding> findings) {}

    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;
    private final DocumentExtractionService extraction;
    private final DocumentCheckService checks;

    public DraftCheckService(LetterOfCreditRepository lcs, LcDocumentRepository documents, DocumentExtractionService extraction, DocumentCheckService checks) {
        this.lcs = lcs; this.documents = documents; this.extraction = extraction; this.checks = checks;
    }

    @Transactional(readOnly = true)
    public Result check(UUID lcId, byte[] content, String filename, String contentType, DocumentType type) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("Bitte eine Datei mit dem Entwurf wählen.");
        if (content.length > MAX_SIZE) throw new IllegalArgumentException("Die Datei ist größer als 10 MB.");
        if (type == null) throw new IllegalArgumentException("Bitte die Dokumentart des Entwurfs angeben.");
        var lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        String name = PREFIX + (filename == null || filename.isBlank() ? "Dokument" : filename.replaceAll("[\\\\/]", "_"));

        var draft = new LcDocument();
        draft.setLetterOfCredit(lc); draft.setDocumentType(type); draft.setOriginalFilename(name);
        draft.setContentType(contentType == null ? "application/octet-stream" : contentType); draft.setFileSize(content.length); draft.setContent(content);
        extraction.extract(draft);
        if (draft.getAmount() == null) draft.setAmount(draft.getExtractedAmount());
        if (draft.getCurrency() == null) draft.setCurrency(draft.getExtractedCurrency());

        var set = new ArrayList<LcDocument>();
        for (LcDocument existing : documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)) if (existing.getDocumentType() != type) set.add(existing);
        set.add(draft);
        var findings = new ArrayList<Finding>();
        for (CheckResult result : checks.simulate(lcId, lc, set).results())
            if (name.equals(result.documentName()) && result.severity() != CheckResult.Severity.OK)
                findings.add(new Finding(result.severity().name(), result.code(), result.message(), result.advice(), result.lcCondition(), result.documentEvidence(), result.messageEn(), result.adviceEn()));
        findings.sort((a, b) -> a.severity().equals(b.severity()) ? 0 : "DISCREPANCY".equals(a.severity()) ? -1 : 1);
        String text = draft.getExtractedText();
        return new Result(name, type, text != null && !text.isBlank(), draft.getExtractedReference(), draft.getDocumentDate(), draft.getExtractedAmount(), draft.getExtractedCurrency(),
            findings.stream().filter(f -> "DISCREPANCY".equals(f.severity())).count(), findings.stream().filter(f -> "WARNING".equals(f.severity())).count(), findings);
    }
}
