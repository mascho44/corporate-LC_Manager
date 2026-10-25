package de.ostms.lc.capture;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Not tenant-scoped by the usual base class on purpose: the token itself decides which tenant a machine request belongs to. */
@Entity @Table(name="capture_token")
public class CaptureToken {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
 @Column(nullable=false,length=100) private String name;
 @Column(name="token_hash",nullable=false,length=64,updatable=false) private String tokenHash;
 @Column(name="token_prefix",nullable=false,length=12,updatable=false) private String tokenPrefix;
 @Column(name="created_by",length=100) private String createdBy;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="last_used_at") private LocalDateTime lastUsedAt;
 @Column(name="revoked_at") private LocalDateTime revokedAt;
 protected CaptureToken(){}
 public CaptureToken(UUID tenantId,String name,String tokenHash,String tokenPrefix,String createdBy){this.tenantId=tenantId;this.name=name;this.tokenHash=tokenHash;this.tokenPrefix=tokenPrefix;this.createdBy=createdBy;}
 public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getName(){return name;} public String getTokenPrefix(){return tokenPrefix;}
 public String getCreatedBy(){return createdBy;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getLastUsedAt(){return lastUsedAt;} public LocalDateTime getRevokedAt(){return revokedAt;}
 public boolean isRevoked(){return revokedAt!=null;}
 public void revoke(){if(revokedAt==null)revokedAt=LocalDateTime.now();}
 public void used(LocalDateTime at){lastUsedAt=at;}
}
