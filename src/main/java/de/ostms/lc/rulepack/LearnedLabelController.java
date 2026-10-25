package de.ostms.lc.rulepack;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Labels learned from manual entries of the current tenant: to look at and to remove a wrong one. */
@RestController
@RequestMapping("/api/learned-labels")
public class LearnedLabelController {
 private final LearnedLabelService service;private final AuditService audit;
 public LearnedLabelController(LearnedLabelService s,AuditService a){service=s;audit=a;}
 @GetMapping public List<Map<String,Object>> list(){return service.list();}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id,Authentication auth){
  if(!service.delete(id))throw new NoSuchElementException();
  audit.record(auth,"FIELD_LABEL_FORGOTTEN","LEARNED_FIELD_LABEL",id,"Gelernte Beschriftung entfernt");
  return ResponseEntity.noContent().build();
 }
}
