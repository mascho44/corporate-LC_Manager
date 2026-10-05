package de.corporate.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class BrowserSecurityHeadersTest {
    @Test void enforcesWithoutSendingReportsElsewhere() {
        var response=new MockHttpServletResponse();
        new BrowserSecurityHeaders().writeHeaders(new MockHttpServletRequest(),response);
        assertThat(response.getHeader("Content-Security-Policy-Report-Only")).isNull();
        assertThat(response.getHeader("Content-Security-Policy"))
            .contains("script-src 'self'","object-src 'none'","form-action 'self'")
            .doesNotContain("report-uri","report-to","unsafe-eval");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()","microphone=()","geolocation=()");
    }
    @Test void supportsInternalPdfFramesAndAuthenticatorImages() {
        assertThat(BrowserSecurityHeaders.POLICY)
            .contains("frame-src 'self' blob:","img-src 'self' data: blob:","style-src 'self' 'unsafe-inline'");
    }
}
