package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.repository.DocumentCheckDecisionRepository;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.rulepack.InternalPackService;
import de.ostms.lc.rulepack.RuleSourceMode;
import de.ostms.lc.rulepack.RuleSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regelquelle der Dokumentenprüfung: eingebaut, importiert oder beides. */
class RuleSourceCheckTest {
    final UUID id = UUID.randomUUID();
    final InternalPackService packs = mock(InternalPackService.class);
    final RuleSourceService source = mock(RuleSourceService.class);
    final CheckResult packFinding = new CheckResult(CheckResult.Severity.OK, "PACK.demo.inv-currency", "Regel erfüllt: synthetic");

    DocumentCheckService service(Set<String> automaticRuleIds, RuleSourceMode mode) {
        var lcs = mock(LetterOfCreditRepository.class);
        var docs = mock(LcDocumentRepository.class);
        var decisions = mock(DocumentCheckDecisionRepository.class);
        var lc = new LetterOfCredit();
        lc.setRequiredDocuments(List.of("COMMERCIAL INVOICE"));
        lc.setCurrency("EUR");
        var invoice = new LcDocument();
        invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
        invoice.setOriginalFilename("synthetic.pdf");
        invoice.setAmount(new BigDecimal("100"));
        invoice.setCurrency("USD");
        when(lcs.findById(id)).thenReturn(Optional.of(lc));
        when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));
        when(source.effective(any())).thenReturn(new RuleSourceService.Effective(mode, Set.of("demo")));
        when(packs.evaluateSelected(any(), any(), any()))
            .thenReturn(new InternalPackService.PackEvaluation(List.of(packFinding), automaticRuleIds));
        var service = new DocumentCheckService(lcs, docs, decisions);
        ReflectionTestUtils.setField(service, "internalPacks", packs);
        ReflectionTestUtils.setField(service, "ruleSource", source);
        return service;
    }

    List<String> codes(DocumentCheckService service) {
        return service.precheck(id).results().stream().map(CheckResult::code).toList();
    }

    @Test
    void bothKeepsBuiltInAndPackFindings() {
        var codes = codes(service(Set.of("inv-currency"), RuleSourceMode.BOTH));
        assertThat(codes).contains("CURRENCY_MISMATCH", "PACK.demo.inv-currency");
    }

    @Test
    void importedDropsOnlyTheBuiltInChecksThatAnAutomaticPackRuleCovers() {
        var codes = codes(service(Set.of("inv-currency"), RuleSourceMode.IMPORTED));
        assertThat(codes).doesNotContain("CURRENCY_MISMATCH").contains("PACK.demo.inv-currency", "DOCUMENT_PRESENT");
    }

    @Test
    void importedKeepsTheBuiltInCheckWhenNoAutomaticPackRuleCoversIt() {
        var codes = codes(service(Set.of(), RuleSourceMode.IMPORTED));
        assertThat(codes).contains("CURRENCY_MISMATCH", "PACK.demo.inv-currency");
    }

    @Test
    void embeddedNeverEvaluatesPacks() {
        var service = service(Set.of("inv-currency"), RuleSourceMode.EMBEDDED);
        var codes = codes(service);
        assertThat(codes).contains("CURRENCY_MISMATCH").noneMatch(c -> c.startsWith("PACK."));
        verify(packs, never()).evaluateSelected(any(), any(), any());
    }

    @Test
    void withoutARuleSourceServiceTheBehaviourIsUnchanged() {
        var service = service(Set.of("inv-currency"), RuleSourceMode.IMPORTED);
        ReflectionTestUtils.setField(service, "ruleSource", null);
        assertThat(codes(service)).contains("CURRENCY_MISMATCH", "PACK.demo.inv-currency");
    }
}
