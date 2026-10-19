package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.ostms.lc.company.domain.CompanyProfile;
import de.ostms.lc.company.service.CompanyProfileService;
import de.ostms.lc.document.api.GeneratedDocumentItemRequest;
import de.ostms.lc.document.api.GeneratedDocumentRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeneratedDocumentTextTest {
    private LcDocument create(DocumentType type, List<GeneratedDocumentItemRequest> items) throws Exception {
        var lcs = mock(LetterOfCreditRepository.class);
        var documents = mock(LcDocumentRepository.class);
        var docx = mock(DocxTemplateService.class);
        var companies = mock(CompanyProfileService.class);
        var lc = new LetterOfCredit();
        lc.setReference("TEST-LC-1"); lc.setCurrency("EUR"); lc.setAmount(new BigDecimal("125000.00")); lc.setApplicant("Buyer LLC"); lc.setBeneficiary("Seller GmbH"); lc.setCompanyId(1);
        lc.setRawMessage(":20:TEST-LC-1\n:42C:AT SIGHT\n:44E:HAMBURG\n:44F:JEBEL ALI\n:45A:2 UNITS MILLING MACHINE\nCIF JEBEL ALI INCOTERMS 2020");
        var id = UUID.randomUUID();
        when(lcs.findById(id)).thenReturn(Optional.of(lc));
        var company = new CompanyProfile(); company.setLegalName("Seller GmbH"); company.setCity("Stuttgart"); company.setCountry("Germany");
        when(companies.profileForLetterhead(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any())).thenReturn(company);
        when(docx.render(any(), any())).thenReturn(new byte[]{1, 2, 3});
        when(documents.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = new GeneratedDocumentRequest(type, "INV-1", LocalDate.of(2026, 10, 5), "Machine", "2 pcs", null, null, null, "Marks", items);
        new GeneratedDocumentService(lcs, documents, docx, companies).createDocx(id, request);
        var captor = ArgumentCaptor.forClass(LcDocument.class);
        verify(documents).save(captor.capture());
        return captor.getValue();
    }

    @Test void theInvoiceAmountIsTheSumOfTheLinesNotTheCreditAmount() throws Exception {
        var saved = create(DocumentType.COMMERCIAL_INVOICE, List.of(
            new GeneratedDocumentItemRequest("1", "Machine", new BigDecimal("2"), "pcs", new BigDecimal("65750"), new BigDecimal("131500.00"), null, null, null)));
        assertThat(saved.getAmount()).isEqualByComparingTo("131500.00");
        assertThat(saved.getExtractedAmount()).isEqualByComparingTo("131500.00");
        assertThat(saved.getExtractedText()).contains("Amount: EUR 131500.00");
    }

    @Test void withoutLineAmountsTheCreditAmountStaysTheFallback() throws Exception {
        var saved = create(DocumentType.COMMERCIAL_INVOICE, List.of(new GeneratedDocumentItemRequest("1", "Machine", new BigDecimal("2"), "pcs", null, null, null, null, null)));
        assertThat(saved.getAmount()).isEqualByComparingTo("125000.00");
    }

    @Test void theCheckedTextCarriesTheShipmentTermsAndTheDeclarationOfThePrintedDocument() throws Exception {
        String text = create(DocumentType.COMMERCIAL_INVOICE, List.of(new GeneratedDocumentItemRequest("1", "Machine", new BigDecimal("2"), "pcs", new BigDecimal("1"), new BigDecimal("2"), null, null, null))).getExtractedText();
        assertThat(text).contains("Incoterm: CIF JEBEL ALI", "Payment terms: AT SIGHT", "Port of loading: HAMBURG", "Port of discharge: JEBEL ALI",
            "We certify that this invoice is true and correct", "documentary credit TEST-LC-1", "Place, date: Stuttgart, 2026-10-05");
    }

    @Test void certificatesCarryTheirDeclarationAndNoIncoterm() throws Exception {
        String text = create(DocumentType.CERTIFICATE_OF_ORIGIN, List.of(new GeneratedDocumentItemRequest("1", "Machine", new BigDecimal("2"), "pcs", null, null, null, null, null))).getExtractedText();
        assertThat(text).contains("originate in Germany").doesNotContain("Incoterm:");
    }
}
