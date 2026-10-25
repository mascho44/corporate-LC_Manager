package de.ostms.lc.rulepack;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regelquelle je Mandant und je Akte. Berechtigungen stehen in {@code SecurityConfig}. */
@RestController
public class RuleSourceController {
    public record ModeRequest(String mode) { }

    public record LcRequest(String override, List<String> packIds) { }

    private final RuleSourceService service;

    public RuleSourceController(RuleSourceService service) {
        this.service = service;
    }

    @GetMapping("/api/settings/rule-source")
    public Map<String, String> tenantMode() {
        return Map.of("mode", service.tenantMode().name());
    }

    @PutMapping("/api/settings/rule-source")
    public Map<String, String> setTenantMode(@RequestBody ModeRequest request, Authentication auth) {
        return Map.of("mode", service.setTenantMode(request.mode(), auth).name());
    }

    @GetMapping("/api/lcs/{lcId}/rule-source")
    public RuleSourceService.View lc(@PathVariable UUID lcId) {
        return service.view(lcId);
    }

    @PutMapping("/api/lcs/{lcId}/rule-source")
    public RuleSourceService.View updateLc(@PathVariable UUID lcId, @RequestBody LcRequest request, Authentication auth) {
        return service.updateLc(lcId, request.override(), request.packIds(), auth);
    }
}
