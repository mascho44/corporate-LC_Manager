package de.ostms.lc.audit.api;

import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.audit.service.AuditChainService;
import de.ostms.lc.audit.service.AuditSearchService;
import de.ostms.lc.audit.service.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/audit")
public class AuditController {
 private final AuditService service;private final AuditSearchService search;private final AuditChainService chain;
 public AuditController(AuditService service,AuditSearchService search,AuditChainService chain){this.service=service;this.search=search;this.chain=chain;}

 /** Without filters the most recent events; with filters (period, user, action, object) up to 2000 matching events. Reading the log is itself recorded. */
 @GetMapping
 public List<AuditEvent> events(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required=false) String user,@RequestParam(required=false) String action,@RequestParam(required=false) String entityId,
                                @RequestParam(defaultValue="200") int limit,Authentication auth){
  var filter=new AuditSearchService.Filter(from,to,user,action,entityId);
  var events=search.search(filter,Math.min(Math.max(limit,1),AuditSearchService.MAX_LIMIT));
  service.record(auth,"AUDIT_VIEWED","AUDIT",null,filter.describe()+" · "+events.size()+" Ereignisse");
  return events;
 }

 /** Full export of the filtered range (up to 50,000 rows), including chain data for independent verification. */
 @GetMapping(value="/export.csv",produces="text/csv")
 public ResponseEntity<byte[]> export(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required=false) String user,@RequestParam(required=false) String action,@RequestParam(required=false) String entityId,Authentication auth){
  var filter=new AuditSearchService.Filter(from,to,user,action,entityId);
  var events=filter.isEmpty()?service.recent():search.search(filter,AuditSearchService.EXPORT_LIMIT);
  byte[] content=service.csv(events);
  service.record(auth,"AUDIT_EXPORTED","AUDIT",null,filter.describe()+" · "+events.size()+" Zeilen");
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=Audit-Protokoll.csv").contentLength(content.length).body(content);
 }

 @GetMapping("/retention")
 public AuditSearchService.Retention retention(){return search.retention();}

 /** Verifies the hash chain of the current tenant and records that the check was run. */
 @GetMapping("/chain")
 public AuditChainService.Chain chain(Authentication auth){
  var result=chain.verifyCurrent();
  service.record(auth,"AUDIT_CHAIN_VERIFIED","AUDIT",null,(result.ok()?"Kette intakt":"Kette BRUCH bei Nr. "+result.firstBadSeq())+" · "+result.checked()+" Eintraege geprueft");
  return result;
 }
}
