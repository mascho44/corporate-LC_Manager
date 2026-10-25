package de.ostms.lc.tenant.domain;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

@MappedSuperclass
public abstract class TenantOwnedEntity {
 @Column(name="tenant_id",nullable=false,updatable=false) @JsonIgnore private UUID tenantId=TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){TenantContext.require(tenantId);}
}
