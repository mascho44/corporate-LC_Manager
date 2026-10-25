package de.ostms.lc.guarantee;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** The request for a guarantee to the bank: the text of the application (made as a Word letter in LCM, sent by hand) and its status until the bank has issued or refused the guarantee. */
@Entity @Table(name="guarantee_request")
public class GuaranteeRequest extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false,updatable=false) private UUID lcId;
 @Column(nullable=false,length=10) private String status="DRAFT";
 @Column(length=500) private String recipient;
 @Column(name="our_reference",length=100) private String ourReference;
 @Column(columnDefinition="text") private String wording;
 @Column(length=1000) private String remarks;
 @Column(name="sent_on") private LocalDate sentOn;
 @Column(name="bank_reference",length=100) private String bankReference;
 @Column(name="response_due") private LocalDate responseDue;
 @Column(name="status_date") private LocalDate statusDate;
 @Column(name="status_note",length=500) private String statusNote;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 protected GuaranteeRequest(){}
 public GuaranteeRequest(UUID lcId){this.lcId=lcId;}
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getStatus(){return status;} public String getRecipient(){return recipient;} public String getOurReference(){return ourReference;} public String getWording(){return wording;}
 public String getRemarks(){return remarks;} public LocalDate getSentOn(){return sentOn;} public String getBankReference(){return bankReference;} public LocalDate getResponseDue(){return responseDue;} public LocalDate getStatusDate(){return statusDate;} public String getStatusNote(){return statusNote;}
 public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void setText(String recipient,String ourReference,String wording,String remarks,LocalDate responseDue,String user){this.recipient=recipient;this.ourReference=ourReference;this.wording=wording;this.remarks=remarks;this.responseDue=responseDue;touch(user);}
 public void setStatus(String status,LocalDate date,String note,String bankReference,String user){
  this.status=status;this.statusDate=date;this.statusNote=note;if(bankReference!=null)this.bankReference=bankReference;if("SENT".equals(status)&&this.sentOn==null)this.sentOn=date;touch(user);
 }
 private void touch(String user){changedBy=user;changedAt=LocalDateTime.now();}
}
