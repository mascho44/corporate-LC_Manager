package de.ostms.lc.monitoring.api;

import de.ostms.lc.monitoring.service.ServerLogBuffer;
import de.ostms.lc.user.service.PlatformAdministrationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

/** The latest server log events (this process only, newest {@value ServerLogBuffer#CAPACITY}), for platform administrators. */
@RestController @RequestMapping("/api/platform/server-log")
public class PlatformServerLogController {
    public record Response(int capacity, int size, Instant since, List<ServerLogBuffer.Entry> entries) {}
    private final ServerLogBuffer buffer; private final PlatformAdministrationService platform;
    public PlatformServerLogController(ServerLogBuffer buffer, PlatformAdministrationService platform) { this.buffer = buffer; this.platform = platform; }

    @GetMapping
    public Response get(@RequestParam(defaultValue = "INFO") String level, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "300") int limit, Authentication auth) {
        if (!platform.enabled(auth)) throw new AccessDeniedException("Platform administration with two-factor authentication is required.");
        platform.verifyLiveAccess(auth);
        return new Response(ServerLogBuffer.CAPACITY, buffer.size(), buffer.startedAt(), buffer.query(level, q.length() > 200 ? q.substring(0, 200) : q, limit));
    }
}
