package de.ostms.lc.monitoring;

import de.ostms.lc.lc.domain.DeadlineDigestLog;
import de.ostms.lc.lc.domain.ProcurementDigestLog;
import de.ostms.lc.lc.repository.DeadlineDigestLogRepository;
import de.ostms.lc.lc.repository.ProcurementDigestLogRepository;
import de.ostms.lc.monitoring.service.MailStatusService;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MailStatusServiceTest {
 final DeadlineDigestLogRepository deadline=mock(DeadlineDigestLogRepository.class);
 final ProcurementDigestLogRepository procurement=mock(ProcurementDigestLogRepository.class);
 final AppUserRepository users=mock(AppUserRepository.class);
 final JavaMailSender mail=mock(JavaMailSender.class);

 MailStatusService service(boolean enabled,String from){return new MailStatusService(deadline,procurement,users,mail,enabled,from);}
 static AppUser user(String email,boolean digest){var u=new AppUser();u.setUsername("anna");u.setEmail(email);u.setDeadlineDigest(digest);return u;}

 @Test void statusShowsTheLatestSummariesAndCountsOnlySubscribersWithAnAddress(){
  var older=new DeadlineDigestLog(LocalDate.of(2026,10,20));older.setRecipients(1);var newer=new DeadlineDigestLog(LocalDate.of(2026,10,25));newer.setRecipients(3);
  when(deadline.findAll()).thenReturn(List.of(older,newer));when(procurement.findAll()).thenReturn(List.of());
  when(users.findAssignableMembershipUsers()).thenReturn(List.of(user("a@example.org",true),user(null,true),user("b@example.org",false)));
  var status=service(true,"lc@example.org").status();
  assertThat(status.enabled()).isTrue();assertThat(status.senderConfigured()).isTrue();assertThat(status.deadlineDigestSubscribers()).isEqualTo(1);
  assertThat(status.lastDeadlineDigest().sentOn()).isEqualTo(LocalDate.of(2026,10,25));assertThat(status.lastDeadlineDigest().recipients()).isEqualTo(3);
  assertThat(status.lastProcurementDigest()).isNull();
 }
 @Test void theTestMailGoesOnlyToTheStoredAddressOfTheCaller(){
  when(users.findByUsernameIgnoreCase("anna")).thenReturn(Optional.of(user(" anna@example.org ",false)));
  assertThat(service(true,"lc@example.org").sendTest("anna")).isEqualTo("anna@example.org");
  var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(captor.capture());
  assertThat(captor.getValue().getTo()).containsExactly("anna@example.org");assertThat(captor.getValue().getFrom()).isEqualTo("lc@example.org");
 }
 @Test void noTestMailWhenSendingIsOffOrThereIsNoAddress(){
  assertThatThrownBy(()->service(false,"lc@example.org").sendTest("anna")).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->service(true,"").sendTest("anna")).isInstanceOf(IllegalStateException.class);
  when(users.findByUsernameIgnoreCase("anna")).thenReturn(Optional.of(user(null,false)));
  assertThatThrownBy(()->service(true,"lc@example.org").sendTest("anna")).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(mail);
 }
 @Test void aFailingMailServerGivesAFriendlyMessageWithoutDetails(){
  when(users.findByUsernameIgnoreCase("anna")).thenReturn(Optional.of(user("anna@example.org",false)));
  doThrow(new org.springframework.mail.MailSendException("secret host details")).when(mail).send(any(SimpleMailMessage.class));
  assertThatThrownBy(()->service(true,"lc@example.org").sendTest("anna")).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret");
 }
}
