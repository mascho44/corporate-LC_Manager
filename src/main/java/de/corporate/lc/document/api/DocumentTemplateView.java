package de.corporate.lc.document.api;
import de.corporate.lc.document.domain.*;import java.time.LocalDateTime;import java.util.UUID;
public record DocumentTemplateView(UUID id,DocumentType documentType,String originalFilename,long fileSize,String uploadedBy,LocalDateTime uploadedAt){public static DocumentTemplateView from(DocumentTemplate t){return new DocumentTemplateView(t.getId(),t.getDocumentType(),t.getOriginalFilename(),t.getFileSize(),t.getUploadedBy(),t.getUploadedAt());}}
