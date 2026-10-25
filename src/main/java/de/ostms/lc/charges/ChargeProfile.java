package de.ostms.lc.charges;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="charge_profile") public class ChargeProfile {
 @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore private UUID tenantId=de.ostms.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.ostms.lc.tenant.domain.TenantContext.require(tenantId);}
 @Id public UUID id=UUID.randomUUID();@Column(nullable=false,length=100) public String name;
 @Column(length=255) public String bankName;@Column(nullable=false,length=3) public String currency;
 @Column(nullable=false,columnDefinition="text") public String rulesJson;
 @Column(nullable=false,length=100) public String createdBy;@Column(nullable=false) public LocalDateTime createdAt=LocalDateTime.now();
}
