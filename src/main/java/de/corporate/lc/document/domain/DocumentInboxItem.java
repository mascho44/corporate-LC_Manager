package de.corporate.lc.document.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_inbox_item")
public class DocumentInboxItem {
    @Column(columnDefinition="text") @JsonIgnore private String classificationHistoryJson;
    @Column(columnDefinition="text") @JsonIgnore private String ocrEvidenceJson;
    public String getClassificationHistoryJson(){return classificationHistoryJson;}
    public void setClassificationHistoryJson(String value){classificationHistoryJson=value;}
    public String getOcrEvidenceJson(){return ocrEvidenceJson;}
    public void setOcrEvidenceJson(String value){ocrEvidenceJson=value;}
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 255) private String originalFilename;
    @Column(nullable = false, length = 150) private String contentType;
    @Column(nullable = false) private long fileSize;
    @Basic(fetch = FetchType.LAZY) @Column(columnDefinition = "bytea") @JsonIgnore private byte[] content;
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(nullable = false, length = 100) private String receivedBy;
    @Column(nullable = false) private LocalDateTime receivedAt = LocalDateTime.now();
    @Column(nullable = false, length = 30) private String extractionStatus = "NOT_PROCESSED";
    @Column(length = 100) private String extractedReference;
    @Column(length = 100) private String extractedDocumentNumber;
    @Column(columnDefinition = "text") private String extractedText;
    @Column(precision = 19, scale = 2) private BigDecimal extractedAmount;
    @Column(length = 3) private String extractedCurrency;
    private UUID attachedLcId;
    private UUID attachedDocumentId;

    public UUID getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long value) { fileSize = value; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] value) { content = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String value) { receivedBy = value; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public String getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(String value) { extractionStatus = value; }
    public String getExtractedReference() { return extractedReference; }
    public void setExtractedReference(String value) { extractedReference = value; }
    public String getExtractedDocumentNumber() { return extractedDocumentNumber; }
    public void setExtractedDocumentNumber(String value) { extractedDocumentNumber = value; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String value) { extractedText = value; }
    public BigDecimal getExtractedAmount() { return extractedAmount; }
    public void setExtractedAmount(BigDecimal value) { extractedAmount = value; }
    public String getExtractedCurrency() { return extractedCurrency; }
    public void setExtractedCurrency(String value) { extractedCurrency = value; }
    public UUID getAttachedLcId() { return attachedLcId; }
    public void setAttachedLcId(UUID value) { attachedLcId = value; }
    public UUID getAttachedDocumentId() { return attachedDocumentId; }
    public void setAttachedDocumentId(UUID value) { attachedDocumentId = value; }
}
