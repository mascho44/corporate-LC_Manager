package de.ostms.lc.document.domain;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="tenant_scan_profile")
public class TenantScanProfile extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=30) private String profile;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public TenantScanProfile(){}
 public String getProfile(){return profile;} public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void change(String profile,String user){this.profile=profile;this.changedBy=user;this.changedAt=LocalDateTime.now();}
}
