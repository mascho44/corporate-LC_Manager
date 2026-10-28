package de.ostms.lc.check.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** The draft of the credit kept with the file, so the credit that arrives later can be compared with it. */
@Entity @Table(name="lc_draft",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","lc_id"}))
public class LcDraft extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false) private UUID lcId;
 @Column(length=255) private String filename;
 @Column(nullable=false,columnDefinition="text") private String content;
 @Column(name="saved_by",nullable=false,length=100) private String savedBy;
 @Column(name="saved_at",nullable=false) private LocalDateTime savedAt=LocalDateTime.now();
 public LcDraft(){}
 public LcDraft(UUID lcId,String filename,String content,String savedBy){this.lcId=lcId;this.filename=filename;this.content=content;this.savedBy=savedBy;}
 public UUID getLcId(){return lcId;} public String getFilename(){return filename;} public String getContent(){return content;} public String getSavedBy(){return savedBy;} public LocalDateTime getSavedAt(){return savedAt;}
 public void replace(String filename,String content,String savedBy){this.filename=filename;this.content=content;this.savedBy=savedBy;this.savedAt=LocalDateTime.now();}
}
