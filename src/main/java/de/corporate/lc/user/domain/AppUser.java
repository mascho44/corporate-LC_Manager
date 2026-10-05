package de.corporate.lc.user.domain;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="app_user") public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,unique=true,length=100) private String username;
 @Column(nullable=false) private String displayName;
 @Column(length=255) private String email;
 public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
 @Column(nullable=false) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private UserRole role=UserRole.USER;
 @Column(nullable=false) private boolean active=true;
 private boolean totpEnabled=false;
 @Column(columnDefinition="text") private String totpSecretEncrypted;
 @Column(columnDefinition="text") private String recoveryCodeHashes;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="assigned_role_id") private AppRole assignedRole;
 @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public UUID getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public UserRole getRole(){return role;} public void setRole(UserRole v){role=v;} public AppRole getAssignedRole(){return assignedRole;} public void setAssignedRole(AppRole v){assignedRole=v;if(v!=null)role=v.getBaseRole();} public java.util.Set<UserPermission> effectivePermissions(){return assignedRole==null?UserPermission.defaults(role):java.util.Set.copyOf(assignedRole.getPermissions());} public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public boolean isTotpEnabled(){return totpEnabled;} public void setTotpEnabled(boolean v){totpEnabled=v;} public String getTotpSecretEncrypted(){return totpSecretEncrypted;} public void setTotpSecretEncrypted(String v){totpSecretEncrypted=v;} public String getRecoveryCodeHashes(){return recoveryCodeHashes;} public void setRecoveryCodeHashes(String v){recoveryCodeHashes=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
