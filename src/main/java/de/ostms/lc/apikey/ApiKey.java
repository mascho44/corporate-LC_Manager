package de.ostms.lc.apikey;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Not tenant-scoped by the usual base class on purpose: the key itself decides which tenant an integration request belongs to. Only the hash is stored. */
@Entity @Table(name="api_key")
public class ApiKey {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
 @Column(nullable=false,length=100) private String name;
 @Column(name="key_hash",nullable=false,length=64,updatable=false) private String keyHash;
 @Column(name="key_prefix",nullable=false,length=12,updatable=false) private String keyPrefix;
 @Column(nullable=false,length=100) private String scopes;
 @Column(name="created_by",length=100) private String createdBy;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="last_used_at") private LocalDateTime lastUsedAt;
 @Column(name="revoked_at") private LocalDateTime revokedAt;
 protected ApiKey(){}
 public ApiKey(UUID tenantId,String name,String keyHash,String keyPrefix,java.util.List<String> scopes,String createdBy){this.tenantId=tenantId;this.name=name;this.keyHash=keyHash;this.keyPrefix=keyPrefix;this.scopes=String.join(",",scopes);this.createdBy=createdBy;}
 public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getName(){return name;} public String getKeyPrefix(){return keyPrefix;}
 public java.util.List<String> scopes(){return java.util.Arrays.asList(scopes.split(","));}
 public String getCreatedBy(){return createdBy;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getLastUsedAt(){return lastUsedAt;} public LocalDateTime getRevokedAt(){return revokedAt;}
 public boolean isRevoked(){return revokedAt!=null;}
 public void revoke(){if(revokedAt==null)revokedAt=LocalDateTime.now();}
 public void used(LocalDateTime at){lastUsedAt=at;}
}
