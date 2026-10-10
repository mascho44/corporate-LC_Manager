package de.ostms.lc.lc.domain;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="workflow")
public class Workflow extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false) private UUID lcId;
 @Column(nullable=false,length=30) private String template;
 @Column(nullable=false,length=12) private String status="RUNNING";
 @Column(name="current_step",nullable=false) private int currentStep=1;
 @Column(name="team_id",nullable=false) private UUID teamId;
 @Column(name="approval_team_id") private UUID approvalTeamId;
 @Column(name="started_by",nullable=false,length=100) private String startedBy;
 @Column(name="started_at",nullable=false,updatable=false) private LocalDateTime startedAt=LocalDateTime.now();
 @Column(name="completed_at") private LocalDateTime completedAt;
 protected Workflow(){}
 public Workflow(UUID lcId,String template,UUID teamId,UUID approvalTeamId,String startedBy){this.lcId=lcId;this.template=template;this.teamId=teamId;this.approvalTeamId=approvalTeamId;this.startedBy=startedBy;}
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getTemplate(){return template;} public String getStatus(){return status;}
 public int getCurrentStep(){return currentStep;} public UUID getTeamId(){return teamId;} public UUID getApprovalTeamId(){return approvalTeamId;}
 public String getStartedBy(){return startedBy;} public LocalDateTime getStartedAt(){return startedAt;} public LocalDateTime getCompletedAt(){return completedAt;}
 public void advanceTo(int step){currentStep=step;}
 public void finish(String newStatus){status=newStatus;completedAt=LocalDateTime.now();}
}
