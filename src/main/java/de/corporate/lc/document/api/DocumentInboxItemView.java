package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentInboxItem;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentInboxItemView(UUID id, String originalFilename, String contentType, long fileSize,
                                    LocalDateTime receivedAt, String receivedBy, String status,
                                    String extractionStatus, String extractedReference,
                                    String extractedDocumentNumber, java.math.BigDecimal extractedAmount,
                                    String extractedCurrency, UUID suggestedLcId, String suggestedLcReference,
                                    java.util.List<LcAssignmentCandidate> assignmentCandidates) {
    public static DocumentInboxItemView from(DocumentInboxItem item, java.util.List<LcAssignmentCandidate> candidates) {
        LcAssignmentCandidate single=candidates.size()==1?candidates.get(0):null;
        return new DocumentInboxItemView(item.getId(), item.getOriginalFilename(), item.getContentType(), item.getFileSize(),
                item.getReceivedAt(), item.getReceivedBy(), item.getStatus(), item.getExtractionStatus(),
                item.getExtractedReference(), item.getExtractedDocumentNumber(), item.getExtractedAmount(),
                item.getExtractedCurrency(), single==null?null:single.lcId(), single==null?null:single.reference(), candidates);
    }
}
