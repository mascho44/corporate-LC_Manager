package de.ostms.lc.guarantee;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Details of a guarantee the company has had issued by a bank for a beneficiary; the guarantee itself is a file (letter_of_credit with instrument type GUARANTEE). */
@Entity @Table(name="guarantee_detail")
public class GuaranteeDetail extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="lc_id",nullable=false,updatable=false) private UUID lcId;
 @Column(name="guarantee_type",nullable=false,length=20) private String guaranteeType="OTHER";
 @Column(name="valid_from") private LocalDate validFrom;
 @Column(name="claim_until") private LocalDate claimUntil;
 @Column(name="auto_extension",nullable=false) private boolean autoExtension;
 @Column(name="extension_notice_days") private Integer extensionNoticeDays;
 @Column(length=500) private String underlying;
 @Column(name="beneficiary_reference",length=100) private String beneficiaryReference;
 @Column(name="bank_reference",length=100) private String bankReference;
 @Column(nullable=false,length=10) private String status="ACTIVE";
 @Column(name="status_date") private LocalDate statusDate;
 @Column(name="status_note",length=500) private String statusNote;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 protected GuaranteeDetail(){}
 public GuaranteeDetail(UUID lcId){this.lcId=lcId;}
 public UUID getId(){return id;} public UUID getLcId(){return lcId;} public String getGuaranteeType(){return guaranteeType;} public LocalDate getValidFrom(){return validFrom;} public LocalDate getClaimUntil(){return claimUntil;}
 public boolean isAutoExtension(){return autoExtension;} public Integer getExtensionNoticeDays(){return extensionNoticeDays;} public String getUnderlying(){return underlying;} public String getBeneficiaryReference(){return beneficiaryReference;}
 public String getStatus(){return status;} public LocalDate getStatusDate(){return statusDate;} public String getStatusNote(){return statusNote;} public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void set(String type,LocalDate validFrom,LocalDate claimUntil,boolean autoExtension,Integer noticeDays,String underlying,String beneficiaryReference,String user){
  this.guaranteeType=type;this.validFrom=validFrom;this.claimUntil=claimUntil;this.autoExtension=autoExtension;this.extensionNoticeDays=noticeDays;this.underlying=underlying;this.beneficiaryReference=beneficiaryReference;touch(user);
 }
 public String getBankReference(){return bankReference;}
 public void setBankReference(String value,String user){this.bankReference=value;touch(user);}
 public void setStatus(String status,LocalDate date,String note,String user){this.status=status;this.statusDate=date;this.statusNote=note;touch(user);}
 private void touch(String user){this.changedBy=user;this.changedAt=LocalDateTime.now();}
 /** The date by which the company has to decide on extension or return: the expiry minus the notice period; null without a period. */
 public LocalDate decisionDate(LocalDate expiry){return expiry==null||extensionNoticeDays==null?null:expiry.minusDays(extensionNoticeDays);}
}
