package de.corporate.lc.check.service;

import de.corporate.lc.check.api.*;
import de.corporate.lc.check.domain.DocumentCheckDecision;
import de.corporate.lc.check.repository.DocumentCheckDecisionRepository;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VersionedReviewTest {
    @Test void savesVersionAndAutomaticFindingSnapshotAndRejectsStaleRequest() {
        var fixture=new Fixture();var finding=fixture.amountFinding();
        when(fixture.decisions.save(any())).thenAnswer(call->call.getArgument(0));
        var saved=fixture.service.decide(fixture.id,new CheckDecisionRequest(finding.code(),finding.documentName(),"ACCEPTED","Original fachlich geprüft",finding.reviewFingerprint()),"checker");
        assertThat(saved.getRuleCatalogVersion()).isEqualTo(RuleCatalog.VERSION);
        assertThat(saved.getRuleId()).isEqualTo("LC_AMOUNT_LIMIT");
        assertThat(saved.getFindingSnapshot()).contains("DISCREPANCY","1200","1000");
        fixture.lc.setAmount(new BigDecimal("900"));
        assertThatThrownBy(()->fixture.service.decide(fixture.id,new CheckDecisionRequest(finding.code(),finding.documentName(),"ACCEPTED","Original geprüft",finding.reviewFingerprint()),"checker"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("geändert");
        verify(fixture.decisions,times(1)).save(any());
    }
    @Test void legacyAndChangedDecisionsCannotMaskCurrentDiscrepancy() {
        var fixture=new Fixture();var finding=fixture.amountFinding();
        var decision=new DocumentCheckDecision();decision.setFindingCode(finding.code());decision.setDocumentName(finding.documentName());decision.setDecision("ACCEPTED");
        when(fixture.decisions.findByLcId(fixture.id)).thenReturn(List.of(decision));
        assertStale(fixture);
        decision.setFindingFingerprint(finding.reviewFingerprint());
        assertThat(fixture.amountFinding().severity()).isEqualTo(CheckResult.Severity.OK);
        assertThat(fixture.amountFinding().reviewFingerprint()).isEqualTo(finding.reviewFingerprint());
        fixture.lc.setAmount(new BigDecimal("900"));assertStale(fixture);
    }
    @Test void rejectsMissingFingerprintAndNonexistentFinding() {
        var fixture=new Fixture();
        assertThatThrownBy(()->fixture.service.decide(fixture.id,new CheckDecisionRequest("FAKE","invoice.pdf","ACCEPTED","Prüfung"),"checker"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->fixture.service.decide(fixture.id,new CheckDecisionRequest("FAKE","invoice.pdf","ACCEPTED","Prüfung","0".repeat(64)),"checker"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(fixture.decisions,never()).save(any());
    }
    @Test void documentContentChangesInvalidateDecisionEvenWhenDisplayedEvidenceIsIdentical(){
        var fixture=new Fixture();var finding=fixture.amountFinding();
        fixture.invoice.setContent(new byte[]{1,2,3});
        assertThat(fixture.amountFinding().documentEvidence()).isEqualTo(finding.documentEvidence());
        assertThat(fixture.amountFinding().reviewFingerprint()).isNotEqualTo(finding.reviewFingerprint());
    }
    private void assertStale(Fixture fixture){
        assertThat(fixture.amountFinding().severity()).isEqualTo(CheckResult.Severity.DISCREPANCY);
        assertThat(fixture.service.check(fixture.id).results()).extracting(CheckResult::code).contains("REVIEW_STALE");
    }
    private static class Fixture {
        final UUID id=UUID.randomUUID();final LetterOfCredit lc=new LetterOfCredit();
        final LcDocument invoice=new LcDocument();
        final DocumentCheckDecisionRepository decisions=mock(DocumentCheckDecisionRepository.class);
        final DocumentCheckService service;
        Fixture(){
            lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of());
            invoice.setOriginalFilename("invoice.pdf");invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);invoice.setAmount(new BigDecimal("1200"));invoice.setCurrency("EUR");
            var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);
            when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));when(decisions.findByLcId(id)).thenReturn(List.of());
            service=new DocumentCheckService(lcs,docs,decisions);
        }
        CheckResult amountFinding(){return service.check(id).results().stream().filter(r->r.code().equals("INVOICE_AMOUNT_EXCEEDED")).findFirst().orElseThrow();}
    }
}
