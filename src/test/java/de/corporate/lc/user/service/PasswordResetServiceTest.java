package de.corporate.lc.user.service;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.audit.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    @Test void pendingInvitationCannotUsePasswordResetEvenIfAccidentallyActivated(){user.setInvitationPending(true);service.request("user","user@example.com");verify(mail,never()).send(any(SimpleMailMessage.class));user.setInvitationPending(false);service.request("user","user@example.com");user.setInvitationPending(true);assertThatThrownBy(()->service.complete(secret,"NewPassword123")).isInstanceOf(IllegalArgumentException.class);verify(users,never()).save(any());}
    final AppUserRepository users=mock(AppUserRepository.class);
    final PasswordResetTokenRepository tokens=mock(PasswordResetTokenRepository.class);
    final PasswordEncoder encoder=mock(PasswordEncoder.class);
    final JavaMailSender mail=mock(JavaMailSender.class);
    final AuditService audit=mock(AuditService.class);
    final PasswordResetService service=new PasswordResetService(users,tokens,encoder,mail,audit,true,"lc@example.com","https://lc.example.com");
    final AppUser user=new AppUser();
    final UUID id=UUID.randomUUID();
    final Map<String,PasswordResetToken> stored=new HashMap<>();
    String secret;
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(user,"id",id);user.setUsername("user");user.setEmail("user@example.com");user.setPasswordHash("old-hash");user.setTotpEnabled(true);
        when(users.findByUsernameIgnoreCase("user")).thenReturn(Optional.of(user));when(users.findForPasswordReset(id)).thenReturn(Optional.of(user));
        when(tokens.saveAndFlush(any())).thenAnswer(c->{PasswordResetToken token=c.getArgument(0);stored.put(token.getTokenHash(),token);return token;});
        when(tokens.findById(anyString())).thenAnswer(c->Optional.ofNullable(stored.get(c.getArgument(0))));
        when(tokens.existsById(anyString())).thenAnswer(c->stored.containsKey(c.getArgument(0)));
        doAnswer(c->{stored.clear();return null;}).when(tokens).deleteForUser(id);
        doAnswer(c->{stored.remove(c.getArgument(0));return null;}).when(tokens).deleteById(anyString());
        doAnswer(c->{SimpleMailMessage message=c.getArgument(0);secret=message.getText().split("#token=")[1].split("\\s")[0];return null;}).when(mail).send(any(SimpleMailMessage.class));
        when(encoder.encode("NewPassword123")).thenReturn("new-hash");
    }
    @Test void tokenIsHashedExpiresAndCanOnlyBeUsedOnce(){
        service.request("user","user@example.com");
        assertThat(secret).hasSize(43);assertThat(stored).containsKey(CredentialStamp.of(secret));
        var token=stored.values().iterator().next();assertThat(token.getExpiresAt()).isBetween(Instant.now().plusSeconds(1700),Instant.now().plusSeconds(1801));
        service.complete(secret,"NewPassword123");
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");assertThat(user.isTotpEnabled()).isTrue();assertThat(stored).isEmpty();
        assertThatThrownBy(()->service.complete(secret,"NewPassword123")).isInstanceOf(IllegalArgumentException.class);
        verify(audit).recordInTransaction(any(),eq("PASSWORD_RESET_COMPLETED"),eq("USER"),eq(id),anyString());
    }
    @Test void unknownWrongEmailAndInactiveAccountsDoNotSendMail(){
        service.request("unknown","user@example.com");service.request("user","other@example.com");
        user.setActive(false);service.request("user","user@example.com");verify(mail,never()).send(any(SimpleMailMessage.class));
    }
    @Test void newRequestRevokesPreviousToken(){
        service.request("user","user@example.com");String old=secret;service.request("user","user@example.com");
        assertThatThrownBy(()->service.complete(old,"NewPassword123")).isInstanceOf(IllegalArgumentException.class);
        service.complete(secret,"NewPassword123");
    }
    @Test void expiryCredentialChangeAndEmailChangeInvalidateLink(){
        service.request("user","user@example.com");String hash=CredentialStamp.of(secret);
        stored.put(hash,new PasswordResetToken(hash,id,CredentialStamp.of("old-hash"),user.getEmail(),Instant.now().minusSeconds(1)));
        assertThatThrownBy(()->service.complete(secret,"NewPassword123")).isInstanceOf(IllegalArgumentException.class);
        service.request("user","user@example.com");user.setPasswordHash("changed");
        assertThatThrownBy(()->service.complete(secret,"NewPassword123")).isInstanceOf(IllegalArgumentException.class);
        user.setPasswordHash("old-hash");user.setEmail("changed@example.com");
        assertThatThrownBy(()->service.complete(secret,"NewPassword123")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidPasswordDoesNotConsumeValidToken(){
        service.request("user","user@example.com");
        assertThatThrownBy(()->service.complete(secret,"short")).isInstanceOf(IllegalArgumentException.class);
        assertThat(stored).hasSize(1);assertThat(user.getPasswordHash()).isEqualTo("old-hash");
    }
    @Test void failedDeliveryLeavesNoUsableTokenAndDoesNotExposeProviderMessage(){
        doThrow(new MailSendException("sensitive provider response")).when(mail).send(any(SimpleMailMessage.class));
        service.request("user","user@example.com");assertThat(stored).isEmpty();
        verify(audit).record(eq("user"),eq("PASSWORD_RESET_MAIL_FAILED"),eq("USER"),eq(id),eq("Reset-Mail konnte nicht versendet werden"),eq(false),isNull());
    }
}
