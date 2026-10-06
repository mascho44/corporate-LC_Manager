package de.corporate.lc.user.api;
import de.corporate.lc.user.service.LanguagePreferencesService;
import de.corporate.lc.audit.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/profile/language")
public class LanguagePreferencesController {
 private final LanguagePreferencesService service;private final AuditService audit;
 public LanguagePreferencesController(LanguagePreferencesService service,AuditService audit){this.service=service;this.audit=audit;}
 public record Update(String language) {}
 @GetMapping public LanguagePreferencesService.Preferences get(Authentication auth){return service.get(auth.getName());}
 @PutMapping public LanguagePreferencesService.Preferences update(@RequestBody Update request,Authentication auth){
  var result=service.update(auth.getName(),request.language());audit.record(auth,"USER_LANGUAGE_UPDATED","USER",auth.getName(),"language="+result.language()+"; preference="+result.preferredLanguage());return result;
 }
}
