package de.ostms.lc.tenant.api;

import de.ostms.lc.tenant.service.HomeTenantService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/home-tenant")
public class PlatformHomeTenantController {
    private final HomeTenantService service;

    public PlatformHomeTenantController(HomeTenantService service) { this.service = service; }

    /** Moves the global accounts and the platform sign-in to another tenant (platform administrators with two-factor only). */
    @PutMapping
    public HomeTenantService.Result change(@RequestBody HomeTenantService.Request request, Authentication auth) {
        return service.change(request, auth);
    }
}
