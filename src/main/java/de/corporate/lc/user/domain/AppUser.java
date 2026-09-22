package de.corporate.lc.user.domain;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="app_user") public class AppUser {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,unique=true,length=100) private String username;
 @Column(nullable=false) private String displayName;
 @Column(nullable=false) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private UserRole role=UserRole.USER;
 @Column(nullable=false) private boolean active=true;
 @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public UUID getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public UserRole getRole(){return role;} public void setRole(UserRole v){role=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
