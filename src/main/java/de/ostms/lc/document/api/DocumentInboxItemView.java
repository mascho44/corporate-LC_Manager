package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.DocumentInboxItem;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentInboxItemView(UUID id, String originalFilename, String contentType, long fileSize,
                                    LocalDateTime receivedAt, String receivedBy, String status,
                                    String extractionStatus, String extractedReference,
                                    String extractedDocumentNumber, java.math.BigDecimal extractedAmount,
                                    String extractedCurrency, UUID suggestedLcId, String suggestedLcReference,
                                    java.util.List<LcAssignmentCandidate> assignmentCandidates,
                                    de.ostms.lc.document.service.DocumentClassifier.Classification classification,
                                    UUID sourceInboxId,Integer sourceFromPage,Integer sourceToPage,boolean automaticallySplit,Integer copyNumber,
                                    de.ostms.lc.document.service.DocumentCopyDetector.Hint copyHint,
                                    java.time.LocalDate extractedDocumentDate,String documentDateRecognitionStatus,String metadataReviewJson,
                                    java.util.List<de.ostms.lc.document.service.OcrEvidence.PageResult> recognitionPages) {
    public static DocumentInboxItemView from(DocumentInboxItem item, java.util.List<LcAssignmentCandidate> candidates) {
        LcAssignmentCandidate single=candidates.size()==1?candidates.get(0):null;
        var selected=de.ostms.lc.document.service.ClassificationHistory.selectedType(item.getClassificationHistoryJson());
        var evidence=de.ostms.lc.document.service.DocumentExtractionService.readEvidence(item.getOcrEvidenceJson());
        return new DocumentInboxItemView(item.getId(), item.getOriginalFilename(), item.getContentType(), item.getFileSize(),
                item.getReceivedAt(), item.getReceivedBy(), item.getStatus(), item.getExtractionStatus(),
                item.getExtractedReference(), item.getExtractedDocumentNumber(), item.getExtractedAmount(),
                item.getExtractedCurrency(), single==null?null:single.lcId(), single==null?null:single.reference(), candidates,
                selected!=null?new de.ostms.lc.document.service.DocumentClassifier.Classification(selected,1,"CONFIRMED","MANUAL",java.util.List.of("Dokumenttyp bei der Aufteilung bestätigt")):"OPEN".equals(item.getStatus())?de.ostms.lc.document.service.DocumentClassifier.classify(item.getOriginalFilename(),item.getExtractedText()):de.ostms.lc.document.service.ClassificationHistory.suggestion(item.getClassificationHistoryJson()),item.getSourceInboxId(),item.getSourceFromPage(),item.getSourceToPage(),de.ostms.lc.document.service.ClassificationHistory.wasAutomaticallySplit(item.getClassificationHistoryJson()),item.getCopyNumber(),de.ostms.lc.document.service.DocumentCopyDetector.detect(item.getExtractedText()),de.ostms.lc.document.service.DocumentDateDetector.detect(item.getExtractedText()).date(),de.ostms.lc.document.service.DocumentDateDetector.detect(item.getExtractedText()).status(),item.getMetadataReviewJson(),evidence==null?java.util.List.of():evidence.pages());
    }
}
