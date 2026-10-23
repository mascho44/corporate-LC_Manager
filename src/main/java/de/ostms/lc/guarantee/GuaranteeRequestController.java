package de.ostms.lc.guarantee;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

/** The request of a guarantee to the bank (corporate profile only). Reading is open to signed-in users, writing needs the right to edit files. */
@RestController @RequestMapping("/api/guarantees/{lcId}/request")
public class GuaranteeRequestController {
 private final GuaranteeRequestService service;private final de.ostms.lc.audit.service.AuditService audit;
 public GuaranteeRequestController(GuaranteeRequestService service,de.ostms.lc.audit.service.AuditService audit){this.service=service;this.audit=audit;}
 @GetMapping public GuaranteeRequestService.View get(@PathVariable UUID lcId){return service.get(lcId);}
 @PutMapping public GuaranteeRequestService.View save(@PathVariable UUID lcId,@RequestBody GuaranteeRequestService.Request request,Authentication auth){return service.save(lcId,request,auth);}
 @PostMapping("/status") public GuaranteeRequestService.View status(@PathVariable UUID lcId,@RequestBody GuaranteeRequestService.StatusRequest request,Authentication auth){return service.setStatus(lcId,request,auth);}
 /** The application as a Word letter. Creating it is a change of the file (right to edit files), so it is a POST. */
 @PostMapping(value="/document",produces="application/vnd.openxmlformats-officedocument.wordprocessingml.document")
 public ResponseEntity<byte[]> document(@PathVariable UUID lcId,@RequestParam(defaultValue="de") String language,Authentication auth)throws IOException{
  byte[] content=service.document(lcId,language,LocalDate.now());
  audit.record(auth,"GUARANTEE_REQUEST_DOCUMENT_CREATED","LETTER_OF_CREDIT",lcId,"Antrag als Word ("+("en".equalsIgnoreCase(language)?"en":"de")+")");
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Garantieantrag.docx").contentLength(content.length).body(content);
 }
}
