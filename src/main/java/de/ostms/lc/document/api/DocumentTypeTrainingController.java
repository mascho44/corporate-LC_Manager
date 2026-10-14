package de.ostms.lc.document.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.DocumentTypeTrainingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** The document types this tenant has taught the recognition (with the confirmed examples), to be reviewed and removed one by one. */
@RestController @RequestMapping("/api/document-type-training")
public class DocumentTypeTrainingController {
 public record View(UUID id,String type,String typeName,String sample,String confirmedBy,java.time.OffsetDateTime confirmedAt){}
 private final DocumentTypeTrainingService training;private final AuditService audit;
 public DocumentTypeTrainingController(DocumentTypeTrainingService training,AuditService audit){this.training=training;this.audit=audit;}

 @GetMapping public List<View> list(){
  return training.examples().stream().map(e->new View(e.id(),e.type().name(),e.type().getDisplayName(),e.sample(),e.confirmedBy(),e.confirmedAt())).toList();
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id,Authentication authentication){
  if(!training.delete(id))throw new NoSuchElementException("Beispiel nicht gefunden.");
  audit.record(authentication,"DOCUMENT_TYPE_EXAMPLE_DELETED","DOCUMENT_TYPE_EXAMPLE",id,"Gelerntes Beispiel einer Dokumentart entfernt");
 }
}
