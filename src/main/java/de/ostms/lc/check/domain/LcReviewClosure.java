package de.ostms.lc.check.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

/** The closure of the examination of one file: started by one person, completed once enough different people have signed off. */
@Entity @Table(name="lc_review_closure",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","lc_id"}))
public class LcReviewClosure extends TenantOwnedEntity {
 public static final String PENDING="PENDING",COMPLETED="COMPLETED";
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false) private UUID lcId;
 @Column(nullable=false,length=10) private String status=PENDING;
 @Column(name="required_closers",nullable=false) private int requiredClosers;
 @Column(nullable=false,length=1900) private String summary;
 @Column(name="started_by",nullable=false,length=100) private String startedBy;
 @Column(nullable=false,length=600) private String signers;
 @Column(name="started_at",nullable=false) private LocalDateTime startedAt=LocalDateTime.now();
 @Column(name="completed_at") private LocalDateTime completedAt;
 public LcReviewClosure(){}
 public LcReviewClosure(UUID lcId,int requiredClosers,String summary,String startedBy){this.lcId=lcId;this.requiredClosers=requiredClosers;this.summary=summary;this.startedBy=startedBy;this.signers=startedBy;}
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getStatus(){return status;} public int getRequiredClosers(){return requiredClosers;}
 public String getSummary(){return summary;} public String getStartedBy(){return startedBy;} public LocalDateTime getStartedAt(){return startedAt;} public LocalDateTime getCompletedAt(){return completedAt;}
 public List<String> signerList(){return signers.isBlank()?List.of():Arrays.asList(signers.split(","));}
 public boolean hasSigned(String user){return signerList().stream().anyMatch(s->s.equalsIgnoreCase(user));}
 public void sign(String user){signers=signers.isBlank()?user:signers+","+user;}
 public void complete(){status=COMPLETED;completedAt=LocalDateTime.now();}
}
