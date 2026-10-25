package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class BrowserSecurityHeadersTest {
    @Test void permitsNativeViewerOnlyForInternalPdfResponses(){
        String path="/api/documents/00000000-0000-0000-0000-000000000001/preview";
        var pdf=new MockHttpServletResponse();pdf.setContentType("application/pdf");
        new BrowserSecurityHeaders().writeHeaders(new MockHttpServletRequest("GET",path),pdf);
        assertThat(pdf.getHeader("Content-Security-Policy")).contains("object-src 'self'","frame-ancestors 'self'").doesNotContain("object-src *");
        for(String type:java.util.List.of("text/html","application/json")){
            var response=new MockHttpServletResponse();response.setContentType(type);
            new BrowserSecurityHeaders().writeHeaders(new MockHttpServletRequest("GET",path),response);
            assertThat(response.getHeader("Content-Security-Policy")).contains("object-src 'none'");
        }
        var other=new MockHttpServletResponse();other.setContentType("application/pdf");
        new BrowserSecurityHeaders().writeHeaders(new MockHttpServletRequest("GET","/untrusted.pdf"),other);
        assertThat(other.getHeader("Content-Security-Policy")).contains("object-src 'none'","frame-ancestors 'none'");
    }
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
    @Test void allowsOnlyInternalDocumentFramesAndKeepsOtherPagesProtected(){
        var id="00000000-0000-0000-0000-000000000001";
        for(String path:java.util.List.of("/api/documents/"+id+"/preview","/api/inbox/"+id+"/content","/api/training/"+id+"/document")){
            var request=new MockHttpServletRequest("GET",path);var response=new MockHttpServletResponse();
            new BrowserSecurityHeaders().writeHeaders(request,response);
            assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
            assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'self'","object-src 'none'");
        }
        var response=new MockHttpServletResponse();new BrowserSecurityHeaders().writeHeaders(new MockHttpServletRequest("GET","/"),response);
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }
}
