package de.ostms.lc.capture;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="inbox_capture_info")
public class InboxCaptureInfo extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="inbox_item_id",nullable=false) private UUID inboxItemId;
 @Column(nullable=false,length=100) private String source;
 @Column(name="batch_id",length=100) private String batchId;
 @Column(length=100) private String scanner;
 @Column(name="declared_pages") private Integer declaredPages;
 @Column(name="actual_pages") private Integer actualPages;
 @Column(name="scanned_at") private LocalDateTime scannedAt;
 @Column(nullable=false,length=64) private String sha256;
 @Column(nullable=false,length=30) private String channel="SCAN_STATION";
 @Column(name="origin_reference",length=255) private String originReference;
 @Column(length=255) private String sender;
 @Column(length=255) private String subject;
 @Column(name="received_at",nullable=false) private LocalDateTime receivedAt=LocalDateTime.now();
 public InboxCaptureInfo(){}
 public InboxCaptureInfo(UUID inboxItemId,String source,String batchId,String scanner,Integer declaredPages,Integer actualPages,LocalDateTime scannedAt,String sha256){
  this.inboxItemId=inboxItemId;this.source=source;this.batchId=batchId;this.scanner=scanner;this.declaredPages=declaredPages;this.actualPages=actualPages;this.scannedAt=scannedAt;this.sha256=sha256;
 }
 /** Channel, reference (message id, file path), sender and subject of the delivery. */
 public InboxCaptureInfo origin(String channel,String reference,String sender,String subject){this.channel=channel;this.originReference=reference;this.sender=sender;this.subject=subject;return this;}
 public String getChannel(){return channel;} public String getOriginReference(){return originReference;} public String getSender(){return sender;} public String getSubject(){return subject;}
 public UUID getInboxItemId(){return inboxItemId;} public String getSource(){return source;} public String getBatchId(){return batchId;} public String getScanner(){return scanner;}
 public Integer getDeclaredPages(){return declaredPages;} public Integer getActualPages(){return actualPages;} public LocalDateTime getScannedAt(){return scannedAt;} public String getSha256(){return sha256;}
 public boolean pageCountMatches(){return declaredPages==null||actualPages==null||declaredPages.equals(actualPages);}
}
