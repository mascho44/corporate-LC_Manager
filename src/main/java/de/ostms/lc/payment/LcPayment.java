package de.ostms.lc.payment;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A payment received for a file, entered by a person or reported by another system through the API. LCM does not read bank statements. */
@Entity @Table(name="lc_payment")
public class LcPayment extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false,updatable=false) private UUID lcId;
 @Column(name="received_on",nullable=false) private LocalDate receivedOn;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(length=100) private String reference;
 @Column(length=500) private String note;
 @Column(nullable=false,length=10) private String source;
 @Column(name="recorded_by",length=100) private String recordedBy;
 @Column(name="recorded_at",nullable=false) private LocalDateTime recordedAt=LocalDateTime.now();
 protected LcPayment(){}
 public LcPayment(UUID lcId,LocalDate receivedOn,BigDecimal amount,String currency,String reference,String note,String source,String recordedBy){
  this.lcId=lcId;this.receivedOn=receivedOn;this.amount=amount;this.currency=currency;this.reference=reference;this.note=note;this.source=source;this.recordedBy=recordedBy;
 }
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public LocalDate getReceivedOn(){return receivedOn;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;}
 public String getReference(){return reference;} public String getNote(){return note;} public String getSource(){return source;} public String getRecordedBy(){return recordedBy;} public LocalDateTime getRecordedAt(){return recordedAt;}
}
