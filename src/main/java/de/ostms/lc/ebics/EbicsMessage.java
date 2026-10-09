package de.ostms.lc.ebics;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="ebics_message")
public class EbicsMessage extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="message_type",nullable=false,length=10) private String messageType;
 @Column(nullable=false,length=64) private String sha256;
 @Column(nullable=false,columnDefinition="text") private String content;
 @Column(nullable=false,length=12) private String status="NEW";
 @Column(length=500) private String note;
 @Column(name="received_at",nullable=false,updatable=false) private LocalDateTime receivedAt=LocalDateTime.now();
 @Column(name="handled_at") private LocalDateTime handledAt;
 @Column(name="handled_by",length=100) private String handledBy;
 protected EbicsMessage(){}
 public EbicsMessage(String messageType,String sha256,String content){this.messageType=messageType;this.sha256=sha256;this.content=content;}
 public UUID getId(){return id;} public String getMessageType(){return messageType;} public String getSha256(){return sha256;}
 public String getContent(){return content;} public String getStatus(){return status;} public String getNote(){return note;}
 public LocalDateTime getReceivedAt(){return receivedAt;} public LocalDateTime getHandledAt(){return handledAt;} public String getHandledBy(){return handledBy;}
 public void handle(String newStatus,String user,String text){status=newStatus;handledBy=user;handledAt=LocalDateTime.now();note=text==null?null:(text.length()>480?text.substring(0,480):text);}
}
