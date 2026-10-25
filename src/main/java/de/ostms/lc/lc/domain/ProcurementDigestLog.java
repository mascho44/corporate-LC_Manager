package de.ostms.lc.lc.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="procurement_digest_log")
public class ProcurementDigestLog extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="sent_on",nullable=false) private LocalDate sentOn;
 @Column(nullable=false) private int recipients;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public ProcurementDigestLog(){}
 public ProcurementDigestLog(LocalDate sentOn){this.sentOn=sentOn;}
 public UUID getId(){return id;} public LocalDate getSentOn(){return sentOn;} public int getRecipients(){return recipients;}
 public void setRecipients(int recipients){this.recipients=recipients;}
}
