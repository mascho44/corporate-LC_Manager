package de.corporate.lc.user.api;
import de.corporate.lc.user.service.*;
import de.corporate.lc.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;
    private final PasswordResetLimiter limiter;
    private final AuditService audit;
    public PasswordResetController(PasswordResetService service,PasswordResetLimiter limiter,AuditService audit){this.service=service;this.limiter=limiter;this.audit=audit;}
    public record Request(@NotBlank @Size(max=100) String username,@NotBlank @Email @Size(max=255) String email) { }
    public record Complete(@NotBlank @Size(max=43) String token,@NotBlank @Size(min=10,max=200) String password) { }
    @PostMapping("/request") public Map<String,String> request(@Valid @RequestBody Request body,HttpServletRequest request){
        if(limiter.allow("request",request.getRemoteAddr(),body.username().trim())){
            try{service.request(body.username().trim(),body.email().trim());}catch(TaskRejectedException ignored){/* Same public response when the bounded queue is full. */}
        }
        return Map.of("message","Falls Benutzername und E-Mail-Adresse zu einem aktiven Konto passen, erhalten Sie einen Reset-Link. Bitte prüfen Sie auch Ihren Spamordner.");
    }
    @PostMapping("/complete") public Map<String,String> complete(@Valid @RequestBody Complete body,HttpServletRequest request){
        if(!limiter.allow("complete",request.getRemoteAddr(),""))throw new IllegalArgumentException("Zu viele Versuche. Bitte später erneut versuchen.");
        try{service.complete(body.token(),body.password());}
        catch(IllegalArgumentException ex){audit.record("unbekannt","PASSWORD_RESET_FAILED","USER",null,"Reset-Versuch abgelehnt",false,request.getRemoteAddr());throw ex;}
        return Map.of("message","Passwort geändert. Bitte erneut anmelden. Ihre Zwei-Faktor-Anmeldung bleibt aktiv.");
    }
}
