package de.ostms.lc.document.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.ScanProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/scan-profile")
public class ScanProfileController {
 private final ScanProfileService service;private final AuditService audit;
 public ScanProfileController(ScanProfileService s,AuditService a){service=s;audit=a;}
 @GetMapping public ScanProfileService.View get(){return service.view();}
 /** Applies to documents recognised from now on; existing recognitions are unchanged until "Neu erkennen". */
 @PutMapping public ScanProfileService.View change(@RequestBody Map<String,String> body,Authentication auth){
  String wanted=body==null?null:body.get("profile");
  String before=service.change(wanted,auth.getName());
  audit.record(auth,"SCAN_PROFILE_CHANGED","TENANT",null,before+" → "+wanted);
  return service.view();
 }
}
