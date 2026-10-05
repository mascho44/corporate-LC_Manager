package de.corporate.lc.document.api;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.document.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/api/documents/{id}/ocr-confidence")
public class OcrConfidenceController {
 private final LcDocumentRepository documents;
 public OcrConfidenceController(LcDocumentRepository documents){this.documents=documents;}
 public record View(String status,OcrEvidence evidence,Map<String,OcrEvidence.Assessment> fields){}
 @GetMapping @Transactional(readOnly=true) public View confidence(@PathVariable UUID id){
  var doc=documents.findById(id).orElseThrow();var evidence=DocumentExtractionService.readEvidence(doc.getOcrEvidenceJson());
  if(evidence==null)return new View("OCR_EXTRACTED".equals(doc.getExtractionStatus())?"UNAVAILABLE":"NOT_APPLICABLE",null,Map.of());
  Map<String,OcrEvidence.Assessment> fields=new LinkedHashMap<>();
  fields.put("reference",evidence.assess(doc.getExtractedReference(),evidence.threshold()));
  fields.put("documentNumber",evidence.assess(doc.getExtractedDocumentNumber(),evidence.threshold()));
  fields.put("currency",evidence.assess(doc.getExtractedCurrency(),evidence.threshold()));
  fields.put("amount",evidence.assess(doc.getExtractedAmount()==null?null:doc.getExtractedAmount().toPlainString(),evidence.threshold()));
  return new View("OCR_EXTRACTED",evidence,fields);
 }
}
