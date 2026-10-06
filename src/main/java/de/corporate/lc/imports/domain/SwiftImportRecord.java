package de.corporate.lc.imports.domain;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="swift_import_record") public class SwiftImportRecord {
 @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore private UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; private String filename; private String messageType; private String reference; private String status; @Column(length=2000) private String message; private LocalDateTime importedAt=LocalDateTime.now();
 public UUID getId(){return id;} public String getFilename(){return filename;} public void setFilename(String v){filename=v;} public String getMessageType(){return messageType;} public void setMessageType(String v){messageType=v;} public String getReference(){return reference;} public void setReference(String v){reference=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getMessage(){return message;} public void setMessage(String v){message=v;} public LocalDateTime getImportedAt(){return importedAt;}
}
