package de.ostms.lc.monitoring.service;

import de.ostms.lc.lc.domain.DeadlineDigestLog;
import de.ostms.lc.lc.domain.ProcurementDigestLog;
import de.ostms.lc.lc.repository.DeadlineDigestLogRepository;
import de.ostms.lc.lc.repository.ProcurementDigestLogRepository;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

/** What an administrator needs to see when "no mail arrives": is sending switched on, when did the last summaries go out, and a test mail to oneself. */
@Service
public class MailStatusService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MailStatusService.class);
    public record Digest(LocalDate sentOn, int recipients) {}
    public record Status(boolean enabled, boolean senderConfigured, long deadlineDigestSubscribers, Digest lastDeadlineDigest, Digest lastProcurementDigest) {}

    private final DeadlineDigestLogRepository deadlineLog;
    private final ProcurementDigestLogRepository procurementLog;
    private final AppUserRepository users;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from;

    public MailStatusService(DeadlineDigestLogRepository deadlineLog, ProcurementDigestLogRepository procurementLog, AppUserRepository users, JavaMailSender mail,
                             @Value("${app.mail.enabled:false}") boolean enabled, @Value("${app.mail.from:}") String from) {
        this.deadlineLog = deadlineLog; this.procurementLog = procurementLog; this.users = users; this.mail = mail;
        this.enabled = enabled; this.from = from == null ? "" : from.strip();
    }

    @Transactional(readOnly = true)
    public Status status() {
        long subscribers = users.findAssignableMembershipUsers().stream().filter(u -> u.isDeadlineDigest() && u.getEmail() != null && !u.getEmail().isBlank()).count();
        Optional<DeadlineDigestLog> deadline = deadlineLog.findAll().stream().max(Comparator.comparing(DeadlineDigestLog::getSentOn));
        Optional<ProcurementDigestLog> procurement = procurementLog.findAll().stream().max(Comparator.comparing(ProcurementDigestLog::getSentOn));
        return new Status(enabled, !from.isBlank(), subscribers,
                deadline.map(d -> new Digest(d.getSentOn(), d.getRecipients())).orElse(null),
                procurement.map(p -> new Digest(p.getSentOn(), p.getRecipients())).orElse(null));
    }

    /** Sends a short test mail to the stored address of the caller; never to any other address. Returns the address. */
    @Transactional(readOnly = true)
    public String sendTest(String username) {
        if (!enabled || from.isBlank()) throw new IllegalStateException("Der Mailversand ist nicht eingeschaltet oder es fehlt der Absender.");
        AppUser user = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("Benutzer nicht gefunden."));
        if (user.getEmail() == null || user.getEmail().isBlank()) throw new IllegalArgumentException("Bitte zuerst im Profil eine E-Mail-Adresse hinterlegen.");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(user.getEmail().strip());
        message.setSubject("Corporate LC Manager – Test-Mail");
        message.setText("Diese Test-Mail bestätigt, dass der Mailversand funktioniert.\nThis test mail confirms that sending email works.\n");
        try { mail.send(message); }
        catch (RuntimeException failure) { { log.warn("Test mail not sent: {}", failure.getClass().getSimpleName()); throw new IllegalStateException("Die Test-Mail konnte nicht gesendet werden. Bitte SMTP-Einstellungen prüfen."); } }
        return user.getEmail().strip();
    }
}
