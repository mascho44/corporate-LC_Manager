package de.ostms.lc.document.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A PDF kept only for marking stamps and signatures (no LC file); belongs to the tenant. */
@Entity @Table(name="mark_training_document")
public class MarkTrainingDocument extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false) private String filename;
 @Column(name="file_size",nullable=false) private long fileSize;
 @Column(name="page_count",nullable=false) private int pageCount;
 @Column(nullable=false,columnDefinition="bytea") private byte[] content;
 @Column(name="uploaded_by",nullable=false,length=100) private String uploadedBy;
 @Column(name="uploaded_at",nullable=false) private LocalDateTime uploadedAt=LocalDateTime.now();
 public MarkTrainingDocument(){}
 public MarkTrainingDocument(String filename,int pageCount,byte[] content,String uploadedBy){this.filename=filename;this.pageCount=pageCount;this.content=content;this.fileSize=content.length;this.uploadedBy=uploadedBy;}
 public UUID getId(){return id;} public String getFilename(){return filename;} public long getFileSize(){return fileSize;} public int getPageCount(){return pageCount;}
 public byte[] getContent(){return content;} public String getUploadedBy(){return uploadedBy;} public LocalDateTime getUploadedAt(){return uploadedAt;}
}
