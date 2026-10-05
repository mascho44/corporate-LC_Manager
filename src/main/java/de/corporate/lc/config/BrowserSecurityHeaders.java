package de.corporate.lc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/** Observe CSP compatibility before enabling enforcement; never transmit reports externally. */
public final class BrowserSecurityHeaders implements HeaderWriter {
    public static final String POLICY = "default-src 'self'; script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
        "font-src 'self'; connect-src 'self'; frame-src 'self' blob:; " +
        "object-src 'none'; base-uri 'self'; form-action 'self'";
    @Override public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Content-Security-Policy-Report-Only", POLICY);
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
    }
}
