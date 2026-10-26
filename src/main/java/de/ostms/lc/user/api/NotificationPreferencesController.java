package de.ostms.lc.user.api;
import de.ostms.lc.user.service.NotificationPreferencesService;
import de.ostms.lc.audit.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/profile/notifications")
public class NotificationPreferencesController {
 private final NotificationPreferencesService service;private final AuditService audit;
 public NotificationPreferencesController(NotificationPreferencesService service,AuditService audit){this.service=service;this.audit=audit;}
 public record Update(boolean deadlineDigest) {}
 @GetMapping public NotificationPreferencesService.Preferences get(Authentication auth){return service.get(auth.getName());}
 @PutMapping public NotificationPreferencesService.Preferences update(@RequestBody Update request,Authentication auth){
  var result=service.update(auth.getName(),request.deadlineDigest());audit.record(auth,"USER_NOTIFICATIONS_UPDATED","USER",auth.getName(),"deadlineDigest="+result.deadlineDigest());return result;
 }
}
