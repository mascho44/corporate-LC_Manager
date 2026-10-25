package de.ostms.lc.document.api;

import de.ostms.lc.document.service.SpatialLayoutTraining;
import de.ostms.lc.audit.service.AuditService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/api/settings/document-layouts")
public class DocumentLayoutController {
 private final SpatialLayoutTraining training;private final AuditService audit;
 public DocumentLayoutController(SpatialLayoutTraining training,AuditService audit){this.training=training;this.audit=audit;}
 public record Update(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=100) String profile,boolean active){}
 @GetMapping public List<Map<String,Object>> list(){return training.layouts();}
 @PutMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) @Transactional
 public void update(@PathVariable UUID id,@Valid @RequestBody Update request,Authentication auth){
  training.updateLayout(id,request.profile(),request.active());
  audit.recordInTransaction(auth,"DOCUMENT_LAYOUT_UPDATED","DOCUMENT_LAYOUT",id,"Profil "+request.profile()+" · aktiv="+request.active());
 }
}
