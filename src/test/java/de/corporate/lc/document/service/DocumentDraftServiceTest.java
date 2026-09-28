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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentDraftServiceTest {
    @Test void flagsInvoicePositionsAboveCurrentLcAmount() {
        UUID lcId=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setIssueDate(LocalDate.of(2026,9,1));lc.setExpiryDate(LocalDate.of(2026,12,31));
        var repo=mock(DocumentDraftRepository.class);when(repo.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        var lcs=mock(LetterOfCreditRepository.class);when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());var service=new DocumentDraftService(repo,lcs,mapper,mock(GeneratedDocumentService.class));
        var item=new GeneratedDocumentItemRequest("1","Machine",BigDecimal.ONE,"pcs",new BigDecimal("1200"),new BigDecimal("1200"),null,null,null);
        var request=new GeneratedDocumentRequest(DocumentType.COMMERCIAL_INVOICE,"INV-1",LocalDate.of(2026,9,28),"Machine","1",null,null,null,null,List.of(item));
        var result=service.create(lcId,request,"editor");
        assertThat(result.validation().status()).isEqualTo("RED");
        assertThat(result.validation().blockers()).anyMatch(value->value.contains("überschreitet"));
    }
}
