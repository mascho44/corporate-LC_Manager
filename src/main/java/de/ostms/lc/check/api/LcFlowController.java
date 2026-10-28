package de.ostms.lc.check.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.LcFlowService;
import de.ostms.lc.document.service.DocumentExtractionService;
import de.ostms.lc.swift.PrintedSwiftNormalizer;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** The guided order of a bank file: draft, comparison with the credit that arrived, check of the credit, documents. */
@RestController @RequestMapping("/api/lcs/{lcId}")
public class LcFlowController {
 public record DraftRequest(String text,String filename){}
 private final LcFlowService flow;private final DocumentExtractionService extraction;private final PrintedSwiftNormalizer normalizer;private final AuditService audit;
 public LcFlowController(LcFlowService flow,DocumentExtractionService extraction,PrintedSwiftNormalizer normalizer,AuditService audit){this.flow=flow;this.extraction=extraction;this.normalizer=normalizer;this.audit=audit;}

 @GetMapping("/flow") public LcFlowService.Flow get(@PathVariable UUID lcId){return flow.flow(lcId,LocalDate.now());}

 @PutMapping("/draft") public LcFlowService.Flow saveText(@PathVariable UUID lcId,@RequestBody DraftRequest request,Authentication auth){
  flow.saveDraft(lcId,request.filename(),request.text(),auth.getName());
  audit.record(auth,"LC_DRAFT_SAVED","LETTER_OF_CREDIT",lcId,request.filename()==null?"Text":request.filename());
  return get(lcId);
 }

 @PutMapping(value="/draft",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public LcFlowService.Flow saveFile(@PathVariable UUID lcId,@RequestPart("file") MultipartFile file,Authentication auth)throws IOException{
  if(file.isEmpty())throw new IllegalArgumentException("Die Datei ist leer.");
  if(file.getSize()>10*1024*1024)throw new IllegalArgumentException("Die Datei ist größer als 10 MB.");
  var result=extraction.extractFile(file.getBytes(),file.getOriginalFilename(),file.getContentType());
  if(result.text()==null||result.text().isBlank())throw new IllegalArgumentException("Aus dem PDF konnte kein Text erkannt werden.");
  String normalized=normalizer.normalize(result.text());
  flow.saveDraft(lcId,file.getOriginalFilename(),normalized.isBlank()?result.text():normalized,auth.getName());
  audit.record(auth,"LC_DRAFT_SAVED","LETTER_OF_CREDIT",lcId,file.getOriginalFilename());
  return get(lcId);
 }

 @DeleteMapping("/draft") public ResponseEntity<Void> delete(@PathVariable UUID lcId,Authentication auth){
  flow.deleteDraft(lcId);
  audit.record(auth,"LC_DRAFT_REMOVED","LETTER_OF_CREDIT",lcId,"");
  return ResponseEntity.noContent().build();
 }
}
