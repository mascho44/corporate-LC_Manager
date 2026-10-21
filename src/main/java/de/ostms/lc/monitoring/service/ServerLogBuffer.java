package de.ostms.lc.monitoring.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The latest log events of this server process in memory, for platform administrators. Secrets are masked; nothing is written to disk. */
@Component
public class ServerLogBuffer {
    public static final int CAPACITY = 2000;
    public static final int MAX_MESSAGE = 4000;
    public record Entry(long seq, Instant time, String level, String logger, String thread, String message) {}
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwort|passphrase|secret|token|authorization|api[-_]?key|x-csrf-token)(\"?\\s*[=:]\\s*\"?)(Bearer\\s+)?[^\\s,;\"&}]+");
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private long sequence = 0;
    private final Instant startedAt = Instant.now();
    private AppenderBase<ILoggingEvent> appender;

    @PostConstruct
    void install() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) return;
        appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) { add(event); }
        };
        appender.setContext(context);
        appender.setName("server-log-buffer");
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    void uninstall() {
        if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
    }

    void add(ILoggingEvent event) {
        String text = event.getFormattedMessage();
        if (event.getThrowableProxy() != null) text += "\n" + firstLines(ThrowableProxyUtil.asString(event.getThrowableProxy()), 25);
        record(event.getLevel().toString(), shortName(event.getLoggerName()), event.getThreadName(), text, Instant.ofEpochMilli(event.getTimeStamp()));
    }

    /** Adds one entry (also used by tests); the message is masked and cut to {@link #MAX_MESSAGE} characters. */
    public synchronized void record(String level, String logger, String thread, String message, Instant time) {
        String safe = mask(message == null ? "" : message);
        if (safe.length() > MAX_MESSAGE) safe = safe.substring(0, MAX_MESSAGE) + " …";
        entries.addLast(new Entry(++sequence, time, level, logger, thread, safe));
        while (entries.size() > CAPACITY) entries.removeFirst();
    }

    static String mask(String text) { return SECRET.matcher(text).replaceAll(m -> m.group(1) + m.group(2) + "***"); }
    private static String shortName(String logger) { int dot = logger == null ? -1 : logger.lastIndexOf('.'); return dot < 0 || logger == null ? String.valueOf(logger) : logger.substring(dot + 1); }
    private static String firstLines(String text, int lines) {
        String[] all = text.split("\\R"); if (all.length <= lines) return text;
        return String.join("\n", java.util.Arrays.copyOf(all, lines)) + "\n… (" + (all.length - lines) + " weitere Zeilen)";
    }

    /** Entries at or above {@code minLevel} (ERROR, WARN, INFO, DEBUG), containing {@code query} (case-insensitive, may be blank), the newest {@code limit} in chronological order. */
    public synchronized List<Entry> query(String minLevel, String query, int limit) {
        int threshold = Level.toLevel(minLevel == null ? "INFO" : minLevel.toUpperCase(Locale.ROOT), Level.INFO).toInt();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        var out = new ArrayList<Entry>();
        var it = entries.descendingIterator();
        while (it.hasNext() && out.size() < Math.max(1, Math.min(limit, CAPACITY))) {
            Entry e = it.next();
            if (Level.toLevel(e.level(), Level.INFO).toInt() < threshold) continue;
            if (!q.isEmpty() && !(e.message().toLowerCase(Locale.ROOT).contains(q) || e.logger().toLowerCase(Locale.ROOT).contains(q))) continue;
            out.add(e);
        }
        java.util.Collections.reverse(out);
        return out;
    }
    public synchronized int size() { return entries.size(); }
    public Instant startedAt() { return startedAt; }
}
