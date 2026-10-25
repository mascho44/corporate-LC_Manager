package de.ostms.lc.customer;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A customer of the tenant. Numbers are kept as entered; IBAN is stored without spaces in capitals. */
@Entity @Table(name="customer")
public class Customer extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=255) private String name;
 @Column(name="customer_number",length=60) private String customerNumber;
 @Column(name="account_number",length=60) private String accountNumber;
 @Column(length=34) private String iban;
 @Column(length=11) private String bic;
 @Column(nullable=false,length=30) private String source="MANUAL";
 @Column(name="external_ref",length=100) private String externalRef;
 @Column(name="created_by",length=100) private String createdBy;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 public Customer(){}
 public UUID getId(){return id;} public String getName(){return name;} public String getCustomerNumber(){return customerNumber;} public String getAccountNumber(){return accountNumber;}
 public String getIban(){return iban;} public String getBic(){return bic;} public String getSource(){return source;} public String getExternalRef(){return externalRef;}
 public String getCreatedBy(){return createdBy;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getChangedAt(){return changedAt;}
 public void set(String name,String customerNumber,String accountNumber,String iban,String bic,String user){
  this.name=name;this.customerNumber=customerNumber;this.accountNumber=accountNumber;this.iban=iban;this.bic=bic;if(createdBy==null)createdBy=user;this.changedAt=LocalDateTime.now();
 }
}
