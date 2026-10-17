package de.ostms.lc.intake;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A channel of a tenant that fetches files into the document inbox by itself. */
@Entity @Table(name="intake_channel")
public class IntakeChannelConfig extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=20) private String type;
 @Column(nullable=false,length=40) private String name;
 @Column(nullable=false) private boolean enabled=false;
 @Column(name="interval_minutes",nullable=false) private int intervalMinutes=2;
 @Column(name="last_run_at") private LocalDateTime lastRunAt;
 @Column(name="last_result",length=500) private String lastResult;
 @Column(name="created_by",nullable=false,length=100) private String createdBy;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt=LocalDateTime.now();
 public IntakeChannelConfig(){}
 public IntakeChannelConfig(String type,String name,boolean enabled,int intervalMinutes,String createdBy){this.type=type;this.name=name;this.enabled=enabled;this.intervalMinutes=intervalMinutes;this.createdBy=createdBy;}
 public UUID getId(){return id;} public String getType(){return type;} public String getName(){return name;} public boolean isEnabled(){return enabled;}
 public int getIntervalMinutes(){return intervalMinutes;} public LocalDateTime getLastRunAt(){return lastRunAt;} public String getLastResult(){return lastResult;}
 public void update(boolean enabled,int intervalMinutes){this.enabled=enabled;this.intervalMinutes=intervalMinutes;}
 public void recordRun(String result,LocalDateTime now){lastRunAt=now;lastResult=result==null?null:result.length()>480?result.substring(0,480):result;}
 /** True when the channel is on and its interval since the last run has elapsed. */
 public boolean due(LocalDateTime now){return enabled&&(lastRunAt==null||!lastRunAt.plusMinutes(intervalMinutes).isAfter(now));}
 @PreUpdate private void touch(){updatedAt=LocalDateTime.now();}
}
