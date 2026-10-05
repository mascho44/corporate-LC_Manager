package de.corporate.lc.user.service;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.audit.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:reset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(PasswordResetService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PasswordResetTransactionTest {
    @Autowired PasswordResetService service;
    @Autowired AppUserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean JavaMailSender mail;
    @MockitoBean AuditService audit;
    UUID userId;
    String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    @BeforeEach void seed(){
        new TransactionTemplate(transactions).execute(status->{
            tokens.deleteAll();users.deleteAll();users.flush();
            var user=new AppUser();user.setUsername("reset-user");user.setDisplayName("Reset User");user.setEmail("user@example.com");user.setPasswordHash("old");user.setTotpEnabled(true);
            userId=users.saveAndFlush(user).getId();
            tokens.saveAndFlush(new PasswordResetToken(CredentialStamp.of(secret),userId,CredentialStamp.of("old"),user.getEmail(),Instant.now().plusSeconds(1800)));return null;
        });
        when(encoder.encode("NewPassword123")).thenReturn("new");
    }
    @Test void concurrentConsumptionAllowsExactlyOneReset()throws Exception{
        var executor=Executors.newFixedThreadPool(2);
        try{
            Callable<Boolean> reset=()->{try{service.complete(secret,"NewPassword123");return true;}catch(IllegalArgumentException ex){return false;}};
            var a=executor.submit(reset);var b=executor.submit(reset);
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(tokens.count()).isZero();assertThat(users.findById(userId).orElseThrow().isTotpEnabled()).isTrue();
        }finally{executor.shutdownNow();}
    }
    @Test void auditFailureRollsBackPasswordAndTokenConsumption(){
        doThrow(new IllegalStateException("audit unavailable")).when(audit).recordInTransaction(any(),anyString(),anyString(),any(),anyString());
        assertThatThrownBy(()->service.complete(secret,"NewPassword123")).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(userId).orElseThrow().getPasswordHash()).isEqualTo("old");assertThat(tokens.count()).isEqualTo(1);
    }
}
