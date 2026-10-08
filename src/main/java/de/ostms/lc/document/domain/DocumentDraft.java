package de.ostms.lc.document.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_draft")
public class DocumentDraft extends de.ostms.lc.tenant.domain.TenantOwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "lc_id", nullable = false) private UUID lcId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentType documentType;
    @Column(nullable = false, length = 100) private String documentNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentDraftStatus status = DocumentDraftStatus.DRAFT;
    @Column(nullable = false, columnDefinition = "text") private String dataJson;
    @Version private long version;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false, length = 100) private String updatedBy;
    @Column(length = 100) private String submittedBy;
    @Column(length = 100) private String checkedBy;
    private LocalDateTime checkedAt;
    @Column(length = 100) private String approvedBy;
    private LocalDateTime approvedAt;
    @Column(name = "required_approvals", nullable = false) private int requiredApprovals = 1;
    @Column(name = "approvals_json", nullable = false, columnDefinition = "text") private String approvalsJson = "[]";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    public UUID getId(){return id;} public UUID getLcId(){return lcId;} public void setLcId(UUID v){lcId=v;} public DocumentType getDocumentType(){return documentType;} public void setDocumentType(DocumentType v){documentType=v;} public String getDocumentNumber(){return documentNumber;} public void setDocumentNumber(String v){documentNumber=v;} public DocumentDraftStatus getStatus(){return status;} public void setStatus(DocumentDraftStatus v){status=v;} public String getDataJson(){return dataJson;} public void setDataJson(String v){dataJson=v;} public long getVersion(){return version;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;} public String getSubmittedBy(){return submittedBy;} public void setSubmittedBy(String v){submittedBy=v;} public String getCheckedBy(){return checkedBy;} public void setCheckedBy(String v){checkedBy=v;} public LocalDateTime getCheckedAt(){return checkedAt;} public void setCheckedAt(LocalDateTime v){checkedAt=v;} public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String v){approvedBy=v;} public LocalDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(LocalDateTime v){approvedAt=v;} public int getRequiredApprovals(){return requiredApprovals;} public void setRequiredApprovals(int v){requiredApprovals=v;} public String getApprovalsJson(){return approvalsJson;} public void setApprovalsJson(String v){approvalsJson=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
