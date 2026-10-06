package de.corporate.lc.user.service;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.user.domain.PasswordResetToken;
import de.corporate.lc.user.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class PasswordResetService {
    private final AppUserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final AuditService audit;
    private final boolean enabled;
    private final String from,baseUrl;
    private final SecureRandom random=new SecureRandom();
    private final Clock clock=Clock.systemUTC();
    public PasswordResetService(AppUserRepository users,PasswordResetTokenRepository tokens,PasswordEncoder encoder,JavaMailSender mail,AuditService audit,
        @Value("${app.mail.enabled:false}") boolean enabled,@Value("${app.mail.from:}") String from,
        @Value("${app.security.public-base-url:https://lc.example.com}") String baseUrl){
        this.users=users;this.tokens=tokens;this.encoder=encoder;this.mail=mail;this.audit=audit;this.enabled=enabled;this.from=from;
        URI uri=URI.create(baseUrl);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("Public base URL must be an HTTPS address.");
        this.baseUrl=baseUrl.replaceAll("/+$","");
    }
    @Async("passwordResetExecutor") @Transactional
    public void request(String username,String email){
        var match=users.findByUsernameIgnoreCase(username);
        if(match.isEmpty())return;
        var user=users.findForPasswordReset(match.get().getId()).orElse(null);
        if(user!=null&&!de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID.equals(user.getTenantId()))return;
        if(user==null||!user.isActive()||user.getEmail()==null||!user.getEmail().equalsIgnoreCase(email))return;
        if(!enabled||from.isBlank()){audit.record(user.getUsername(),"PASSWORD_RESET_MAIL_FAILED","USER",user.getId(),"Reset-Versand nicht eingerichtet",false,null);return;}
        tokens.deleteExpired(clock.instant());tokens.deleteForUser(user.getId());
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash=CredentialStamp.of(secret);
        tokens.saveAndFlush(new PasswordResetToken(hash,user.getId(),CredentialStamp.of(user.getPasswordHash()),user.getEmail(),clock.instant().plusSeconds(1800)));
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(user.getEmail());message.setSubject("Corporate LC Manager – Passwort zurücksetzen");
        message.setText("Für Ihr Benutzerkonto wurde ein neues Passwort angefordert.\n\n"+baseUrl+"/password-reset.html#token="+secret+
            "\n\nDieser Link ist 30 Minuten gültig und kann einmal verwendet werden. Ihre Zwei-Faktor-Anmeldung bleibt aktiv.\nFalls Sie die Anfrage nicht gestellt haben, ignorieren Sie diese Nachricht.");
        try{mail.send(message);audit.record(user.getUsername(),"PASSWORD_RESET_REQUESTED","USER",user.getId(),"Reset-Link per E-Mail angefordert",true,null);}
        catch(RuntimeException ex){tokens.deleteById(hash);audit.record(user.getUsername(),"PASSWORD_RESET_MAIL_FAILED","USER",user.getId(),"Reset-Mail konnte nicht versendet werden",false,null);}
    }
    @Transactional
    public void complete(String token,String password){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        String hash=CredentialStamp.of(token);
        var initial=tokens.findById(hash).orElseThrow(PasswordResetService::invalid);
        var user=users.findForPasswordReset(initial.getUserId()).orElseThrow(PasswordResetService::invalid);
        if(!de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID.equals(user.getTenantId()))throw invalid();
        if(!tokens.existsById(hash))throw invalid();
        var saved=tokens.findById(hash).orElseThrow(PasswordResetService::invalid);
        if(!user.isActive()||!saved.getExpiresAt().isAfter(clock.instant())||!saved.getCredentialStamp().equals(CredentialStamp.of(user.getPasswordHash()))||!saved.getEmail().equals(user.getEmail()))throw invalid();
        UserService.validatePassword(password);
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Das Passwort darf höchstens 72 UTF-8-Bytes enthalten.");
        if(password.length()>200||encoder.matches(password,user.getPasswordHash()))throw new IllegalArgumentException("Bitte ein neues Passwort mit höchstens 200 Zeichen wählen.");
        user.setPasswordHash(encoder.encode(password));users.save(user);tokens.deleteForUser(user.getId());
        audit.recordInTransaction(UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of()),"PASSWORD_RESET_COMPLETED","USER",user.getId(),"Passwort zurückgesetzt; bisherige Sitzungen ungültig; TOTP unverändert");
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Der Reset-Link ist ungültig oder abgelaufen. Bitte einen neuen Link anfordern.");}
}
