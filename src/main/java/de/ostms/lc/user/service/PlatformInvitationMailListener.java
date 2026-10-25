package de.ostms.lc.user.service;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component public class PlatformInvitationMailListener {
 private final InvitationMailQueue queue;
 public PlatformInvitationMailListener(InvitationMailQueue queue){this.queue=queue;}
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
 public void beforeCommit(PlatformInvitationService.MailRequested event){queue.enqueue(event);}
}
