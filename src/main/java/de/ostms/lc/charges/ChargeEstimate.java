package de.ostms.lc.charges;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="charge_estimate") public class ChargeEstimate {
 @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore private UUID tenantId=de.ostms.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.ostms.lc.tenant.domain.TenantContext.require(tenantId);}
 @Id public UUID id=UUID.randomUUID();@Column(nullable=false) public UUID lcId;
 @Column(nullable=false) public UUID profileId;@Column(nullable=false,columnDefinition="text") public String resultJson;
 @Column(nullable=false,columnDefinition="text") public String profileSnapshot;
 @Column(nullable=false,length=100) public String createdBy;@Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now();
}
