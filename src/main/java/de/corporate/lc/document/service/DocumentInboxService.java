package de.corporate.lc.document.service;

import de.corporate.lc.document.api.DocumentInboxAttachRequest;
import de.corporate.lc.document.api.DocumentInboxAttachResult;
import de.corporate.lc.document.api.DocumentInboxItemView;
import de.corporate.lc.document.api.DocumentView;
import de.corporate.lc.document.domain.DocumentInboxItem;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import de.corporate.lc.document.repository.DocumentInboxRepository;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DocumentInboxService {
    private final DocumentInboxRepository inbox;
    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDocumentRepository documents;
    private final DocumentExtractionService extraction;
    private final de.corporate.lc.check.service.DocumentCheckService checks;

    public DocumentInboxService(DocumentInboxRepository inbox, LetterOfCreditRepository lettersOfCredit,
                                LcDocumentRepository documents, DocumentExtractionService extraction,
                                de.corporate.lc.check.service.DocumentCheckService checks) {
        this.inbox = inbox;
        this.lettersOfCredit = lettersOfCredit;
        this.documents = documents;
        this.extraction = extraction;
        this.checks = checks;
    }

    @Transactional
    public List<DocumentInboxItemView> receive(List<MultipartFile> files, String username) throws IOException {
        return InboxUploadReader.read(files).stream().map(file -> receiveOne(file, username)).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentInboxItemView> openItems() {
        return inbox.findTop100ByStatusOrderByReceivedAtDesc("OPEN").stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public DocumentInboxItem openItem(UUID id) {
        DocumentInboxItem item = inbox.findById(id).orElseThrow(() -> new NoSuchElementException("Inbox-Datei nicht gefunden."));
        if (!"OPEN".equals(item.getStatus()) || item.getContent() == null) throw new IllegalStateException("Diese Datei ist nicht mehr im offenen Eingang.");
        return item;
    }

    @Transactional
    public DocumentInboxAttachResult attach(UUID id, DocumentInboxAttachRequest request) {
        DocumentInboxItem item = lockedOpenItem(id);
        var lc = lettersOfCredit.findById(request.lcId()).orElseThrow(() -> new NoSuchElementException("LC-Akte nicht gefunden."));
        LcDocument document = new LcDocument();
        document.setLetterOfCredit(lc);
        document.setDocumentType(request.documentType());
        document.setOriginalFilename(item.getOriginalFilename());
        document.setContentType(item.getContentType());
        document.setFileSize(item.getFileSize());
        document.setContent(item.getContent());
        document.setDocumentDate(request.documentDate());
        document.setAmount(item.getExtractedAmount());
        document.setCurrency(item.getExtractedCurrency());
        document.setExtractedReference(item.getExtractedReference());
        document.setExtractedDocumentNumber(item.getExtractedDocumentNumber());
        document.setExtractedAmount(item.getExtractedAmount());
        document.setExtractedCurrency(item.getExtractedCurrency());
        document.setExtractedText(item.getExtractedText());
        document.setExtractionStatus(item.getExtractionStatus());
        LcDocument saved = documents.save(document);
        checks.invalidateDecisions(request.lcId());
        item.setStatus("ATTACHED");
        item.setAttachedLcId(lc.getId());
        item.setAttachedDocumentId(saved.getId());
        item.setContent(null);
        inbox.save(item);
        return new DocumentInboxAttachResult(view(item), DocumentView.from(saved));
    }

    @Transactional
    public DocumentInboxItem delete(UUID id) {
        DocumentInboxItem item = lockedOpenItem(id);
        inbox.delete(item);
        return item;
    }

    private DocumentInboxItem lockedOpenItem(UUID id) {
        DocumentInboxItem item = inbox.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Inbox-Datei nicht gefunden."));
        if (!"OPEN".equals(item.getStatus()) || item.getContent() == null) throw new IllegalStateException("Diese Datei ist nicht mehr im offenen Eingang.");
        return item;
    }

    private DocumentInboxItemView receiveOne(InboxUploadReader.Upload file, String username) {
            String filename = file.filename();
            String contentType = contentType(filename);
            byte[] bytes = file.content();
            LcDocument extracted = new LcDocument();
            extracted.setOriginalFilename(filename);
            extracted.setContentType(contentType);
            extracted.setContent(bytes);
            extracted.setDocumentType(DocumentType.ANNEX);
            extraction.extract(extracted);

            DocumentInboxItem item = new DocumentInboxItem();
            item.setOriginalFilename(filename);
            item.setContentType(contentType);
            item.setFileSize(bytes.length);
            item.setContent(bytes);
            item.setReceivedBy(username);
            item.setExtractionStatus(extracted.getExtractionStatus());
            item.setExtractedReference(extracted.getExtractedReference());
            item.setExtractedDocumentNumber(extracted.getExtractedDocumentNumber());
            item.setExtractedAmount(extracted.getExtractedAmount());
            item.setExtractedCurrency(extracted.getExtractedCurrency());
            item.setExtractedText(extracted.getExtractedText());
            return view(inbox.save(item));
    }

    private DocumentInboxItemView view(DocumentInboxItem item) {
        UUID suggestedId = null;
        String suggestedReference = null;
        if (item.getExtractedReference() != null && !item.getExtractedReference().isBlank()) {
            var suggestion = lettersOfCredit.findByReference(item.getExtractedReference().trim());
            if (suggestion.isPresent()) {
                suggestedId = suggestion.get().getId();
                suggestedReference = suggestion.get().getReference();
            }
        }
        return DocumentInboxItemView.from(item, suggestedId, suggestedReference);
    }

    private String contentType(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".txt") || name.endsWith(".csv")) return "text/plain";
        if (name.endsWith(".xml")) return "application/xml";
        return "application/octet-stream";
    }
}
