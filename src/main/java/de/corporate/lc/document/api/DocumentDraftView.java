package de.corporate.lc.document.api;

import de.corporate.lc.document.domain.*;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDraftView(UUID id, UUID lcId, DocumentType documentType, String documentNumber,
        DocumentDraftStatus status, long version, String createdBy, String updatedBy,
        LocalDateTime createdAt, LocalDateTime updatedAt, GeneratedDocumentRequest data,
        DocumentDraftValidation validation) {}
