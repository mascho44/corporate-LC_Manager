package de.ostms.lc.monitoring.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ServerLogBufferTest {
    private static ServerLogBuffer filled() {
        var b = new ServerLogBuffer(); var t = Instant.parse("2026-10-21T10:00:00Z");
        b.record("DEBUG", "A", "main", "debug line", t); b.record("INFO", "B", "main", "info line", t.plusSeconds(1));
        b.record("WARN", "C", "main", "warn line", t.plusSeconds(2)); b.record("ERROR", "D", "main", "error line", t.plusSeconds(3));
        return b;
    }

    @Test void levelFilterKeepsThatLevelAndAbove() {
        var b = filled();
        assertThat(b.query("WARN", "", 100)).extracting(ServerLogBuffer.Entry::level).containsExactly("WARN", "ERROR");
        assertThat(b.query("DEBUG", "", 100)).hasSize(4);
        assertThat(b.query("garbage", "", 100)).extracting(ServerLogBuffer.Entry::level).containsExactly("INFO", "WARN", "ERROR");
    }

    @Test void searchIsCaseInsensitiveAndLimitKeepsTheNewest() {
        var b = filled();
        assertThat(b.query("DEBUG", "INFO LINE", 100)).hasSize(1);
        assertThat(b.query("DEBUG", "", 2)).extracting(ServerLogBuffer.Entry::message).containsExactly("warn line", "error line");
    }

    @Test void secretsAreMaskedAndLongMessagesCut() {
        var b = new ServerLogBuffer();
        b.record("INFO", "X", "t", "login password=Geheim123 and Authorization: Bearer abc.def token=xyz", Instant.now());
        b.record("INFO", "X", "t", "x".repeat(ServerLogBuffer.MAX_MESSAGE + 500), Instant.now());
        var list = b.query("INFO", "", 10);
        assertThat(list.get(0).message()).doesNotContain("Geheim123").doesNotContain("abc.def").doesNotContain("xyz").contains("password=***");
        assertThat(list.get(1).message().length()).isLessThanOrEqualTo(ServerLogBuffer.MAX_MESSAGE + 2);
    }

    @Test void capacityDropsTheOldestEntries() {
        var b = new ServerLogBuffer();
        for (int i = 0; i < ServerLogBuffer.CAPACITY + 50; i++) b.record("INFO", "L", "t", "m" + i, Instant.now());
        assertThat(b.size()).isEqualTo(ServerLogBuffer.CAPACITY);
        assertThat(b.query("INFO", "", ServerLogBuffer.CAPACITY).get(0).message()).isEqualTo("m50");
    }
}
