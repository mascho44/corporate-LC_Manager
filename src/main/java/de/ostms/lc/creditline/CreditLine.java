package de.ostms.lc.creditline;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A line (guarantee line) a bank has granted the company: a limit per bank and currency, optionally for one type of guarantee. */
@Entity @Table(name="credit_line")
public class CreditLine extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false,length=200) private String bank;
 @Column(length=11) private String bic;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(name="guarantee_type",length=20) private String guaranteeType;
 @Column(name="valid_until") private LocalDate validUntil;
 @Column(name="warn_percent",nullable=false) private int warnPercent=80;
 @Column(nullable=false) private boolean active=true;
 @Column(length=500) private String note;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public UUID getId(){return id;} public String getName(){return name;} public String getBank(){return bank;} public String getBic(){return bic;} public String getCurrency(){return currency;} public BigDecimal getAmount(){return amount;}
 public String getGuaranteeType(){return guaranteeType;} public LocalDate getValidUntil(){return validUntil;} public int getWarnPercent(){return warnPercent;} public boolean isActive(){return active;} public String getNote(){return note;}
 public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void set(String name,String bank,String bic,String currency,BigDecimal amount,String guaranteeType,LocalDate validUntil,int warnPercent,boolean active,String note,String user){
  this.name=name;this.bank=bank;this.bic=bic;this.currency=currency;this.amount=amount;this.guaranteeType=guaranteeType;this.validUntil=validUntil;this.warnPercent=warnPercent;this.active=active;this.note=note;this.changedBy=user;this.changedAt=LocalDateTime.now();
 }
}
