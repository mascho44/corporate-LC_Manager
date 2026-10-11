package de.ostms.lc.ai;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="tenant_ai_setting")
public class TenantAiSetting extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false) private boolean enabled;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public TenantAiSetting(){}
 public boolean isEnabled(){return enabled;} public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void change(boolean enabled,String user){this.enabled=enabled;this.changedBy=user;this.changedAt=LocalDateTime.now();}
}
