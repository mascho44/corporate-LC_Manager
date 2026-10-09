package de.ostms.lc.user.api;

import de.ostms.lc.user.service.PlatformOverviewService;
import de.ostms.lc.user.service.PlatformOverviewStore;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/platform")
public class PlatformOverviewController {
 private final PlatformOverviewService service;
 public PlatformOverviewController(PlatformOverviewService service){this.service=service;}
 @GetMapping("/memberships") public List<PlatformOverviewStore.Membership> memberships(Authentication auth){return service.memberships(auth);}
 @GetMapping("/audit") public List<PlatformOverviewStore.AuditRow> audit(@RequestParam(required=false) String tenant,@RequestParam(required=false) Integer limit,Authentication auth){return service.audit(tenant,limit,auth);}
}
