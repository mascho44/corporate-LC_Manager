package de.ostms.lc.check.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A step of the guided examination of one file that was completed or skipped (with a reason). */
@Entity @Table(name="lc_review_step",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","lc_id","step_key"}))
public class LcReviewStep extends TenantOwnedEntity {
 public static final String DONE="DONE",SKIPPED="SKIPPED";
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false) private UUID lcId;
 @Column(name="step_key",nullable=false,length=30) private String stepKey;
 @Column(nullable=false,length=10) private String status;
 @Column(length=500) private String reason;
 @Column(name="completed_by",nullable=false,length=100) private String completedBy;
 @Column(name="completed_at",nullable=false) private LocalDateTime completedAt=LocalDateTime.now();
 public LcReviewStep(){}
 public LcReviewStep(UUID lcId,String stepKey,String status,String reason,String completedBy){this.lcId=lcId;this.stepKey=stepKey;this.status=status;this.reason=reason;this.completedBy=completedBy;}
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getStepKey(){return stepKey;} public String getStatus(){return status;}
 public String getReason(){return reason;} public String getCompletedBy(){return completedBy;} public LocalDateTime getCompletedAt(){return completedAt;}
 public void set(String status,String reason,String completedBy){this.status=status;this.reason=reason;this.completedBy=completedBy;this.completedAt=LocalDateTime.now();}
}
