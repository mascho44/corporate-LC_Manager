package de.ostms.lc.monitoring.api;

import de.ostms.lc.monitoring.service.ServerLogBuffer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformServerLogControllerTest {
    @Test void viewingIsAuditedOncePerUserAndInterval() {
        var controller = new PlatformServerLogController(new ServerLogBuffer(), null, null);
        long t = 1_000_000L;
        assertThat(controller.shouldAudit("admin", t)).isTrue();
        assertThat(controller.shouldAudit("admin", t + 5_000)).isFalse();
        assertThat(controller.shouldAudit("other", t + 5_000)).isTrue();
        assertThat(controller.shouldAudit("admin", t + PlatformServerLogController.AUDIT_INTERVAL_MS)).isTrue();
    }
}
