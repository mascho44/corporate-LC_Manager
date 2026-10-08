package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentInboxItem;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentInboxItemView(UUID id, String originalFilename, String contentType, long fileSize,
                                    LocalDateTime receivedAt, String receivedBy, String status,
                                    String extractionStatus, String extractedReference,
                                    String extractedDocumentNumber, java.math.BigDecimal extractedAmount,
                                    String extractedCurrency, UUID suggestedLcId, String suggestedLcReference,
                                    java.util.List<LcAssignmentCandidate> assignmentCandidates,
                                    de.corporate.lc.document.service.DocumentClassifier.Classification classification,
                                    UUID sourceInboxId,Integer sourceFromPage,Integer sourceToPage,boolean automaticallySplit) {
    public static DocumentInboxItemView from(DocumentInboxItem item, java.util.List<LcAssignmentCandidate> candidates) {
        LcAssignmentCandidate single=candidates.size()==1?candidates.get(0):null;
        var selected=de.corporate.lc.document.service.ClassificationHistory.selectedType(item.getClassificationHistoryJson());
        return new DocumentInboxItemView(item.getId(), item.getOriginalFilename(), item.getContentType(), item.getFileSize(),
                item.getReceivedAt(), item.getReceivedBy(), item.getStatus(), item.getExtractionStatus(),
                item.getExtractedReference(), item.getExtractedDocumentNumber(), item.getExtractedAmount(),
                item.getExtractedCurrency(), single==null?null:single.lcId(), single==null?null:single.reference(), candidates,
                selected!=null?new de.corporate.lc.document.service.DocumentClassifier.Classification(selected,1,"CONFIRMED","MANUAL",java.util.List.of("Dokumenttyp bei der Aufteilung bestätigt")):"OPEN".equals(item.getStatus())?de.corporate.lc.document.service.DocumentClassifier.classify(item.getOriginalFilename(),item.getExtractedText()):de.corporate.lc.document.service.ClassificationHistory.suggestion(item.getClassificationHistoryJson()),item.getSourceInboxId(),item.getSourceFromPage(),item.getSourceToPage(),de.corporate.lc.document.service.ClassificationHistory.wasAutomaticallySplit(item.getClassificationHistoryJson()));
    }
}
