package de.corporate.lc.document.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.document.service.*;
import de.corporate.lc.audit.service.AuditService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Stateless training upload: no inbox item, LC or original PDF is persisted. */
@RestController
@RequestMapping("/api/training/document-types")
public class SplitPretrainingController {
 private final DocumentExtractionService extraction;private final SplitTrainingService training;private final ObjectMapper json;private final AuditService audit;private final SplitTrainingReceipt receipts;
 public SplitPretrainingController(DocumentExtractionService extraction,SplitTrainingService training,ObjectMapper json,AuditService audit,SplitTrainingReceipt receipts){this.extraction=extraction;this.training=training;this.json=json;this.audit=audit;this.receipts=receipts;}
 private record Input(byte[] content,String evidence){}
 private Input read(MultipartFile file)throws Exception{
  if(file==null||file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("Bitte eine PDF bis 10 MB auswählen.");
  byte[] content;try(var stream=file.getInputStream()){content=stream.readNBytes(10*1024*1024+1);}
  if(content.length>10*1024*1024||content.length<5||!new String(content,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalArgumentException("Ungültige PDF-Datei.");
  var recognized=extraction.extractFile(content,"training.pdf","application/pdf");
  if(!List.of("EXTRACTED","OCR_EXTRACTED").contains(recognized.status()))throw new IllegalArgumentException("Auslesen oder OCR fehlgeschlagen: "+recognized.status());
  return new Input(content,recognized.ocrEvidence()==null?null:json.writeValueAsString(recognized.ocrEvidence()));
 }
 public record Preview(PdfDocumentSplitter.Proposal proposal,String receipt){}
 @PostMapping(value="/proposal",consumes="multipart/form-data")
 public Preview proposal(@RequestPart("file") MultipartFile file,Authentication actor)throws Exception{
  var input=read(file);var baseline=PdfDocumentSplitter.propose(input.content(),input.evidence());
  if(baseline.pageCount()<2)throw new IllegalArgumentException("Für das Split-Vortraining wird eine mehrseitige Sammel-PDF benötigt.");
  String hash=PdfDocumentSplitter.trainingPattern(input.content(),input.evidence());
  if(hash==null)throw new IllegalArgumentException("Zu wenig lesbarer Text für ein Trainingsmuster. Jede Seite benötigt mindestens 40 Buchstaben.");
  return new Preview(training.suggest(input.content(),input.evidence(),baseline),receipts.issue(hash,baseline.pageCount(),actor.getName()));
 }
 public record Confirmation(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) String receipt,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=2,max=100) List<PdfDocumentSplitter.Part> parts){}
 @PostMapping(value="/confirm",consumes="application/json")
 @Transactional(rollbackFor=Exception.class)
 public Map<String,String> confirm(@jakarta.validation.Valid @RequestBody Confirmation request,Authentication actor)throws Exception{
  var receipt=receipts.verify(request.receipt(),actor.getName());var parts=request.parts();
  // The same coverage checks as a real split, without generating child PDFs.
  PdfDocumentSplitter.validate(parts,receipt.pages());training.confirmPattern(receipt.hash(),parts,actor.getName());
  audit.recordInTransaction(actor,"DOCUMENT_SPLIT_PRETRAINED","TRAINING",null,"Split template confirmed; pages="+receipt.pages()+"; ranges="+parts.size()+"; no PDF retained");
  return Map.of("status","CONFIRMED");
 }
}
