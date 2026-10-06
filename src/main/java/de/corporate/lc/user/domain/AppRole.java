package de.corporate.lc.user.domain;
import jakarta.persistence.*;import java.util.*;
@Entity @Table(name="app_role") public class AppRole {
 @Column(nullable=false,updatable=false) private UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;@Column(nullable=false,unique=true,length=100) private String name;@Column(nullable=false) private boolean systemRole;@Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private UserRole baseRole=UserRole.VIEWER;
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="app_role_permission",joinColumns=@JoinColumn(name="role_id")) @Enumerated(EnumType.STRING) @Column(name="permission",nullable=false,length=50) private Set<UserPermission> permissions=new LinkedHashSet<>();
 public UUID getId(){return id;}public String getName(){return name;}public void setName(String v){name=v;}public boolean isSystemRole(){return systemRole;}public void setSystemRole(boolean v){systemRole=v;}public UserRole getBaseRole(){return baseRole;}public void setBaseRole(UserRole v){baseRole=v;}public Set<UserPermission> getPermissions(){return permissions;}public void setPermissions(Set<UserPermission> v){permissions=v;}
}
