package de.ostms.lc.user.service;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlatformInvitationMailServiceTest {
 @Test void mailUsesFragmentSecretAndRecordsSuccessWithoutExposingPassword(){var mail=mock(JavaMailSender.class);var invitations=mock(PlatformInvitationRepository.class);var users=mock(AppUserRepository.class);UUID id=UUID.randomUUID();var user=new AppUser();user.setInvitationPending(true);when(users.findForPasswordReset(id)).thenReturn(Optional.of(user));String token="A".repeat(43);var saved=new PlatformInvitation(CredentialStamp.of(token),id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"role","credential","user@example.invalid",Instant.now().plusSeconds(3600));when(invitations.findById(saved.getTokenHash())).thenReturn(Optional.of(saved));new PlatformInvitationMailService(mail,invitations,users,"from@example.invalid","https://lc.example.invalid").deliver(new PlatformInvitationService.MailRequested(id,token,"synthetic","user@example.invalid","synthetic","Synthetic"));var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(message.capture());assertThat(message.getValue().getText()).contains("/invitation.html#token="+token).doesNotContain("?token=");assertThat(saved.getDeliveryStatus()).isEqualTo("SENT");}
 @Test void failedSmtpKeepsRetryableInvitation(){var mail=mock(JavaMailSender.class);doThrow(new org.springframework.mail.MailSendException("Synthetic")).when(mail).send(any(SimpleMailMessage.class));var invitations=mock(PlatformInvitationRepository.class);var users=mock(AppUserRepository.class);UUID id=UUID.randomUUID();var user=new AppUser();user.setInvitationPending(true);when(users.findForPasswordReset(id)).thenReturn(Optional.of(user));var saved=new PlatformInvitation("hash",id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"role","credential","user@example.invalid",Instant.now().plusSeconds(3600));when(invitations.findById(any())).thenReturn(Optional.of(saved));new PlatformInvitationMailService(mail,invitations,users,"from@example.invalid","https://lc.example.invalid").deliver(new PlatformInvitationService.MailRequested(id,"A".repeat(43),"synthetic","user@example.invalid","synthetic","Synthetic"));assertThat(saved.getDeliveryStatus()).isEqualTo("MAIL_FAILED");verify(invitations).saveAndFlush(saved);verify(invitations,never()).deleteForUser(any());}
}
