package de.ostms.lc.document.api;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.service.ClassificationHistory;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@RestController @RequestMapping("/api/documents/{id}/classification")
public class DocumentClassificationController {
 private final LcDocumentRepository documents;
 public DocumentClassificationController(LcDocumentRepository documents){this.documents=documents;}
 @GetMapping @Transactional(readOnly=true) public com.fasterxml.jackson.databind.JsonNode history(@PathVariable UUID id){return ClassificationHistory.read(documents.findById(id).orElseThrow().getClassificationHistoryJson());}
}
