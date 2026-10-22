package de.ostms.lc.sla;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** One service level of a tenant: an activity whose clock starts with one of the start actions and stops with one of the stop actions (audit actions on the file). */
@Entity @Table(name="sla_rule")
public class SlaRule extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="activity_key",nullable=false,length=40) private String activityKey;
 @Column(nullable=false,length=120) private String name;
 @Column(name="start_actions",nullable=false,length=400) private String startActions;
 @Column(name="stop_actions",nullable=false,length=400) private String stopActions;
 @Column(name="target_days",nullable=false) private int targetDays;
 @Column(name="warn_percent",nullable=false) private int warnPercent=80;
 @Column(nullable=false) private boolean active=true;
 @Column(name="escalation_team_id") private UUID escalationTeamId;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public UUID getId(){return id;} public String getActivityKey(){return activityKey;} public String getName(){return name;}
 public java.util.List<String> startActions(){return split(startActions);} public java.util.List<String> stopActions(){return split(stopActions);}
 public int getTargetDays(){return targetDays;} public int getWarnPercent(){return warnPercent;} public boolean isActive(){return active;} public UUID getEscalationTeamId(){return escalationTeamId;}
 public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 static java.util.List<String> split(String value){return value==null||value.isBlank()?java.util.List.of():java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();}
 public void set(String activityKey,String name,java.util.List<String> start,java.util.List<String> stop,int targetDays,int warnPercent,boolean active,UUID escalationTeamId,String user){
  this.activityKey=activityKey;this.name=name;this.startActions=String.join(",",start);this.stopActions=String.join(",",stop);this.targetDays=targetDays;this.warnPercent=warnPercent;this.active=active;this.escalationTeamId=escalationTeamId;this.changedBy=user;this.changedAt=LocalDateTime.now();
 }
}
