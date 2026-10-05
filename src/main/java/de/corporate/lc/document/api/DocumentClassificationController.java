package de.corporate.lc.document.api;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.document.service.ClassificationHistory;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@RestController @RequestMapping("/api/documents/{id}/classification")
public class DocumentClassificationController {
 private final LcDocumentRepository documents;
 public DocumentClassificationController(LcDocumentRepository documents){this.documents=documents;}
 @GetMapping @Transactional(readOnly=true) public com.fasterxml.jackson.databind.JsonNode history(@PathVariable UUID id){return ClassificationHistory.read(documents.findById(id).orElseThrow().getClassificationHistoryJson());}
}
