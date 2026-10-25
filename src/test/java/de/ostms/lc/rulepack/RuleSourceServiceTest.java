package de.ostms.lc.rulepack;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuleSourceServiceTest {
    final TenantRepository tenants = mock(TenantRepository.class);
    final LetterOfCreditRepository lcs = mock(LetterOfCreditRepository.class);
    final InternalPackService packs = mock(InternalPackService.class);
    final AuditService audit = mock(AuditService.class);
    final RuleSourceService service = new RuleSourceService(tenants, lcs, packs, audit);
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("synthetic-admin", "unused");

    static InternalPackService.View pack(String id, boolean active) {
        return new InternalPackService.View(UUID.randomUUID(), id, "1.0.0", "Pack " + id, "sum", true, "x", LocalDateTime.now(), active, false);
    }

    @Test
    void builtInChecksAreOnlyCoveredByMatchingAutomaticPackRules() {
        assertThat(RuleSourceService.covered("CURRENCY_MISMATCH", Set.of("inv-currency"))).isTrue();
        assertThat(RuleSourceService.covered("CURRENCY_MISMATCH", Set.of())).isFalse();
        assertThat(RuleSourceService.covered("CURRENCY_MISMATCH", Set.of("inv-amount"))).isFalse();
        assertThat(RuleSourceService.covered("PRESENTATION_PERIOD_REVIEW", Set.of("pres21-bl"))).isTrue();
        assertThat(RuleSourceService.covered("PRESENTATION_PERIOD_REVIEW", Set.of("pres21"))).isFalse();
        assertThat(RuleSourceService.covered("INSURANCE_COVERAGE_REVIEW", Set.of("ins-amount-credit"))).isTrue();
        // technische Pruefungen und nicht zugeordnete Codes werden nie unterdrueckt
        for (String technical : List.of("MISSING_DOCUMENT", "PDF_REQUIRES_OCR", "DATE_NOT_CAPTURED", "LC_REFERENCE_MISMATCH", "UNKNOWN"))
            assertThat(RuleSourceService.covered(technical, Set.of("inv-currency", "inv-amount", "pres21-bl"))).isFalse();
    }

    @Test
    void effectiveSourceUsesTheTenantModeUnlessTheLcOverridesIt() {
        try (var ignored = TenantContext.open(Tenant.DEFAULT_ID)) {
            var tenant = new Tenant();
            tenant.setRuleSourceMode("IMPORTED");
            when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(tenant));
            var lc = new LetterOfCredit();
            assertThat(service.effective(lc).mode()).isEqualTo(RuleSourceMode.IMPORTED);
            assertThat(service.effective(lc).packIds()).isEmpty();
            lc.setRuleSourceOverride("EMBEDDED");
            lc.setRulePackIds("ucp600-isbp821-see, ucp600-isbp821-basis");
            var effective = service.effective(lc);
            assertThat(effective.mode()).isEqualTo(RuleSourceMode.EMBEDDED);
            assertThat(effective.packIds()).containsExactlyInAnyOrder("ucp600-isbp821-see", "ucp600-isbp821-basis");
        }
    }

    @Test
    void withoutAnyTenantRowTheBehaviourStaysAsBefore() {
        try (var ignored = TenantContext.open(Tenant.DEFAULT_ID)) {
            when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.empty());
            assertThat(service.effective(new LetterOfCredit()).mode()).isEqualTo(RuleSourceMode.BOTH);
        }
    }

    @Test
    void tenantModeIsValidatedStoredAndAudited() {
        try (var ignored = TenantContext.open(Tenant.DEFAULT_ID)) {
            var tenant = new Tenant();
            when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(tenant));
            assertThat(service.setTenantMode("IMPORTED", auth)).isEqualTo(RuleSourceMode.IMPORTED);
            assertThat(tenant.getRuleSourceMode()).isEqualTo("IMPORTED");
            verify(tenants).saveAndFlush(tenant);
            verify(audit).recordChangeInTransaction(eq(auth), eq("RULE_SOURCE_TENANT_UPDATED"), eq("TENANT"), any(), anyString(), eq("BOTH"), eq("IMPORTED"));
            assertThatThrownBy(() -> service.setTenantMode("NONSENSE", auth)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.setTenantMode(null, auth)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void lcSelectionRejectsUnknownOrMalformedPacksAndClearsOnEmpty() {
        try (var ignored = TenantContext.open(Tenant.DEFAULT_ID)) {
            var id = UUID.randomUUID();
            var lc = new LetterOfCredit();
            when(lcs.findById(id)).thenReturn(Optional.of(lc));
            when(tenants.findById(Tenant.DEFAULT_ID)).thenReturn(Optional.of(new Tenant()));
            when(packs.list()).thenReturn(List.of(pack("ucp600-isbp821-see", true), pack("ucp600-isbp821-luft", false)));
            assertThatThrownBy(() -> service.updateLc(id, null, List.of("does-not-exist"), auth)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.updateLc(id, null, List.of("Bad Id!"), auth)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.updateLc(id, "NONSENSE", List.of(), auth)).isInstanceOf(IllegalArgumentException.class);
            verify(lcs, never()).saveAndFlush(any());

            var view = service.updateLc(id, "IMPORTED", List.of("ucp600-isbp821-see", "ucp600-isbp821-see"), auth);
            assertThat(lc.getRuleSourceOverride()).isEqualTo("IMPORTED");
            assertThat(lc.getRulePackIds()).isEqualTo("ucp600-isbp821-see");
            assertThat(view.effective()).isEqualTo(RuleSourceMode.IMPORTED);
            assertThat(view.activePacks()).extracting(RuleSourceService.PackOption::packId).containsExactly("ucp600-isbp821-see");
            verify(audit).recordChangeInTransaction(eq(auth), eq("RULE_SOURCE_LC_UPDATED"), eq("LC"), eq(id), anyString(), anyString(), anyString());

            service.updateLc(id, null, List.of(), auth);
            assertThat(lc.getRuleSourceOverride()).isNull();
            assertThat(lc.getRulePackIds()).isNull();
        }
    }
}
