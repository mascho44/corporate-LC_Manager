package de.ostms.lc.monitoring.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.monitoring.service.ServerLogBuffer;
import de.ostms.lc.user.service.PlatformAdministrationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The latest server log events (this process only, newest {@value ServerLogBuffer#CAPACITY}), for platform administrators. */
@RestController @RequestMapping("/api/platform/server-log")
public class PlatformServerLogController {
    public record Response(int capacity, int size, Instant since, List<ServerLogBuffer.Entry> entries) {}
    /** Viewing the log is audited, but at most once per user and ten minutes, so the 5-second auto refresh does not fill the audit log. */
    static final long AUDIT_INTERVAL_MS = 10 * 60_000L;
    private final ServerLogBuffer buffer; private final PlatformAdministrationService platform; private final AuditService audit;
    private final Map<String, Long> lastAudited = new ConcurrentHashMap<>();
    public PlatformServerLogController(ServerLogBuffer buffer, PlatformAdministrationService platform, AuditService audit) { this.buffer = buffer; this.platform = platform; this.audit = audit; }

    /** True when this user's last audit entry for viewing is older than the interval (and remembers the time). */
    boolean shouldAudit(String user, long now) {
        boolean[] due = {false};
        lastAudited.compute(user, (k, last) -> { if (last == null || now - last >= AUDIT_INTERVAL_MS) { due[0] = true; return now; } return last; });
        return due[0];
    }

    @GetMapping
    public Response get(@RequestParam(defaultValue = "INFO") String level, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "300") int limit, Authentication auth) {
        if (!platform.enabled(auth)) throw new AccessDeniedException("Platform administration with two-factor authentication is required.");
        platform.verifyLiveAccess(auth);
        if (shouldAudit(auth.getName(), System.currentTimeMillis())) audit.record(auth, "SERVER_LOG_VIEWED", "SERVER_LOG", "server-log", "Server-Log angesehen (Stufe " + level.replaceAll("[^A-Za-z]", "") + ")");
        return new Response(ServerLogBuffer.CAPACITY, buffer.size(), buffer.startedAt(), buffer.query(level, q.length() > 200 ? q.substring(0, 200) : q, limit));
    }
}
