package de.ostms.lc.lc.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Manual working status of one required document of an LC. Delivery itself is derived from the uploaded documents. */
@Entity @Table(name="procurement_item_status")
public class ProcurementItemStatus extends TenantOwnedEntity {
 public enum Status { NOT_STARTED, IN_PROGRESS, REQUESTED }
 @Id private UUID id=UUID.randomUUID();
 @Column(name="letter_of_credit_id",nullable=false) private UUID lcId;
 @Column(nullable=false,length=2000) private String requirement;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.NOT_STARTED;
 @Column(length=500) private String note;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public ProcurementItemStatus(){}
 public ProcurementItemStatus(UUID lcId,String requirement){this.lcId=lcId;this.requirement=requirement;}
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getRequirement(){return requirement;}
 public Status getStatus(){return status;} public String getNote(){return note;}
 public void change(Status status,String note,String user){this.status=status;this.note=note;this.changedBy=user;this.changedAt=LocalDateTime.now();}
}
