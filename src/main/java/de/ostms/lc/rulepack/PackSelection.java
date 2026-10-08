package de.ostms.lc.rulepack;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="internal_rule_pack")
@IdClass(PackSelection.Key.class)
public class PackSelection {
 @Id @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore private UUID tenantId=de.ostms.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.ostms.lc.tenant.domain.TenantContext.require(tenantId);}
 public static class Key implements java.io.Serializable {
  public UUID tenantId;public String id;
  public Key(){} public Key(UUID tenantId,String id){this.tenantId=tenantId;this.id=id;}
  @Override public boolean equals(Object other){return other instanceof Key key&&java.util.Objects.equals(tenantId,key.tenantId)&&java.util.Objects.equals(id,key.id);}
  @Override public int hashCode(){return java.util.Objects.hash(tenantId,id);}
 }
 @Id @Column(length=31) public String id;
 public UUID activeVersionId;
 public UUID previousVersionId;
 @Version public long lockVersion;
}
