package de.corporate.lc.document.repository;
import de.corporate.lc.document.domain.*;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate,UUID>{Optional<DocumentTemplate> findByDocumentTypeAndCompanyId(DocumentType type,Integer companyId);Optional<DocumentTemplate> findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(DocumentType type,String companyName);}
