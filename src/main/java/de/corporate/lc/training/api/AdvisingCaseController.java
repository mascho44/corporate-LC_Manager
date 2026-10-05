package de.corporate.lc.training.api;

import de.corporate.lc.training.service.AdvisingCaseService;
import de.corporate.lc.document.service.AdvisingLetterExtractor;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/training/advising/{id}/new-case")
public class AdvisingCaseController {
 private final AdvisingCaseService service;
 public AdvisingCaseController(AdvisingCaseService service){this.service=service;}
 private void authorize(Authentication auth){
  for(String permission:new String[]{"PERM_TRAINING_MANAGE","PERM_LC_EDIT","PERM_DOCUMENT_UPLOAD"})if(auth.getAuthorities().stream().noneMatch(a->permission.equals(a.getAuthority())))throw new AccessDeniedException("Keine Berechtigung zur Übernahme in eine LC-Akte.");
 }
 @GetMapping public AdvisingLetterExtractor.Proposal preview(@PathVariable UUID id,Authentication auth){authorize(auth);return service.preview(id,auth.getName());}
 @PostMapping public AdvisingCaseService.Result create(@PathVariable UUID id,@Valid @RequestBody AdvisingNewCaseRequest request,Authentication auth){authorize(auth);return service.create(id,request,auth);}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
 public org.springframework.http.ResponseEntity<java.util.Map<String,String>> conflict(){return org.springframework.http.ResponseEntity.status(409).body(java.util.Map.of("error","Die Akte konnte nicht gespeichert werden. Bitte prüfen, ob die Referenz bereits vergeben ist. Das Training bleibt erhalten."));}
}
