package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.domain.LcDocument;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentView(UUID id, DocumentType documentType, String typeLabel,
        String originalFilename, String contentType, long fileSize, LocalDate documentDate,
        BigDecimal amount, String currency, LocalDateTime uploadedAt, String downloadUrl,
        String extractionStatus, String extractedReference, String extractedDocumentNumber,
        BigDecimal extractedAmount, String extractedCurrency) {
    public static DocumentView from(LcDocument document) {
        return new DocumentView(document.getId(), document.getDocumentType(),
                document.getDocumentType().getDisplayName(), document.getOriginalFilename(),
                document.getContentType(), document.getFileSize(), document.getDocumentDate(),
                document.getAmount(), document.getCurrency(), document.getUploadedAt(),
                "/api/documents/" + document.getId() + "/content", document.getExtractionStatus(),
                document.getExtractedReference(), document.getExtractedDocumentNumber(),
                document.getExtractedAmount(), document.getExtractedCurrency());
    }
}
