package de.corporate.lc.user.service;

import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.*;

/** Bounded single-instance limiter. Count attempts before expensive password/TOTP work. */
@Service
public class LoginAttemptLimiter {
    private static final long WINDOW = 300_000;
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    public LoginAttemptLimiter() { this(Clock.systemUTC()); }
    LoginAttemptLimiter(Clock clock) { this.clock = clock; }
    private record Bucket(long started, int attempts) { }
    public synchronized boolean allow(String stage, String username, String ip) {
        long now = clock.millis();
        buckets.entrySet().removeIf(e -> now - e.getValue().started() >= WINDOW);
        String account = stage + ":account:" + normalized(username);
        String address = stage + ":ip:" + normalized(ip);
        if (buckets.size() + (buckets.containsKey(account) ? 0 : 1) + (buckets.containsKey(address) ? 0 : 1) > MAX_KEYS) return false;
        boolean allowed = count(account) < 5 && count(address) < 30;
        if (allowed) { increment(account, now); increment(address, now); }
        return allowed;
    }
    public synchronized void succeeded(String stage, String username) {
        buckets.remove(stage + ":account:" + normalized(username));
    }
    private int count(String key) { var b = buckets.get(key); return b == null ? 0 : b.attempts(); }
    private void increment(String key, long now) { var b = buckets.get(key); buckets.put(key, new Bucket(b == null ? now : b.started(), count(key) + 1)); }
    private String normalized(String value) { return CredentialStamp.of(value == null ? "" : value.strip().toLowerCase(Locale.ROOT)); }
}
