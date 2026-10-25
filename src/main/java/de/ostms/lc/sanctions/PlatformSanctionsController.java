package de.ostms.lc.sanctions;

import de.ostms.lc.user.service.PlatformAdministrationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Status of the sanctions lists and a manual refresh, for platform administrators (the lists are global). */
@RestController @RequestMapping("/api/platform/sanctions")
public class PlatformSanctionsController {
    private final SanctionsService sanctions;private final PlatformAdministrationService platform;
    public PlatformSanctionsController(SanctionsService sanctions,PlatformAdministrationService platform){this.sanctions=sanctions;this.platform=platform;}
    private void require(Authentication auth){if(!platform.enabled(auth))throw new AccessDeniedException("Platform administration with two-factor authentication is required.");platform.verifyLiveAccess(auth);}
    @GetMapping public SanctionsService.Status status(Authentication auth){require(auth);return sanctions.status();}
    @PostMapping("/refresh") public SanctionsService.Status refresh(Authentication auth){
        require(auth);
        SanctionsService.background("sanctions-refresh",sanctions::refresh);
        return sanctions.status();
    }
}
