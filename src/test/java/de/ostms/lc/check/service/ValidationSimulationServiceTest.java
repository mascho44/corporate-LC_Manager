package de.ostms.lc.check.service;

import de.ostms.lc.check.api.SimulationRequest;
import de.ostms.lc.check.repository.DocumentCheckDecisionRepository;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationSimulationServiceTest {
    private final UUID lcId=UUID.randomUUID(),documentId=UUID.randomUUID();
    private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
    private final LcDocumentRepository documents=mock(LcDocumentRepository.class);
    private final DocumentCheckDecisionRepository decisions=mock(DocumentCheckDecisionRepository.class);
    private final ValidationSimulationService service=new ValidationSimulationService(lcs,documents,new DocumentCheckService(lcs,documents,decisions));
    private final LetterOfCredit lc=new LetterOfCredit();
    private final LcDocument invoice=new LcDocument();
    private void setup(){
        lc.setReference("LC-TEST");lc.setAmount(new BigDecimal("100"));lc.setCurrency("EUR");lc.setExpiryDate(LocalDate.of(2030,12,31));lc.setRequiredDocuments(List.of("COMMERCIAL INVOICE"));
        ReflectionTestUtils.setField(invoice,"id",documentId);invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);invoice.setOriginalFilename("invoice.pdf");invoice.setAmount(new BigDecimal("90"));invoice.setCurrency("EUR");invoice.setDocumentDate(LocalDate.of(2030,1,1));
        when(lcs.findById(lcId)).thenReturn(Optional.of(lc));when(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)).thenReturn(List.of(invoice));
    }
    @Test void changedTestAmountDoesNotChangeOriginalOrReadReviewDecisions(){
        setup();var result=service.simulate(lcId,new SimulationRequest(new BigDecimal("50"),null,null,null));
        assertThat(result.results()).anyMatch(r->r.code().equals("INVOICE_AMOUNT_EXCEEDED"));
        assertThat(result.results()).allMatch(r->r.reviewDecision()==null);
        assertThat(lc.getAmount()).isEqualByComparingTo("100");assertThat(invoice.getAmount()).isEqualByComparingTo("90");
        verifyNoInteractions(decisions);verify(lcs,never()).save(any());verify(documents,never()).save(any());
    }
    @Test void documentOverridesOnlyAffectCopies(){
        setup();var result=service.simulate(lcId,new SimulationRequest(null,null,null,List.of(new SimulationRequest.DocumentOverride(documentId,LocalDate.of(2031,1,1),new BigDecimal("120"),"USD"))));
        assertThat(result.results()).anyMatch(r->r.code().equals("CURRENCY_MISMATCH"));
        assertThat(result.results()).anyMatch(r->r.code().equals("DOCUMENT_AFTER_EXPIRY"));
        assertThat(invoice.getCurrency()).isEqualTo("EUR");assertThat(invoice.getDocumentDate()).isEqualTo(LocalDate.of(2030,1,1));
    }
    @Test void rejectsForeignDocument(){setup();assertThatThrownBy(()->service.simulate(lcId,new SimulationRequest(null,null,null,List.of(new SimulationRequest.DocumentOverride(UUID.randomUUID(),null,null,null))))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gehört nicht");}
    @Test void rejectsDuplicateOverrides(){setup();var override=new SimulationRequest.DocumentOverride(documentId,null,null,null);assertThatThrownBy(()->service.simulate(lcId,new SimulationRequest(null,null,null,List.of(override,override)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("doppelt");}
    @Test void unchangedSimulationMatchesAutomaticFindings(){
        setup();when(decisions.findByLcId(lcId)).thenReturn(List.of());
        var baseline=new DocumentCheckService(lcs,documents,decisions).check(lcId);
        var simulation=service.simulate(lcId,new SimulationRequest(null,null,null,null));
        assertThat(simulation.results().stream().filter(r->!r.code().equals("SIMULATION_ONLY")).map(r->r.withInputFingerprint(null)).toList())
            .isEqualTo(baseline.results().stream().map(r->r.withInputFingerprint(null)).toList());
        // A copied simulation document is not a confirmable production document snapshot.
        assertThat(simulation.results().get(1).inputFingerprint()).isNotEqualTo(baseline.results().get(0).inputFingerprint());
    }
}
