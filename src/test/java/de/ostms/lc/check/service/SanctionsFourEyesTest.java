package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckDecisionRequest;
import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.domain.DocumentCheckDecision;
import de.ostms.lc.check.repository.DocumentCheckDecisionRepository;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.sanctions.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SanctionsFourEyesTest {
    @Test void aFalsePositiveNeedsASecondPersonAndIsThenRemembered() {
        UUID id=UUID.randomUUID();var lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of());lc.setBeneficiary("Bank Melli Iran\nTehran");
        ReflectionTestUtils.setField(lc,"id",id);
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of());when(decisions.findByLcId(id)).thenReturn(List.of());
        when(decisions.findByLcIdAndFindingCodeAndDocumentNameAndFindingFingerprint(any(),any(),any(),any())).thenReturn(Optional.empty());
        when(decisions.save(any())).thenAnswer(call->call.getArgument(0));
        var sanctions=new SanctionsService(mock(SanctionsListRepository.class),false,"","","","");
        var clearances=mock(SanctionsClearanceRepository.class);when(clearances.findAll()).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(sanctions,"setClearances",clearances);
        ReflectionTestUtils.invokeMethod(sanctions,"useEntries","OFAC",List.of(new SanctionsListParser.Entry("OFAC","25578",SanctionsListParser.Kind.ENTITY,"BANK MELLI IRAN",List.of(),"IRAN")));
        var service=new DocumentCheckService(lcs,docs,decisions);ReflectionTestUtils.setField(service,"sanctions",sanctions);

        var hit=service.check(id).results().stream().filter(r->r.code().equals("SANCTIONS_MATCH")).findFirst().orElseThrow();
        var first=service.decide(id,new CheckDecisionRequest(hit.code(),"","ACCEPTED","Andere Bank, gleicher Name",hit.reviewFingerprint()),"anna");
        assertThat(first.getSecondReviewedBy()).isNull();
        when(decisions.findByLcId(id)).thenReturn(List.of(first));
        when(decisions.findByLcIdAndFindingCodeAndDocumentNameAndFindingFingerprint(eq(id),eq(hit.code()),eq(""),eq(first.getFindingFingerprint()))).thenReturn(Optional.of(first));

        var pending=service.check(id).results().stream().filter(r->r.code().equals("SANCTIONS_MATCH")).findFirst().orElseThrow();
        assertThat(pending.reviewDecision()).isNull();assertThat(pending.reviewedBy()).isEqualTo("anna");assertThat(pending.reviewComment()).contains("Freigabe durch eine zweite Person");

        var request=new CheckDecisionRequest(hit.code(),"","ACCEPTED","Freigabe",pending.reviewFingerprint());
        assertThatThrownBy(()->service.decide(id,request,"anna",true)).hasMessageContaining("Vier-Augen");
        assertThatThrownBy(()->service.decide(id,request,"bob",false)).hasMessageContaining("Sanktionstreffer freigeben");
        var approved=service.decide(id,request,"bob",true);
        assertThat(approved.getSecondReviewedBy()).isEqualTo("bob");assertThat(approved.getReviewedBy()).isEqualTo("anna");
        verify(clearances).save(argThat(c->c.getPartyKey().equals("BANK MELLI IRAN")&&c.getReference().equals("25578")&&c.getApprovedBy().equals("bob")));

        assertThat(service.check(id).results().stream().filter(r->r.code().equals("SANCTIONS_MATCH")).findFirst().orElseThrow().reviewDecision()).isEqualTo("ACCEPTED");

        // A later file with the same party: the remembered clearance replaces the hit, unless the list entry changes.
        var saved=new SanctionsClearance();saved.setPartyKey("BANK MELLI IRAN");saved.setPartyValue("Bank Melli Iran");saved.setSource("OFAC");saved.setReference("25578");saved.setListedName("BANK MELLI IRAN");saved.setClearedBy("anna");saved.setApprovedBy("bob");
        saved.setEntryHash(sanctions.clearanceKey(hit).orElseThrow().entryHash());
        when(clearances.findAll()).thenReturn(List.of(saved));
        var codes=sanctions.screen(lc).stream().map(CheckResult::code).toList();
        assertThat(codes).contains("SANCTIONS_CLEARED").doesNotContain("SANCTIONS_MATCH");
        ReflectionTestUtils.invokeMethod(sanctions,"useEntries","OFAC",List.of(new SanctionsListParser.Entry("OFAC","25578",SanctionsListParser.Kind.ENTITY,"BANK MELLI IRAN",List.of("MELLI BANK"),"IRAN")));
        assertThat(sanctions.screen(lc).stream().map(CheckResult::code).toList()).contains("SANCTIONS_MATCH");
    }
}
