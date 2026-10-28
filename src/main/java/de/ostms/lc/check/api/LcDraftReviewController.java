package de.ostms.lc.check.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.LcDraftReviewService;
import de.ostms.lc.document.service.DocumentExtractionService;
import de.ostms.lc.swift.PrintedSwiftNormalizer;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** The draft of a credit from a third party, checked before it is issued; the draft is not stored. */
@RestController
public class LcDraftReviewController {
 public record TextRequest(String text) {}
 private final LcDraftReviewService service;private final DocumentExtractionService extraction;private final PrintedSwiftNormalizer normalizer;private final AuditService audit;
 public LcDraftReviewController(LcDraftReviewService service,DocumentExtractionService extraction,PrintedSwiftNormalizer normalizer,AuditService audit){this.service=service;this.extraction=extraction;this.normalizer=normalizer;this.audit=audit;}

 @PostMapping("/api/lc-draft-check")
 public LcDraftReviewService.Result checkText(@RequestBody TextRequest request,Authentication auth){return run(request.text(),"Text",auth);}

 @PostMapping(value="/api/lc-draft-check",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public LcDraftReviewService.Result checkFile(@RequestPart("file") MultipartFile file,Authentication auth)throws IOException{
  if(file.isEmpty())throw new IllegalArgumentException("Die Datei ist leer.");
  if(file.getSize()>10*1024*1024)throw new IllegalArgumentException("Die Datei ist größer als 10 MB.");
  var result=extraction.extractFile(file.getBytes(),file.getOriginalFilename(),file.getContentType());
  if(result.text()==null||result.text().isBlank())throw new IllegalArgumentException("Aus dem PDF konnte kein Text erkannt werden.");
  String normalized=normalizer.normalize(result.text());
  return run(normalized.isBlank()?result.text():normalized,file.getOriginalFilename(),auth);
 }

 private LcDraftReviewService.Result run(String text,String source,Authentication auth){
  var result=service.check(text,LocalDate.now());
  audit.record(auth,"LC_DRAFT_CHECKED","LC_DRAFT",null,result.reference()+" · "+result.discrepancies()+" Abweichungen · "+result.warnings()+" Hinweise (Entwurf nicht gespeichert; "+source+")");
  return result;
 }
}
