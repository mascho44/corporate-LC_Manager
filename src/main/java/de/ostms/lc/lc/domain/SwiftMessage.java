package de.ostms.lc.lc.domain;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A stored MT199/MT799 free-format message, linked to a dossier when its reference matches one. */
@Entity @Table(name="swift_message")
public class SwiftMessage extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id") private UUID lcId;
 @Column(name="message_type",nullable=false,length=10) private String messageType;
 @Column(nullable=false,length=35) private String reference;
 @Column(name="related_reference",length=35) private String relatedReference;
 @Column(nullable=false,columnDefinition="text") private String narrative;
 @Column(name="raw_message",nullable=false,columnDefinition="text") private String rawMessage;
 @Column(nullable=false,length=20) private String source="IMPORT";
 @Column(name="imported_by",length=100) private String importedBy;
 @Column(name="imported_at",nullable=false,updatable=false) private LocalDateTime importedAt=LocalDateTime.now();
 protected SwiftMessage(){}
 public SwiftMessage(UUID lcId,String messageType,String reference,String relatedReference,String narrative,String rawMessage,String source,String importedBy){
  this.lcId=lcId;this.messageType=messageType;this.reference=reference;this.relatedReference=relatedReference;this.narrative=narrative;this.rawMessage=rawMessage;this.source=source;this.importedBy=importedBy;
 }
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getMessageType(){return messageType;} public String getReference(){return reference;}
 public String getRelatedReference(){return relatedReference;} public String getNarrative(){return narrative;} public String getSource(){return source;}
 public String getImportedBy(){return importedBy;} public LocalDateTime getImportedAt(){return importedAt;}
 @com.fasterxml.jackson.annotation.JsonIgnore public String getRawMessage(){return rawMessage;}
 public void link(UUID lc){lcId=lc;}
}
