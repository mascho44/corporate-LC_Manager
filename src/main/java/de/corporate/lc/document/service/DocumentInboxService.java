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
        var uploads=InboxUploadReader.read(files);
        var targets=lettersOfCredit.findAssignmentTargets();
        return uploads.stream().map(file -> receiveOne(file, username, targets)).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentInboxItemView> openItems() {
        var items=inbox.findTop100ByStatusOrderByReceivedAtDesc("OPEN");
        var targets=lettersOfCredit.findAssignmentTargets();
        return items.stream().map(item->view(item,targets)).toList();
    }

    @Transactional(readOnly = true)
    public DocumentInboxItem openItem(UUID id) {
        DocumentInboxItem item = inbox.findById(id).orElseThrow(() -> new NoSuchElementException("Inbox-Datei nicht gefunden."));
        if (!"OPEN".equals(item.getStatus()) || item.getContent() == null) throw new IllegalStateException("Diese Datei ist nicht mehr im offenen Eingang.");
        return item;
    }

    public record NewCaseResult(UUID lcId,DocumentInboxAttachResult attachment){}
    @Transactional
    public NewCaseResult createCase(UUID id,de.corporate.lc.document.api.InboxNewCaseRequest request){
        requireProcessed(lockedOpenItem(id));
        String reference=request.reference().trim();
        if(lettersOfCredit.existsByReference(reference))throw new IllegalArgumentException("Eine Akte mit dieser Referenz besteht bereits. Bitte die bestehende Akte auswählen.");
        var lc=new de.corporate.lc.lc.domain.LetterOfCredit();
        lc.setReference(reference);lc.setApplicant(request.applicant());lc.setBeneficiary(request.beneficiary());
        lc.setOwnBankReference(cleanReference(request.ownBankReference()));lc.setForeignBankReference(cleanReference(request.foreignBankReference()));
        lc.setAmount(request.amount());lc.setCurrency(request.currency());lc.setExpiryDate(request.expiryDate());
        lc.setIssuingBank(request.issuingBank());lc.setExpiryPlace(request.expiryPlace());
        var saved=lettersOfCredit.saveAndFlush(lc);
        return new NewCaseResult(saved.getId(),attach(id,new DocumentInboxAttachRequest(saved.getId(),request.documentType(),request.documentDate())));
    }

    @Transactional
    public DocumentInboxAttachResult attach(UUID id, DocumentInboxAttachRequest request) {
        DocumentInboxItem item = lockedOpenItem(id);
        requireProcessed(item);
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
        document.setOcrEvidenceJson(item.getOcrEvidenceJson());
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        document.setClassificationHistoryJson(ClassificationHistory.manual(item.getClassificationHistoryJson(),request.documentType(),auth==null?"unknown":auth.getName()));
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

    private DocumentInboxItemView receiveOne(InboxUploadReader.Upload file, String username,
                                            List<LetterOfCreditRepository.AssignmentTarget> targets) {
            String filename = file.filename();
            String contentType = contentType(filename);
            byte[] bytes = file.content();
            DocumentInboxItem item = new DocumentInboxItem();
            item.setOriginalFilename(filename);
            item.setContentType(contentType);
            item.setFileSize(bytes.length);
            item.setContent(bytes);
            item.setReceivedBy(username);
            item.setExtractionStatus("QUEUED");
            return view(inbox.save(item),targets);
    }

    private DocumentInboxItemView view(DocumentInboxItem item) {
        return view(item,lettersOfCredit.findAssignmentTargets());
    }

    @Transactional
    public DocumentInboxItemView retryExtraction(UUID id){
        var item=lockedOpenItem(id);requireProcessed(item);
        if(!List.of("FAILED","OCR_TIMEOUT","OCR_UNAVAILABLE","NO_TEXT","NOT_PROCESSED").contains(item.getExtractionStatus()))
            throw new IllegalStateException("Die Dokumentenerkennung ist bereits abgeschlossen.");
        item.setExtractionStatus("QUEUED");item.setExtractionStartedAt(null);item.setExtractionToken(null);
        return view(inbox.save(item));
    }

    private void requireProcessed(DocumentInboxItem item){
        if(List.of("QUEUED","PROCESSING").contains(item.getExtractionStatus()))
            throw new IllegalStateException("Die Dokumentenerkennung läuft noch. Bitte auf den Abschluss warten.");
    }

    private DocumentInboxItemView view(DocumentInboxItem item,List<LetterOfCreditRepository.AssignmentTarget> targets) {
        return DocumentInboxItemView.from(item,LcAssignmentMatcher.suggest(item,targets));
    }

    private String cleanReference(String value){return value==null||value.isBlank()?null:value.trim();}
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
