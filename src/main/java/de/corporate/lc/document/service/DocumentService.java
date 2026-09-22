package de.corporate.lc.document.service;

import de.corporate.lc.document.api.DocumentView;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DocumentService {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private final LcDocumentRepository documents;
    private final LetterOfCreditRepository lcs;
    private final DocumentExtractionService extraction;

    public DocumentService(LcDocumentRepository documents, LetterOfCreditRepository lcs,
            DocumentExtractionService extraction) {
        this.documents = documents;
        this.lcs = lcs;
        this.extraction = extraction;
    }

    @Transactional
    public DocumentView upload(UUID lcId, MultipartFile file, DocumentType type,
            LocalDate documentDate, BigDecimal amount, String currency) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please select a non-empty file.");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("File exceeds the 10 MB limit.");
        String filename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        if (filename.contains("/") || filename.contains("\\")) throw new IllegalArgumentException("Invalid filename.");

        LcDocument document = new LcDocument();
        document.setLetterOfCredit(lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("LC not found: " + lcId)));
        document.setDocumentType(type);
        document.setOriginalFilename(filename);
        document.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        document.setFileSize(file.getSize());
        document.setDocumentDate(documentDate);
        document.setAmount(amount);
        document.setCurrency(currency == null || currency.isBlank() ? null : currency.trim().toUpperCase());
        document.setContent(file.getBytes());
        extraction.extract(document);
        if (document.getAmount() == null) document.setAmount(document.getExtractedAmount());
        if (document.getCurrency() == null) document.setCurrency(document.getExtractedCurrency());
        return DocumentView.from(documents.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentView> forLc(UUID lcId) {
        if (!lcs.existsById(lcId)) throw new NoSuchElementException("LC not found: " + lcId);
        return documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId).stream().map(DocumentView::from).toList();
    }

    @Transactional(readOnly = true)
    public LcDocument one(UUID id) {
        return documents.findById(id).orElseThrow(() -> new NoSuchElementException("Document not found: " + id));
    }
}
