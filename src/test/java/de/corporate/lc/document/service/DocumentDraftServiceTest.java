package de.corporate.lc.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.corporate.lc.document.api.*;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.DocumentDraftRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentDraftServiceTest {
    @Test void flagsInvoicePositionsAboveCurrentLcAmount() {
        UUID lcId=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setIssueDate(LocalDate.of(2026,9,1));lc.setExpiryDate(LocalDate.of(2026,12,31));
        var repo=mock(DocumentDraftRepository.class);when(repo.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        var lcs=mock(LetterOfCreditRepository.class);when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());var service=new DocumentDraftService(repo,lcs,mapper,mock(GeneratedDocumentService.class),mock(DocumentApprovalPolicyService.class));
        var item=new GeneratedDocumentItemRequest("1","Machine",BigDecimal.ONE,"pcs",new BigDecimal("1200"),new BigDecimal("1200"),null,null,null);
        var request=new GeneratedDocumentRequest(DocumentType.COMMERCIAL_INVOICE,"INV-1",LocalDate.of(2026,9,28),"Machine","1",null,null,null,null,List.of(item));
        var result=service.create(lcId,request,"editor");
        assertThat(result.validation().status()).isEqualTo("RED");
        assertThat(result.validation().blockers()).anyMatch(value->value.contains("überschreitet"));
    }

    @Test void requiresDistinctMakerCheckerAndApprover() throws Exception {
        UUID lcId=UUID.randomUUID(),draftId=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setExpiryDate(LocalDate.of(2026,12,31));lc.setAmount(new BigDecimal("100000"));lc.setCurrency("EUR");
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());
        var request=new GeneratedDocumentRequest(DocumentType.BENEFICIARY_CERTIFICATE,"CERT-1",LocalDate.of(2026,9,28),"Certificate",null,null,null,null,null,List.of());
        var draft=new DocumentDraft();draft.setLcId(lcId);draft.setCreatedBy("maker");draft.setUpdatedBy("maker");draft.setDocumentType(request.type());draft.setDocumentNumber(request.documentNumber());draft.setDataJson(mapper.writeValueAsString(request));draft.setStatus(DocumentDraftStatus.SUBMITTED);draft.setSubmittedBy("maker");
        var repo=mock(DocumentDraftRepository.class);when(repo.findById(draftId)).thenReturn(Optional.of(draft));
        var lcs=mock(LetterOfCreditRepository.class);when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        var policy=mock(DocumentApprovalPolicyService.class);when(policy.requiredApprovals(any(),any())).thenReturn(2);
        var service=new DocumentDraftService(repo,lcs,mapper,mock(GeneratedDocumentService.class),policy);
        assertThatThrownBy(()->service.status(lcId,draftId,DocumentDraftStatus.REVIEWED,"maker")).isInstanceOf(IllegalStateException.class).hasMessageContaining("Ersteller darf");
        var checked=service.status(lcId,draftId,DocumentDraftStatus.REVIEWED,"checker");
        assertThat(checked.status()).isEqualTo(DocumentDraftStatus.REVIEWED);assertThat(checked.checkedBy()).isEqualTo("checker");
        assertThatThrownBy(()->service.status(lcId,draftId,DocumentDraftStatus.FINAL,"checker")).isInstanceOf(IllegalStateException.class).hasMessageContaining("Prüfer und Freigeber");
        var firstApproval=service.status(lcId,draftId,DocumentDraftStatus.FINAL,"approver");
        assertThat(firstApproval.status()).isEqualTo(DocumentDraftStatus.REVIEWED);assertThat(firstApproval.approvalCount()).isEqualTo(1);assertThat(firstApproval.requiredApprovals()).isEqualTo(2);
        var result=service.status(lcId,draftId,DocumentDraftStatus.FINAL,"approver2");
        assertThat(result.status()).isEqualTo(DocumentDraftStatus.FINAL);assertThat(result.approvalUsers()).containsExactly("approver","approver2");assertThat(result.approvedAt()).isNotNull();
    }

}
