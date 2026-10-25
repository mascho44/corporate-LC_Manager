package de.ostms.lc.security.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TlsStatusControllerTest {
    private final TlsStatusController controller = new TlsStatusController();

    @Test
    void reportsGreenForHttpsWithoutInspectionIndicators() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        assertThat(controller.status(request).status()).isEqualTo("GREEN");
    }

    @Test
    void reportsYellowForHttpsWithProxyIndicator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("Via", "corporate-proxy");
        assertThat(controller.status(request).status()).isEqualTo("YELLOW");
        assertThat(controller.status(request).proxyIndicators()).contains("Via");
    }

    @Test
    void reportsRedForUnencryptedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        assertThat(controller.status(request).status()).isEqualTo("RED");
    }
}
