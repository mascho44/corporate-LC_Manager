package de.ostms.lc.monitoring.service;

import de.ostms.lc.capture.CaptureTokenService;
import de.ostms.lc.sanctions.SanctionsService;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operational notices by e-mail, at most once a day each: delivery stations of a tenant that have gone silent (to the tenant's administrators) and sanctions lists
 * that are stale or failed to load (to the platform administrators). Needs working SMTP (app.mail.enabled, app.mail.from).
 */
@Service
public class OperationalAlertService {
    private static final Logger log = LoggerFactory.getLogger(OperationalAlertService.class);
    static final Duration STALE = Duration.ofHours(48);
    private final CaptureTokenService captures;
    private final SanctionsService sanctions;
    private final AppUserRepository users;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final String from, baseUrl;
    // Sent today per key (tenant id or "sanctions"); a restart on the same day may send once more, which is acceptable for a notice.
    private final Map<String, LocalDate> sent = new ConcurrentHashMap<>();

    public OperationalAlertService(CaptureTokenService captures, SanctionsService sanctions, AppUserRepository users, JavaMailSender mail,
                                   @Value("${app.mail.enabled:false}") boolean enabled, @Value("${app.mail.from:}") String from,
                                   @Value("${app.security.public-base-url:}") String baseUrl) {
        this.captures = captures; this.sanctions = sanctions; this.users = users; this.mail = mail;
        this.enabled = enabled; this.from = from == null ? "" : from; this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    static String silentBody(List<CaptureTokenService.Silent> silent, String baseUrl) {
        var text = new StringBuilder("Diese Erfassungszugänge (Scan-Stationen, Poststelle) haben schon länger nichts geliefert:\n\n");
        for (var s : silent) text.append("  - ").append(s.name()).append(": ")
            .append(s.neverUsed() ? "noch nie genutzt" : "seit " + s.quietWorkingDays() + " Arbeitstagen keine Lieferung").append('\n');
        text.append("\nBitte prüfen, ob die Station läuft und der Zugang noch gültig ist.");
        if (!baseUrl.isBlank()) text.append("\nZur Verwaltung der Zugänge: ").append(baseUrl).append('\n');
        text.append("\nDiese Meldung kommt höchstens einmal täglich und nur, wenn eine Station still ist.\n");
        return text.toString();
    }

    /** Problems of the sanctions lists: a list never loaded, older than 48 hours, or with a failed last download. */
    static List<String> sanctionsProblems(SanctionsService.Status status, LocalDateTime now) {
        var out = new ArrayList<String>();
        if (!status.fetchEnabled()) return out;
        for (var s : status.sources()) {
            if (s.fetchedAt() == null) out.add(s.source() + ": noch nicht geladen" + (s.error() == null ? "" : " (" + s.error() + ")"));
            else if (Duration.between(s.fetchedAt(), now).compareTo(STALE) > 0) out.add(s.source() + ": Stand vom " + s.fetchedAt().toLocalDate() + (s.error() == null ? "" : ", letzter Abruf fehlgeschlagen (" + s.error() + ")"));
            else if (s.error() != null) out.add(s.source() + ": letzter Abruf fehlgeschlagen (" + s.error() + "), Stand noch aktuell");
        }
        return out;
    }

    /** Silent stations of the current tenant; returns the number of mails sent. */
    @Transactional(readOnly = true)
    public int silentStations(LocalDate today) {
        if (!enabled || from.isBlank()) return 0;
        String key = "capture:" + TenantContext.currentId();
        if (today.equals(sent.get(key))) return 0;
        var silent = captures.silent(today);
        if (silent.isEmpty()) return 0;
        sent.put(key, today);
        return send(users.findActiveTenantAdministrators(), "Corporate LC Manager – " + silent.size() + (silent.size() == 1 ? " Erfassungszugang ist still" : " Erfassungszugänge sind still"), silentBody(silent, baseUrl));
    }

    /** Stale or failed sanctions lists, once a day to the platform administrators. */
    @Transactional(readOnly = true)
    public int sanctionsLists(LocalDate today) {
        if (!enabled || from.isBlank() || today.equals(sent.get("sanctions"))) return 0;
        var problems = sanctionsProblems(sanctions.status(), LocalDateTime.now());
        if (problems.isEmpty()) return 0;
        sent.put("sanctions", today);
        String body = "Die Sanktionsprüfung arbeitet nicht mit aktuellen Listen:\n\n  - " + String.join("\n  - ", problems)
            + "\n\nBitte in der Plattformadministration (Übersicht → Sanktionslisten) den Abruf prüfen oder „Jetzt aktualisieren“ wählen.\n";
        return send(users.findActivePlatformAdministrators(), "Corporate LC Manager – Sanktionslisten nicht aktuell", body);
    }

    private int send(List<AppUser> recipients, String subject, String body) {
        int count = 0;
        for (var user : recipients) {
            if (user.getEmail() == null || user.getEmail().isBlank()) continue;
            try {
                var message = new SimpleMailMessage();
                message.setFrom(from); message.setTo(user.getEmail().strip()); message.setSubject(subject); message.setText(body);
                mail.send(message); count++;
            } catch (RuntimeException failure) { log.warn("Operational notice not sent: {}", failure.getClass().getSimpleName()); }
        }
        return count;
    }
}
