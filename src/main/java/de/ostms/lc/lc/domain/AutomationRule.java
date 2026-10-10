package de.ostms.lc.lc.domain;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.util.UUID;

/** Per tenant and trigger: whether automatic tasks are created and for which team. */
@Entity @Table(name="automation_rule")
public class AutomationRule extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="trigger_type",nullable=false,length=20) private String trigger;
 @Column(nullable=false) private boolean enabled;
 @Column(name="team_id") private UUID teamId;
 @Column(name="lead_days",nullable=false) private int leadDays=3;
 protected AutomationRule(){}
 public AutomationRule(String trigger){this.trigger=trigger;}
 public UUID getId(){return id;} public String getTrigger(){return trigger;} public boolean isEnabled(){return enabled;} public UUID getTeamId(){return teamId;} public int getLeadDays(){return leadDays;}
 public void configure(boolean enabled,UUID teamId,int leadDays){this.enabled=enabled;this.teamId=teamId;this.leadDays=leadDays;}
}
