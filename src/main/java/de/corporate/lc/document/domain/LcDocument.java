package de.corporate.lc.document.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.corporate.lc.lc.domain.LetterOfCredit;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lc_document")
public class LcDocument {
    @Column(columnDefinition="text") @JsonIgnore private String ruleFactsJson;
    public String getRuleFactsJson(){return ruleFactsJson;}
    public void setRuleFactsJson(String value){ruleFactsJson=value;}
    @Column(columnDefinition="text") @JsonIgnore private String classificationHistoryJson;
    public String getClassificationHistoryJson(){return classificationHistoryJson;}
    public void setClassificationHistoryJson(String value){classificationHistoryJson=value;}
    @Column(columnDefinition="text") @JsonIgnore private String ocrEvidenceJson;
    public String getOcrEvidenceJson(){return ocrEvidenceJson;}
    public void setOcrEvidenceJson(String value){ocrEvidenceJson=value;}
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "lc_id") @JsonIgnore
    private LetterOfCredit letterOfCredit;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentType documentType;
    @Column(nullable = false) private String originalFilename;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private long fileSize;
    private LocalDate documentDate;
    @Column(precision = 19, scale = 2) private BigDecimal amount;
    private String currency;
    @Column(length = 100) private String extractedReference;
    @Column(length = 100) private String extractedDocumentNumber;
    @Column(precision = 19, scale = 2) private BigDecimal extractedAmount;
    @Column(length = 3) private String extractedCurrency;
    @Column(length = 30, nullable = false) private String extractionStatus = "NOT_PROCESSED";
    @Column(columnDefinition = "text") @JsonIgnore private String extractedText;
    @Basic(fetch = FetchType.LAZY) @Column(nullable = false, columnDefinition = "bytea") @JsonIgnore private byte[] content;
    @Column(nullable = false) private LocalDateTime uploadedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public LetterOfCredit getLetterOfCredit() { return letterOfCredit; }
    public void setLetterOfCredit(LetterOfCredit value) { letterOfCredit = value; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType value) { documentType = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long value) { fileSize = value; }
    public LocalDate getDocumentDate() { return documentDate; }
    public void setDocumentDate(LocalDate value) { documentDate = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public String getExtractedReference() { return extractedReference; }
    public void setExtractedReference(String value) { extractedReference = value; }
    public String getExtractedDocumentNumber() { return extractedDocumentNumber; }
    public void setExtractedDocumentNumber(String value) { extractedDocumentNumber = value; }
    public BigDecimal getExtractedAmount() { return extractedAmount; }
    public void setExtractedAmount(BigDecimal value) { extractedAmount = value; }
    public String getExtractedCurrency() { return extractedCurrency; }
    public void setExtractedCurrency(String value) { extractedCurrency = value; }
    public String getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(String value) { extractionStatus = value; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String value) { extractedText = value; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] value) { content = value; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
