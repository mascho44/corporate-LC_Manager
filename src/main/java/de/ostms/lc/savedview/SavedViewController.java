package de.ostms.lc.savedview;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/saved-views")
public class SavedViewController {
 private final SavedViewService service;private final AuditService audit;
 public SavedViewController(SavedViewService service,AuditService audit){this.service=service;this.audit=audit;}
 public record Request(String name,boolean shared,Map<String,String> filters) {}

 @GetMapping public List<SavedViewService.View> list(Authentication auth){return service.list(auth.getName());}
 @PostMapping public SavedViewService.View create(@RequestBody Request request,Authentication auth){
  var view=service.create(auth.getName(),request.name(),request.shared(),request.filters());
  audit.record(auth,"SAVED_VIEW_CREATED","SAVED_VIEW",view.id(),view.name()+(view.shared()?" (geteilt)":""));return view;
 }
 @PutMapping("/{id}") public SavedViewService.View update(@PathVariable UUID id,@RequestBody Request request,Authentication auth){
  var view=service.update(auth.getName(),id,request.name(),request.shared(),request.filters());
  audit.record(auth,"SAVED_VIEW_UPDATED","SAVED_VIEW",id,view.name()+(view.shared()?" (geteilt)":""));return view;
 }
 @DeleteMapping("/{id}") public void delete(@PathVariable UUID id,Authentication auth){
  boolean manage=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("PERM_SETTINGS_MANAGE"));
  service.delete(auth.getName(),id,manage);audit.record(auth,"SAVED_VIEW_DELETED","SAVED_VIEW",id,"Ansicht gelöscht");
 }
}
