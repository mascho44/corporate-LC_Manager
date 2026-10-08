package de.ostms.lc.user.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component public class InvitationMailWorker {
 private final InvitationMailQueue queue;private final boolean enabled;public InvitationMailWorker(InvitationMailQueue queue,@org.springframework.beans.factory.annotation.Value("${app.mail.enabled:false}") boolean enabled){this.queue=queue;this.enabled=enabled;}
 @Scheduled(fixedDelayString="${app.mail.invitation-interval-ms:1000}") public void tick(){if(!enabled)return;try{queue.deliverNext();}catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Invitation mail queue iteration failed; pending work remains retryable.");}}
}
