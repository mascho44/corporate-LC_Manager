package de.corporate.lc.document.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="document_template")
public class DocumentTemplate {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=60) private DocumentType documentType;
    @Column(nullable=false,length=255) private String companyName="*";
    @Column(nullable=false) private String originalFilename;
    @Column(nullable=false,length=150) private String contentType;
    @Column(nullable=false) private long fileSize;
    @Basic(fetch=FetchType.LAZY) @Column(nullable=false,columnDefinition="bytea") @JsonIgnore private byte[] content;
    @Column(nullable=false,length=100) private String uploadedBy;
    @Column(nullable=false) private LocalDateTime uploadedAt=LocalDateTime.now();
    public UUID getId(){return id;} public DocumentType getDocumentType(){return documentType;} public void setDocumentType(DocumentType v){documentType=v;}public String getCompanyName(){return companyName;}public void setCompanyName(String v){companyName=v;} public String getOriginalFilename(){return originalFilename;} public void setOriginalFilename(String v){originalFilename=v;} public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;} public long getFileSize(){return fileSize;} public void setFileSize(long v){fileSize=v;} public byte[] getContent(){return content;} public void setContent(byte[] v){content=v;} public String getUploadedBy(){return uploadedBy;} public void setUploadedBy(String v){uploadedBy=v;} public LocalDateTime getUploadedAt(){return uploadedAt;} public void setUploadedAt(LocalDateTime v){uploadedAt=v;}
}
