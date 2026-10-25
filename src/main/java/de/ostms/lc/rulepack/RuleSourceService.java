package de.ostms.lc.rulepack;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Regelquelle der Dokumentenprüfung: eingebaute Prüfungen, importierte Rule Packs oder beides.
 * Die Wahl gilt für den Mandanten und kann je Akte überschrieben werden; je Akte lässt sich außerdem
 * festlegen, welche Packs (zum Beispiel nur das Modul "See") angewendet werden. Ohne Eintrag gilt das
 * bisherige Verhalten: beides, alle aktiven Packs.
 */
@Service
public class RuleSourceService {
    public record Effective(RuleSourceMode mode, Set<String> packIds) {
        public static Effective defaults() { return new Effective(RuleSourceMode.BOTH, Set.of()); }
    }

    public record PackOption(String packId, String name, String version) { }

    public record View(RuleSourceMode tenantMode, RuleSourceMode override, RuleSourceMode effective,
                       List<String> selectedPackIds, List<PackOption> activePacks) { }

    /**
     * Eingebaute Vergleichsprüfung (Befundcode) und die Pack-Regeln, die dasselbe automatisch prüfen. Ein Eintrag
     * mit "*" am Ende gilt für alle Regel-IDs mit diesem Anfang. Unterdrückt wird eine eingebaute Prüfung nur,
     * wenn mindestens eine passende AUTOMATISCHE Regel in einem ausgewerteten Pack steht; manuelle Pack-Regeln
     * ersetzen keine automatische Prüfung. Technische Prüfungen (OCR, Pflichtdokumente, LC-Referenz) fehlen hier
     * mit Absicht und laufen immer.
     */
    static final Map<String, List<String>> COVERED = Map.ofEntries(
        Map.entry("CURRENCY_MISMATCH", List.of("inv-currency")),
        Map.entry("INVOICE_AMOUNT_EXCEEDED", List.of("inv-amount")),
        Map.entry("DRAFT_AMOUNT_EXCEEDED", List.of("draft-amount")),
        Map.entry("DRAFT_CURRENCY_MISMATCH", List.of("draft-currency")),
        Map.entry("INSURANCE_CURRENCY_MISMATCH", List.of("ins-currency")),
        Map.entry("INSURANCE_COVERAGE_REVIEW", List.of("ins-amount-110", "ins-amount-credit")),
        Map.entry("INSURANCE_COVERAGE_BASIS_REVIEW", List.of("ins-amount-110", "ins-amount-credit")),
        Map.entry("INSURANCE_DATE_AFTER_SHIPMENT_REVIEW", List.of("ins-eff-*")),
        Map.entry("PRESENTATION_PERIOD_REVIEW", List.of("pres21-*")));

    private static final Pattern PACK_ID = Pattern.compile("[a-z][a-z0-9-]{2,30}");
    private static final int MAX_PACKS = 20;

    private final TenantRepository tenants;
    private final LetterOfCreditRepository lcs;
    private final InternalPackService packs;
    private final AuditService audit;

    public RuleSourceService(TenantRepository tenants, LetterOfCreditRepository lcs, InternalPackService packs,
                             AuditService audit) {
        this.tenants = tenants;
        this.lcs = lcs;
        this.packs = packs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public RuleSourceMode tenantMode() {
        return tenants.findById(TenantContext.currentId())
            .map(t -> RuleSourceMode.parse(t.getRuleSourceMode()))
            .orElse(RuleSourceMode.BOTH);
    }

    /** Gültige Regelquelle und Pack-Auswahl für eine Akte (leere Auswahl = alle aktiven Packs). */
    @Transactional(readOnly = true)
    public Effective effective(LetterOfCredit lc) {
        RuleSourceMode override = RuleSourceMode.parse(lc.getRuleSourceOverride());
        return new Effective(override != null ? override : tenantMode(), parseIds(lc.getRulePackIds()));
    }

    /** Ob eine eingebaute Prüfung durch eine automatische Pack-Regel abgedeckt ist. */
    public static boolean covered(String builtinCode, Set<String> automaticRuleIds) {
        var rules = COVERED.get(builtinCode);
        if (rules == null) return false;
        for (String rule : rules) {
            if (rule.endsWith("*")) {
                String prefix = rule.substring(0, rule.length() - 1);
                if (automaticRuleIds.stream().anyMatch(id -> id.startsWith(prefix))) return true;
            } else if (automaticRuleIds.contains(rule)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public RuleSourceMode setTenantMode(String mode, Authentication auth) {
        RuleSourceMode parsed = RuleSourceMode.parse(mode);
        if (parsed == null) throw new IllegalArgumentException("Regelquelle fehlt.");
        var tenant = tenants.findById(TenantContext.currentId())
            .orElseThrow(() -> new AccessDeniedException("Tenant not available."));
        String before = tenant.getRuleSourceMode();
        tenant.setRuleSourceMode(parsed.name());
        tenants.saveAndFlush(tenant);
        audit.recordChangeInTransaction(auth, "RULE_SOURCE_TENANT_UPDATED", "TENANT", tenant.getId(),
            "Regelquelle des Mandanten geändert", before, parsed.name());
        return parsed;
    }

    @Transactional(readOnly = true)
    public View view(UUID lcId) {
        var lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("LC nicht gefunden."));
        var tenantMode = tenantMode();
        var override = RuleSourceMode.parse(lc.getRuleSourceOverride());
        return new View(tenantMode, override, override != null ? override : tenantMode,
            List.copyOf(parseIds(lc.getRulePackIds())), activePacks());
    }

    @Transactional
    public View updateLc(UUID lcId, String override, List<String> packIds, Authentication auth) {
        var lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("LC nicht gefunden."));
        RuleSourceMode mode = RuleSourceMode.parse(override);
        var ids = validatedIds(packIds);
        String before = describe(lc.getRuleSourceOverride(), lc.getRulePackIds());
        lc.setRuleSourceOverride(mode == null ? null : mode.name());
        lc.setRulePackIds(ids.isEmpty() ? null : String.join(",", ids));
        lcs.saveAndFlush(lc);
        audit.recordChangeInTransaction(auth, "RULE_SOURCE_LC_UPDATED", "LC", lcId,
            "Regelquelle und Pack-Auswahl der Akte geändert", before, describe(lc.getRuleSourceOverride(), lc.getRulePackIds()));
        return view(lcId);
    }

    private List<PackOption> activePacks() {
        return packs.list().stream().filter(InternalPackService.View::active)
            .map(v -> new PackOption(v.packId(), v.name(), v.version())).toList();
    }

    private Set<String> validatedIds(List<String> packIds) {
        if (packIds == null || packIds.isEmpty()) return Set.of();
        var ids = new LinkedHashSet<String>();
        for (String id : packIds) {
            if (id == null || !PACK_ID.matcher(id).matches()) throw new IllegalArgumentException("Ungültige Pack-ID.");
            ids.add(id);
        }
        if (ids.size() > MAX_PACKS) throw new IllegalArgumentException("Höchstens " + MAX_PACKS + " Packs auswählbar.");
        var known = new HashSet<String>();
        packs.list().forEach(v -> known.add(v.packId()));
        if (!known.containsAll(ids)) throw new IllegalArgumentException("Unbekanntes Pack in der Auswahl.");
        return ids;
    }

    private static Set<String> parseIds(String stored) {
        var ids = new LinkedHashSet<String>();
        if (stored == null || stored.isBlank()) return ids;
        for (String part : stored.split(",")) if (!part.isBlank()) ids.add(part.trim());
        return ids;
    }

    private static String describe(String override, String ids) {
        return "Regelquelle=" + (override == null ? "Standard" : override) + "; Packs=" + (ids == null ? "alle aktiven" : ids);
    }
}
