package de.ostms.lc.check.domain;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** From this LC amount (per currency) on, the examination must be closed by this many different people. */
@Entity @Table(name="review_close_threshold",uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","currency","minimum_amount"}))
public class ReviewCloseThreshold extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=3) private String currency;
 @Column(name="minimum_amount",nullable=false,precision=19,scale=2) private BigDecimal minimumAmount;
 @Column(name="required_closers",nullable=false) private int requiredClosers;
 public ReviewCloseThreshold(){}
 public ReviewCloseThreshold(String currency,BigDecimal minimumAmount,int requiredClosers){this.currency=currency;this.minimumAmount=minimumAmount;this.requiredClosers=requiredClosers;}
 public String getCurrency(){return currency;} public BigDecimal getMinimumAmount(){return minimumAmount;} public int getRequiredClosers(){return requiredClosers;}
}
