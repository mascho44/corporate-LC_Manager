package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Die URL-Regeln in {@link SecurityConfig} enden mit {@code anyRequest().authenticated()}: Ein neuer Endpunkt ohne Regel steht
 * jedem angemeldeten Benutzer offen. Dieser Test hält fest, dass jeder ändernde Endpunkt unter {@code /api} entweder eine
 * URL-Regel hat oder hier mit Begründung steht. Ein neuer Endpunkt ohne beides lässt den Build scheitern.
 */
class EndpointAuthorizationCoverageTest {
    private static final Set<RequestMethod> MUTATING = EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH);
    private static final Pattern RULE = Pattern.compile("\\.requestMatchers\\((.*?)\\)\\.(hasAuthority|hasAnyAuthority|hasRole|authenticated|permitAll)");
    private static final Pattern METHOD = Pattern.compile("HttpMethod\\.(\\w+)");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]+)\"");

    /** Endpunkte ohne URL-Regel mit Begründung, wer sie schützt. Jeder Eintrag muss weiterhin existieren und ohne Regel sein. */
    static final Map<String, String> EXEMPT = Map.ofEntries(
        Map.entry("PUT /api/tenants/current/settings", "Dienst prüft Administratorrolle, Benutzerverwaltungsrecht und Zwei-Faktor (TenantSettingsService.canEdit)"),
        Map.entry("POST /api/tenants/*/select", "Dienst prüft die aktive Mitgliedschaft im gewählten Mandanten"),
        Map.entry("DELETE /api/platform/tenants/*", "Dienst verlangt Plattformadministration mit Zwei-Faktor (PlatformTenantService.require)"),
        Map.entry("PUT /api/platform/tenants/*/archive", "Dienst verlangt Plattformadministration mit Zwei-Faktor (PlatformTenantService.require)"),
        Map.entry("POST /api/platform/tenants", "Dienst verlangt Plattformadministration mit Zwei-Faktor (PlatformTenantService.require)"),
        Map.entry("PUT /api/platform/tenants/*", "Dienst verlangt Plattformadministration mit Zwei-Faktor (PlatformTenantService.require)"),
        Map.entry("POST /api/platform/users", "Plattformadministration, Prüfung im Dienst"),
        Map.entry("POST /api/platform/invitations", "Plattformadministration, Prüfung im Dienst (PlatformInvitationService.authorize)"),
        Map.entry("POST /api/platform/invitations/*/resend", "Plattformadministration, Prüfung im Dienst (PlatformInvitationService.authorize)"),
        Map.entry("DELETE /api/platform/invitations/*", "Plattformadministration, Prüfung im Dienst (PlatformInvitationService.authorize)"),
        Map.entry("POST /api/platform/logout", "Abmeldung der eigenen Sitzung"),
        Map.entry("POST /api/platform/memberships", "Plattformadministration, Prüfung im Dienst (PlatformMembershipService.verifyLiveAccess)"),
        Map.entry("PUT /api/platform/memberships/*/*/role", "Plattformadministration, Prüfung im Dienst (PlatformMembershipService.verifyLiveAccess)"),
        Map.entry("PUT /api/platform/memberships/*/*/access", "Plattformadministration, Prüfung im Dienst (PlatformMembershipService.verifyLiveAccess)"),
        Map.entry("PUT /api/platform/users/*/access", "Plattformadministration, Prüfung im Dienst (PlatformAdministrationService)"),
        Map.entry("PUT /api/platform/users/*/platform-grant", "Plattformadministration, Prüfung im Dienst (PlatformAdministrationService)"),
        Map.entry("PUT /api/profile", "Selbstbedienung: ändert nur das eigene Konto"),
        Map.entry("POST /api/profile/avatar", "Selbstbedienung: ändert nur das eigene Konto"),
        Map.entry("DELETE /api/profile/avatar", "Selbstbedienung: ändert nur das eigene Konto"),
        Map.entry("PUT /api/profile/language", "Selbstbedienung: ändert nur das eigene Konto"),
        Map.entry("POST /api/auth/totp/setup", "Selbstbedienung: Zwei-Faktor des eigenen Kontos"),
        Map.entry("POST /api/auth/totp/enable", "Selbstbedienung: Zwei-Faktor des eigenen Kontos"),
        Map.entry("POST /api/auth/totp/disable", "Selbstbedienung: Zwei-Faktor des eigenen Kontos"),
        Map.entry("POST /api/auth/password", "Selbstbedienung: Passwort des eigenen Kontos"),
        Map.entry("POST /api/auth/logout", "Abmeldung der eigenen Sitzung"),
        Map.entry("POST /api/lcs/*/document-checks/simulation", "Simulation mit Testwerten, speichert keine Geschäftsdaten oder Prüfentscheidungen"));

    private record Rule(String method, String pattern) { }

    private record Endpoint(String method, String path, String source) {
        String key() { return method + " " + path; }
    }

    private static List<Rule> rules() throws Exception {
        String source = Files.readString(Path.of("src/main/java/de/ostms/lc/config/SecurityConfig.java"));
        var rules = new ArrayList<Rule>();
        var matcher = RULE.matcher(source);
        while (matcher.find()) {
            var method = METHOD.matcher(matcher.group(1));
            String http = method.find() ? method.group(1) : null;
            var literal = LITERAL.matcher(matcher.group(1));
            while (literal.find()) rules.add(new Rule(http, literal.group(1)));
        }
        return rules;
    }

    private static List<Endpoint> endpoints() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var paths = new AntPathMatcher();
        var found = new ArrayList<Endpoint>();
        for (BeanDefinition definition : scanner.findCandidateComponents("de.ostms.lc")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            var base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String prefix = base == null || base.path().length == 0 ? "" : base.path()[0];
            for (Method method : type.getDeclaredMethods()) {
                var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String suffix = mapping.path().length == 0 ? "" : mapping.path()[0];
                String path = paths.combine(prefix, suffix).replaceAll("\\{[^}/]+}", "*");
                if (!path.startsWith("/api")) continue;
                for (RequestMethod verb : mapping.method()) found.add(new Endpoint(verb.name(), path, type.getSimpleName() + "." + method.getName()));
            }
        }
        return found;
    }

    private static boolean covered(Endpoint endpoint, List<Rule> rules) {
        var matcher = new AntPathMatcher();
        String concrete = endpoint.path().replace("*", "x");
        return rules.stream().anyMatch(rule -> (rule.method() == null || rule.method().equals(endpoint.method())) && matcher.match(rule.pattern(), concrete));
    }

    @Test
    void everyMutatingApiEndpointHasAnAuthorityRuleOrAJustifiedException() throws Exception {
        var rules = rules();
        var endpoints = endpoints();
        assertThat(rules).as("Regeln aus SecurityConfig gelesen").hasSizeGreaterThan(30);
        assertThat(endpoints).as("Endpunkte gefunden").hasSizeGreaterThan(100);
        var unprotected = endpoints.stream()
            .filter(e -> MUTATING.contains(RequestMethod.valueOf(e.method())))
            .filter(e -> !covered(e, rules))
            .filter(e -> !EXEMPT.containsKey(e.key()))
            .map(e -> e.key() + "  (" + e.source() + ")")
            .sorted().toList();
        assertThat(unprotected)
            .as("Ändernde Endpunkte ohne URL-Regel in SecurityConfig und ohne begründete Ausnahme in diesem Test")
            .isEmpty();
    }

    @Test
    void exemptionsAreStillNeededAndStillExist() throws Exception {
        var rules = rules();
        var byKey = new HashMap<String, Endpoint>();
        endpoints().forEach(e -> byKey.put(e.key(), e));
        for (String key : EXEMPT.keySet()) {
            assertThat(byKey).as("Ausnahme existiert nicht mehr: " + key).containsKey(key);
            assertThat(covered(byKey.get(key), rules)).as("Ausnahme ist inzwischen durch eine URL-Regel abgedeckt: " + key).isFalse();
        }
    }
}
