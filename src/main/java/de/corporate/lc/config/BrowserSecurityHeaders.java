package de.corporate.lc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/** Enforce same-origin resources; never transmit reports externally. */
public final class BrowserSecurityHeaders implements HeaderWriter {
    public static final String POLICY = "default-src 'self'; script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
        "font-src 'self'; connect-src 'self'; frame-src 'self' blob:; " +
        "object-src 'none'; base-uri 'self'; form-action 'self'";
    @Override public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        boolean preview=request.getRequestURI().matches("/api/(documents/[0-9a-fA-F-]{36}/(preview|content)|inbox/[0-9a-fA-F-]{36}/content|training/[0-9a-fA-F-]{36}/document)");
        response.setHeader("X-Frame-Options",preview?"SAMEORIGIN":"DENY");
        response.setHeader("Content-Security-Policy", POLICY+"; frame-ancestors "+(preview?"'self'":"'none'"));
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
    }
}
