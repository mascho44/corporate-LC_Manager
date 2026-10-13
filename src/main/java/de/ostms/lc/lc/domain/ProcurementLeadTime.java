package de.ostms.lc.lc.domain;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Working days a document type needs before the latest date of shipment; overrides the built-in default for one tenant. */
@Entity @Table(name="procurement_lead_time")
public class ProcurementLeadTime extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Enumerated(EnumType.STRING) @Column(name="document_type",nullable=false,length=60) private DocumentType documentType;
 @Column(name="working_days",nullable=false) private int workingDays;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public ProcurementLeadTime(){}
 public ProcurementLeadTime(DocumentType type){this.documentType=type;}
 public UUID getId(){return id;} public DocumentType getDocumentType(){return documentType;} public int getWorkingDays(){return workingDays;}
 public void change(int days,String user){this.workingDays=days;this.changedBy=user;this.changedAt=LocalDateTime.now();}
}
