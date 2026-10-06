package de.corporate.lc.audit.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore
    private UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
    public UUID getTenantId(){return tenantId;}
    @PrePersist private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
    @PreUpdate @PreRemove private void rejectMutation(){throw new IllegalStateException("Audit events are append-only.");}
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 100) private String username;
    @Column(nullable = false, length = 80) private String action;
    @Column(length = 80) private String entityType;
    private String entityId;
    @Column(length = 2000) private String details;
    @Column(length = 4000) private String previousValue;
    @Column(length = 4000) private String newValue;
    @Column(nullable = false) private boolean successful;
    @Column(length = 64) private String ipAddress;
    @Column(nullable = false) private LocalDateTime occurredAt = LocalDateTime.now();
    public UUID getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getAction(){return action;} public void setAction(String v){action=v;} public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;}
    public String getEntityId(){return entityId;} public void setEntityId(String v){entityId=v;} public String getDetails(){return details;} public void setDetails(String v){details=v;}
    public String getPreviousValue(){return previousValue;} public void setPreviousValue(String v){previousValue=v;} public String getNewValue(){return newValue;} public void setNewValue(String v){newValue=v;}
    public boolean isSuccessful(){return successful;} public void setSuccessful(boolean v){successful=v;} public String getIpAddress(){return ipAddress;} public void setIpAddress(String v){ipAddress=v;}
    public LocalDateTime getOccurredAt(){return occurredAt;}
}
