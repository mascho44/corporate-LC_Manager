package de.ostms.lc.calendarfeed;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Not tenant-scoped by the usual base class on purpose: the link itself decides which tenant and person a calendar request belongs to. Only the hash is stored. */
@Entity @Table(name="calendar_feed")
public class CalendarFeed {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
 @Column(nullable=false,length=100,updatable=false) private String username;
 @Column(name="token_hash",nullable=false,length=64,updatable=false) private String tokenHash;
 @Column(name="token_prefix",nullable=false,length=12,updatable=false) private String tokenPrefix;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="last_used_at") private LocalDateTime lastUsedAt;
 protected CalendarFeed(){}
 public CalendarFeed(UUID tenantId,String username,String tokenHash,String tokenPrefix){this.tenantId=tenantId;this.username=username;this.tokenHash=tokenHash;this.tokenPrefix=tokenPrefix;}
 public UUID getTenantId(){return tenantId;} public String getUsername(){return username;} public String getTokenPrefix(){return tokenPrefix;}
 public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getLastUsedAt(){return lastUsedAt;}
 public void used(LocalDateTime at){lastUsedAt=at;}
}
