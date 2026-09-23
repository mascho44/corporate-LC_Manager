package de.corporate.lc.training.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name="training_session")
public class TrainingSession {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    private String filename;
    private String contentType;
    @Basic(fetch=FetchType.LAZY) @Column(nullable=false,columnDefinition="bytea") private byte[] originalPdf;
    @Column(nullable=false,columnDefinition="text") private String extractedText;
    @Column(columnDefinition="text") private String correctedText;
    @Column(columnDefinition="text") private String reviewsJson;
    private String status;
    private String username;
    private String extractionStatus;
    private String messageType;
    private UUID lcId;
    private LocalDateTime createdAt=LocalDateTime.now();
    private LocalDateTime confirmedAt;

    public UUID getId(){return id;}
    public String getFilename(){return filename;} public void setFilename(String v){filename=v;}
    public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
    public byte[] getOriginalPdf(){return originalPdf;} public void setOriginalPdf(byte[]v){originalPdf=v;}
    public String getExtractedText(){return extractedText;} public void setExtractedText(String v){extractedText=v;}
    public String getCorrectedText(){return correctedText;} public void setCorrectedText(String v){correctedText=v;}
    public String getReviewsJson(){return reviewsJson;} public void setReviewsJson(String v){reviewsJson=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getExtractionStatus(){return extractionStatus;} public void setExtractionStatus(String v){extractionStatus=v;}
    public String getMessageType(){return messageType;} public void setMessageType(String v){messageType=v;}
    public UUID getLcId(){return lcId;} public void setLcId(UUID v){lcId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public LocalDateTime getConfirmedAt(){return confirmedAt;} public void setConfirmedAt(LocalDateTime v){confirmedAt=v;}
}
