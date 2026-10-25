package de.ostms.lc.tenant.api;
import de.ostms.lc.tenant.service.TenantReadinessService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/tenants/current/readiness") public class TenantReadinessController {
 private final TenantReadinessService service;
 public TenantReadinessController(TenantReadinessService service){this.service=service;}
 @GetMapping public TenantReadinessService.Readiness get(Authentication auth){return service.get(auth);}
}
