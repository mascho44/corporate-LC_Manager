package de.ostms.lc.user.api;
import de.ostms.lc.user.service.PlatformInvitationService;
import de.ostms.lc.user.service.TenantInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Invitations of the current tenant, for its user administrators (USER_MANAGE, see SecurityConfig). */
@RestController @RequestMapping("/api/users/invitations")
public class TenantInvitationController {
 public record Invite(@NotBlank @Size(max=255) String displayName,@NotBlank @Email @Size(max=255) String email,@NotNull UUID roleId){}
 private final TenantInvitationService service;
 public TenantInvitationController(TenantInvitationService service){this.service=service;}
 @GetMapping public TenantInvitationService.Overview overview(Authentication auth){return service.overview(auth);}
 @PostMapping public TenantInvitationService.Result invite(@Valid @RequestBody Invite body,Authentication auth){return service.invite(body.displayName(),body.email(),body.roleId(),auth);}
 @PostMapping("/{userId}/resend") public PlatformInvitationService.InvitationView resend(@PathVariable UUID userId,Authentication auth){return service.resend(userId,auth);}
 @DeleteMapping("/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID userId,Authentication auth){service.revoke(userId,auth);}
}
