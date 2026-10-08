package de.ostms.lc.document.api;

import de.ostms.lc.document.domain.*;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDraftView(UUID id, UUID lcId, DocumentType documentType, String documentNumber,
        DocumentDraftStatus status, long version, String createdBy, String updatedBy, String submittedBy, String checkedBy, LocalDateTime checkedAt, String approvedBy, LocalDateTime approvedAt,
        LocalDateTime createdAt, LocalDateTime updatedAt, GeneratedDocumentRequest data, java.util.List<String> approvalUsers, int approvalCount, int requiredApprovals,
        DocumentDraftValidation validation) {}
