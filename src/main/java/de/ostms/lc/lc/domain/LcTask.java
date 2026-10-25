package de.ostms.lc.lc.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="lc_task")
public class LcTask extends de.ostms.lc.tenant.domain.TenantOwnedEntity {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="letter_of_credit_id") private UUID letterOfCreditId;
    @Column(nullable=false,length=500) private String title;
    @Column(length=100) private String assignedTo;
    private LocalDate dueDate;
    @Column(nullable=false) private boolean completed;
    @Column(nullable=false,length=100) private String createdBy;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
    private LocalDateTime completedAt;
    @Column(name="subject_type",length=20) private String subjectType;
    @Column(name="subject_id") private UUID subjectId;
    @Column(nullable=false,length=20) private String source="MANUAL";
    @Column(name="auto_key",length=120) private String autoKey;
    public String getSubjectType(){return subjectType;} public UUID getSubjectId(){return subjectId;} public String getSource(){return source;} public String getAutoKey(){return autoKey;}
    public void setSubject(String type,UUID id){subjectType=type;subjectId=id;}
    public void setSource(String v){source=v;} public void setAutoKey(String v){autoKey=v;}
    @Column(name="workflow_id") private UUID workflowId;
    @Column(name="step_no") private Integer stepNo;
    @Column(name="step_key",length=30) private String stepKey;
    @Column(name="four_eyes",nullable=false) private boolean fourEyes;
    @Column(name="completed_by",length=100) private String completedBy;
    public UUID getWorkflowId(){return workflowId;} public void setWorkflowId(UUID v){workflowId=v;}
    public Integer getStepNo(){return stepNo;} public void setStepNo(Integer v){stepNo=v;}
    public String getStepKey(){return stepKey;} public void setStepKey(String v){stepKey=v;}
    public boolean isFourEyes(){return fourEyes;} public void setFourEyes(boolean v){fourEyes=v;}
    public String getCompletedBy(){return completedBy;} public void setCompletedBy(String v){completedBy=v;}
    @Column(name="team_id") private UUID teamId;
    @Column(name="claimed_at") private LocalDateTime claimedAt;
    public UUID getTeamId(){return teamId;} public void setTeamId(UUID v){teamId=v;}
    public LocalDateTime getClaimedAt(){return claimedAt;} public void setClaimedAt(LocalDateTime v){claimedAt=v;}
    public UUID getId(){return id;} public UUID getLetterOfCreditId(){return letterOfCreditId;} public void setLetterOfCreditId(UUID v){letterOfCreditId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getAssignedTo(){return assignedTo;} public void setAssignedTo(String v){assignedTo=v;} public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;} public boolean isCompleted(){return completed;} public void setCompleted(boolean v){completed=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
