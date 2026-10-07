package de.corporate.lc.user.service;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component public class PlatformInvitationMailListener {
 private final PlatformInvitationMailService delivery;
 public PlatformInvitationMailListener(PlatformInvitationMailService delivery){this.delivery=delivery;}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void afterCommit(PlatformInvitationService.MailRequested event){try{delivery.deliver(event);}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Invitation delivery status could not be persisted; administrator may resend.");}}
}
