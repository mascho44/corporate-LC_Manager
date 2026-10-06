package de.corporate.lc.lc.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;
@Entity @Table(name="letter_of_credit")
public class LetterOfCredit {
 @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore
 private UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
 public UUID getTenantId(){return tenantId;}
 @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
 @Column(columnDefinition="text") @com.fasterxml.jackson.annotation.JsonIgnore private String ruleFactsJson;
 public String getRuleFactsJson(){return ruleFactsJson;}
 public void setRuleFactsJson(String value){ruleFactsJson=value;}
 @Column(columnDefinition="text") @com.fasterxml.jackson.annotation.JsonIgnore private String ruleRequirementsJson;
 public String getRuleRequirementsJson(){return ruleRequirementsJson;}
 public void setRuleRequirementsJson(String value){ruleRequirementsJson=value;}
 @ElementCollection @CollectionTable(name="lc_condition",joinColumns=@JoinColumn(name="lc_id"))
 @MapKeyEnumerated(EnumType.STRING) @MapKeyColumn(name="condition_name",length=100)
 @Column(name="condition_value",columnDefinition="text") private Map<LcCondition,String> conditions=new EnumMap<>(LcCondition.class);
 @com.fasterxml.jackson.annotation.JsonIgnore
 public Map<LcCondition,String> getConditions(){return conditions;}
 public void setConditions(Map<LcCondition,String> values){conditions=new EnumMap<>(LcCondition.class);if(values!=null)conditions.putAll(values);}
 private String ownBankReference;
 private String foreignBankReference;
 public String getOwnBankReference(){return ownBankReference;}
 public void setOwnBankReference(String value){ownBankReference=value;}
 public String getForeignBankReference(){return foreignBankReference;}
 public void setForeignBankReference(String value){foreignBankReference=value;}
 private Integer companyId;
 public Integer getCompanyId(){return companyId;}
 public void setCompanyId(Integer value){companyId=value;}
 private String templateCompany;
 public String getTemplateCompany(){return templateCompany;}
 public void setTemplateCompany(String value){templateCompany=value;}
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false,unique=true) private String reference;
 private String applicant; private String beneficiary; private String issuingBank; private String advisingBank;
 @Column(precision=19,scale=2) private BigDecimal amount; private String currency; private LocalDate issueDate; private LocalDate expiryDate; private String expiryPlace; private LocalDate latestShipmentDate; private String assignedTo; private LocalDate followUpDate;
 @Enumerated(EnumType.STRING) private LetterOfCreditStatus status=LetterOfCreditStatus.RECEIVED;
 @ElementCollection @CollectionTable(name="lc_required_document",joinColumns=@JoinColumn(name="lc_id")) @Column(name="description",length=2000) private List<String> requiredDocuments=new ArrayList<>();
 @ElementCollection @CollectionTable(name="lc_additional_field",joinColumns=@JoinColumn(name="lc_id")) @MapKeyColumn(name="field_name",length=255) @Column(name="field_value",length=4000) private Map<String,String> additionalFields=new LinkedHashMap<>();
 @Column(columnDefinition="text") private String rawMessage;
 public UUID getId(){return id;} public String getReference(){return reference;} public void setReference(String v){reference=v;} public String getApplicant(){return applicant;} public void setApplicant(String v){applicant=v;} public String getBeneficiary(){return beneficiary;} public void setBeneficiary(String v){beneficiary=v;} public String getIssuingBank(){return issuingBank;} public void setIssuingBank(String v){issuingBank=v;} public String getAdvisingBank(){return advisingBank;} public void setAdvisingBank(String v){advisingBank=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;} public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public LocalDate getIssueDate(){return issueDate;} public void setIssueDate(LocalDate v){issueDate=v;} public LocalDate getExpiryDate(){return expiryDate;} public void setExpiryDate(LocalDate v){expiryDate=v;} public String getExpiryPlace(){return expiryPlace;} public void setExpiryPlace(String v){expiryPlace=v;} public LocalDate getLatestShipmentDate(){return latestShipmentDate;} public void setLatestShipmentDate(LocalDate v){latestShipmentDate=v;} public String getAssignedTo(){return assignedTo;} public void setAssignedTo(String v){assignedTo=v;} public LocalDate getFollowUpDate(){return followUpDate;} public void setFollowUpDate(LocalDate v){followUpDate=v;} public LetterOfCreditStatus getStatus(){return status;} public void setStatus(LetterOfCreditStatus v){status=v;} public List<String> getRequiredDocuments(){return requiredDocuments;} public void setRequiredDocuments(List<String> v){requiredDocuments=v;} public Map<String,String> getAdditionalFields(){return additionalFields;} public void setAdditionalFields(Map<String,String> v){additionalFields=v;} public String getRawMessage(){return rawMessage;} public void setRawMessage(String v){rawMessage=v;}
}
