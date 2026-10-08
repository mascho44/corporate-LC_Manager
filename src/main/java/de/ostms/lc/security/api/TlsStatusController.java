package de.ostms.lc.security.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@RestController
@RequestMapping("/api/security")
public class TlsStatusController {
    public record TlsStatus(String status, String label, String message, boolean encrypted,
                            String scheme, String host, List<String> proxyIndicators, String limitation) {}

    @GetMapping("/tls-status")
    public TlsStatus status(HttpServletRequest request) {
        String scheme = forwardedScheme(request);
        boolean encrypted = request.isSecure() || "https".equalsIgnoreCase(scheme);
        List<String> indicators = proxyIndicators(request);
        if (!encrypted) return new TlsStatus("RED", "Unsichere Verbindung", "Die Verbindung ist nicht als HTTPS erkennbar.", false, scheme, request.getServerName(), indicators, limitation());
        if (!indicators.isEmpty()) return new TlsStatus("YELLOW", "Mögliche TLS-Inspection", "HTTPS ist aktiv, aber Proxy-/Inspection-Indikatoren wurden erkannt.", true, scheme, request.getServerName(), indicators, limitation());
        return new TlsStatus("GREEN", "Verschlüsselt", "HTTPS ist aktiv; es wurden keine typischen Inspection-Indikatoren erkannt.", true, scheme, request.getServerName(), indicators, limitation());
    }

    private String forwardedScheme(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String standard = request.getHeader("Forwarded");
        if (standard != null) for (String part : standard.split(";")) if (part.trim().toLowerCase().startsWith("proto=")) return part.substring(part.indexOf('=') + 1).trim();
        return request.getScheme();
    }

    private List<String> proxyIndicators(HttpServletRequest request) {
        List<String> result = new ArrayList<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return result;
        while (names.hasMoreElements()) {
            String name = names.nextElement(), lower = name.toLowerCase();
            if (lower.equals("via") || lower.contains("bluecoat") || lower.contains("zscaler")
                    || lower.equals("client-cert") || lower.equals("x-forwarded-client-cert")
                    || lower.equals("x-proxyuser-ip")) result.add(name);
        }
        return result;
    }

    private String limitation() {
        return "Die Ampel bewertet nur sichtbare HTTP- und Proxy-Indizien. Sie ist kein kryptografischer Nachweis dafür, ob TLS-Inspection stattfindet.";
    }
}
