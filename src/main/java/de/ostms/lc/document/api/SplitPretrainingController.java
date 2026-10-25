package de.ostms.lc.document.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.service.*;
import de.ostms.lc.audit.service.AuditService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Stateless training upload: no inbox item, LC or original PDF is persisted. */
@RestController
@RequestMapping("/api/training/document-types")
public class SplitPretrainingController {
 @org.springframework.beans.factory.annotation.Autowired private SplitPretrainingJobs jobs;
 @org.springframework.beans.factory.annotation.Autowired private PdfPagePreviewService pagePreview;
 @PostMapping(value="/page-preview",consumes="multipart/form-data",produces="image/png")
 public org.springframework.http.ResponseEntity<byte[]> pagePreview(@RequestPart("file") MultipartFile file,@RequestParam int page,@RequestParam(defaultValue="false") boolean enlarged)throws Exception{
  if(file==null||file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("Bitte eine PDF bis 10 MB auswählen.");
  byte[] content;try(var stream=file.getInputStream()){content=stream.readNBytes(10*1024*1024+1);}
  if(content.length>10*1024*1024)throw new IllegalArgumentException("PDF überschreitet 10 MB.");
  return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_PNG).cacheControl(org.springframework.http.CacheControl.noStore()).body(pagePreview.render(content,page,enlarged));
 }
 private final DocumentExtractionService extraction;private final SplitTrainingService training;private final ObjectMapper json;private final AuditService audit;private final SplitTrainingReceipt receipts;
 public SplitPretrainingController(DocumentExtractionService extraction,SplitTrainingService training,ObjectMapper json,AuditService audit,SplitTrainingReceipt receipts){this.extraction=extraction;this.training=training;this.json=json;this.audit=audit;this.receipts=receipts;}
 private record Input(byte[] content,String evidence){}
 private byte[] readBytes(MultipartFile file)throws Exception{
  if(file==null||file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("Bitte eine PDF bis 10 MB auswählen.");
  byte[] content;try(var stream=file.getInputStream()){content=stream.readNBytes(10*1024*1024+1);}
  if(content.length>10*1024*1024||content.length<5||!new String(content,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalArgumentException("Ungültige PDF-Datei.");
  return content;
 }
 private Input recognize(byte[] content,boolean background)throws Exception{
  var document=new de.ostms.lc.document.domain.LcDocument();document.setContent(content);document.setContentType("application/pdf");document.setOriginalFilename("training.pdf");
  DocumentExtractionService.TextExtraction recognized;
  if(background){extraction.extractInBackground(document);recognized=new DocumentExtractionService.TextExtraction(document.getExtractedText(),document.getExtractionStatus(),DocumentExtractionService.readEvidence(document.getOcrEvidenceJson()));}
  else recognized=extraction.extractFile(content,"training.pdf","application/pdf");
  if(!List.of("EXTRACTED","OCR_EXTRACTED").contains(recognized.status()))throw new IllegalArgumentException("Auslesen oder OCR fehlgeschlagen: "+recognized.status());
  return new Input(content,recognized.ocrEvidence()==null?null:json.writeValueAsString(recognized.ocrEvidence()));
 }
 public record Preview(PdfDocumentSplitter.Proposal proposal,String receipt){}
 @PostMapping(value="/proposal",consumes="multipart/form-data")
 public Preview proposal(@RequestPart("file") MultipartFile file,Authentication actor)throws Exception{
  return buildPreview(recognize(readBytes(file),false),actor.getName());
 }
 @PostMapping(value="/jobs",consumes="multipart/form-data")
 public org.springframework.http.ResponseEntity<SplitPretrainingJobs.Status> start(@RequestPart("file") MultipartFile file,Authentication actor)throws Exception{
  byte[] content=readBytes(file);String username=actor.getName();
  return org.springframework.http.ResponseEntity.accepted().cacheControl(org.springframework.http.CacheControl.noStore()).body(jobs.submit(username,()->buildPreview(recognize(content,true),username)));
 }
 @GetMapping("/jobs/{id}")
 public org.springframework.http.ResponseEntity<SplitPretrainingJobs.Status> status(@PathVariable UUID id,Authentication actor){
  return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(jobs.status(id,actor.getName()));
 }
 private Preview buildPreview(Input input,String actor)throws Exception{
  var baseline=PdfDocumentSplitter.propose(input.content(),input.evidence());
  if(baseline.pageCount()<2)throw new IllegalArgumentException("Für das Split-Vortraining wird eine mehrseitige Sammel-PDF benötigt.");
  String hash=PdfDocumentSplitter.trainingPattern(input.content(),input.evidence());
  if(hash==null)throw new IllegalArgumentException("Zu wenig lesbarer Text für ein Trainingsmuster. Jede Seite benötigt mindestens 40 Buchstaben.");
  var suggested=training.suggest(input.content(),input.evidence(),baseline);
  return new Preview(suggested,receipts.issue(hash,baseline.pageCount(),actor,suggested.parts(),SplitTrainingService.method(suggested)));
 }
 public record Confirmation(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=40000) String receipt,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min=1,max=100) List<PdfDocumentSplitter.Part> parts){}
 @PostMapping(value="/confirm",consumes="application/json")
 @Transactional(rollbackFor=Exception.class)
 public Map<String,String> confirm(@jakarta.validation.Valid @RequestBody Confirmation request,Authentication actor)throws Exception{
  var receipt=receipts.verify(request.receipt(),actor.getName());var parts=request.parts();
  // The same coverage checks as a real split, without generating child PDFs.
  PdfDocumentSplitter.validate(parts,receipt.pages());training.confirmMeasured(receipt.hash(),parts,actor.getName(),receipt.proposedParts(),receipt.method());
  audit.recordInTransaction(actor,"DOCUMENT_SPLIT_PRETRAINED","TRAINING",null,"Split template confirmed; pages="+receipt.pages()+"; ranges="+parts.size()+"; no PDF retained");
  return Map.of("status","CONFIRMED");
 }
}
