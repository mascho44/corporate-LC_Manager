package de.ostms.lc.document.api;
import de.ostms.lc.document.domain.*;import java.time.LocalDateTime;import java.util.UUID;
public record DocumentTemplateView(UUID id,DocumentType documentType,Integer companyId,String companyName,String originalFilename,long fileSize,String uploadedBy,LocalDateTime uploadedAt){public static DocumentTemplateView from(DocumentTemplate t){return new DocumentTemplateView(t.getId(),t.getDocumentType(),t.getCompanyId(),t.getCompanyName(),t.getOriginalFilename(),t.getFileSize(),t.getUploadedBy(),t.getUploadedAt());}}
