package de.corporate.lc.rulepack;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="internal_rule_pack_version",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","pack_id","pack_version"}))
public class StoredPackVersion {
 @Column(name="tenant_id",nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore private UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
 @Id public UUID id=UUID.randomUUID();
 @Column(name="pack_id",nullable=false,length=31,updatable=false) public String packId;
 @Column(name="pack_version",nullable=false,length=11,updatable=false) public String version;
 @Column(nullable=false,columnDefinition="text",updatable=false) public String definitionJson;
 @Column(nullable=false,length=64,updatable=false) public String checksum;
 @Column(nullable=false,updatable=false) public boolean testsPassed;
 @Column(nullable=false,length=100,updatable=false) public String importedBy;
 @Column(nullable=false,updatable=false) public LocalDateTime importedAt=LocalDateTime.now();
}
