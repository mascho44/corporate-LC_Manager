package de.ostms.lc.capture;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/capture-tokens")
public class CaptureTokenController {
 public record CreateRequest(String name){}
 private final CaptureTokenService tokens;private final AuditService audit;
 public CaptureTokenController(CaptureTokenService tokens,AuditService audit){this.tokens=tokens;this.audit=audit;}

 @GetMapping public List<CaptureTokenService.View> list(){return tokens.list();}

 /** Readable by everyone who may see the inbox: which delivery stations have been silent (the mailroom may have a problem). */
 @GetMapping("/silent") public List<CaptureTokenService.Silent> silent(){return tokens.silent(java.time.LocalDate.now());}

 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public CaptureTokenService.Created create(@RequestBody CreateRequest request,Authentication authentication){
  var created=tokens.create(request==null?null:request.name(),authentication.getName());
  audit.record(authentication,"CAPTURE_TOKEN_CREATED","CAPTURE_TOKEN",created.token().id(),created.token().name()+" · "+created.token().prefix()+"…");
  return created;
 }

 @DeleteMapping("/{id}")
 public CaptureTokenService.View revoke(@PathVariable UUID id,Authentication authentication){
  var view=tokens.revoke(id);
  audit.record(authentication,"CAPTURE_TOKEN_REVOKED","CAPTURE_TOKEN",id,view.name()+" · "+view.prefix()+"…");
  return view;
 }
}
