package de.corporate.lc.user.api;
import de.corporate.lc.user.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/platform/invitations") public class PlatformInvitationController {
 public record Invite(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=255) String displayName,@NotBlank @Email @Size(max=255) String email,@NotNull UUID tenantId,@NotNull UUID roleId){}
 private final PlatformInvitationService service;
 public PlatformInvitationController(PlatformInvitationService service){this.service=service;}
 @GetMapping("/choices") public List<PlatformInvitationService.TenantChoice> choices(Authentication auth){return service.choices(auth);}
 @GetMapping public List<PlatformInvitationService.InvitationView> list(Authentication auth){return service.list(auth);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public PlatformInvitationService.InvitationView invite(@Valid @RequestBody Invite request,Authentication auth){return service.invite(request.username(),request.displayName(),request.email(),request.tenantId(),request.roleId(),auth);}
 @PostMapping("/{userId}/resend") public PlatformInvitationService.InvitationView resend(@PathVariable UUID userId,Authentication auth){return service.resend(userId,auth);}
 @DeleteMapping("/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID userId,Authentication auth){service.revoke(userId,auth);}
}
