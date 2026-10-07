package de.corporate.lc.user.api;
import de.corporate.lc.user.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/platform/users")
public class PlatformAccountCreationController {
 public record CreateAccount(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=255) String displayName,@NotBlank @Email @Size(max=255) String email,@NotBlank @Size(min=10,max=200) String password){
  @Override public String toString(){return "CreateAccount[redacted]";}
 }
 private final PlatformAccountCreationService service;
 public PlatformAccountCreationController(PlatformAccountCreationService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public PlatformAdministrationService.Account create(@Valid @RequestBody CreateAccount request,Authentication auth){return service.create(request.username(),request.displayName(),request.email(),request.password(),auth);}
}
