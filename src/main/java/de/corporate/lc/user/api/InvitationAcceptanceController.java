package de.corporate.lc.user.api;
import de.corporate.lc.user.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/auth/invitation") public class InvitationAcceptanceController {
 public record Accept(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(min=10,max=200) String password){@Override public String toString(){return "InvitationAcceptance[redacted]";}}
 private final PlatformInvitationService service;private final PasswordResetLimiter limiter;
 public InvitationAcceptanceController(PlatformInvitationService service,PasswordResetLimiter limiter){this.service=service;this.limiter=limiter;}
 @PostMapping("/accept") public Map<String,String> accept(@Valid @RequestBody Accept request,HttpServletRequest http){if(!limiter.allow("invite-accept",http.getRemoteAddr(),""))throw new IllegalArgumentException("Too many attempts. Please try again later.");service.accept(request.token(),request.password());return Map.of("message","Invitation accepted. Sign in with your username, new password and the tenant code from your invitation.");}
}
