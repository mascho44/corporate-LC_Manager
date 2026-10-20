package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.CheckResult.Severity;
import de.ostms.lc.check.api.ReviewSummary;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.company.domain.CompanyProfile;
import de.ostms.lc.company.service.CompanyProfileService;
import de.ostms.lc.document.api.AmendmentRequestInput;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AmendmentRequestServiceTest {
    private final LetterOfCreditRepository lcs = mock(LetterOfCreditRepository.class);
    private final LcDocumentRepository documents = mock(LcDocumentRepository.class);
    private final DocumentCheckService checks = mock(DocumentCheckService.class);
    private final CompanyProfileService companies = mock(CompanyProfileService.class);
    private final AmendmentRequestService service = new AmendmentRequestService(lcs, documents, checks, new DocxTemplateService(mock(DocumentTemplateService.class), companies));
    private final UUID lcId = UUID.randomUUID();
    private final LetterOfCredit lc = new LetterOfCredit();

    {
        lc.setReference("TEST-LC-1"); lc.setCurrency("EUR"); lc.setAmount(new BigDecimal("100000.00")); lc.setExpiryDate(LocalDate.of(2026, 10, 15)); lc.setExpiryPlace("FRANKFURT");
        lc.setLatestShipmentDate(LocalDate.of(2026, 10, 1)); lc.setApplicant("MUSTER IMPORT LLC\nDUBAI"); lc.setBeneficiary("BEISPIEL GMBH"); lc.setIssuingBank("TESTBANK DUBAI"); lc.setCompanyId(1);
        when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        var company = new CompanyProfile(); company.setLegalName("Beispiel GmbH"); company.setCity("Stuttgart"); company.setCountry("Germany");
        when(companies.profileForLetterhead(eq(1), any())).thenReturn(company);
    }

    private AmendmentRequestInput input(BigDecimal amount, LocalDate expiry, LocalDate shipment, String goods) {
        return new AmendmentRequestInput(LocalDate.of(2026, 10, 20), "Muster Import LLC\nDubai", "AR-1", amount, expiry, null, shipment, goods, null, null, null, "Production delayed.");
    }

    @Test void theProposalFollowsTheOpenDiscrepancies() {
        var invoice = new LcDocument(); invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE); invoice.setAmount(new BigDecimal("104000.00"));
        var bl = new LcDocument(); bl.setDocumentType(DocumentType.BILL_OF_LADING); bl.setDocumentDate(LocalDate.of(2026, 10, 6));
        when(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)).thenReturn(List.of(invoice, bl));
        when(checks.precheck(lcId)).thenReturn(new ReviewSummary("RED", 3, 0, 0, List.of(new CheckResult(Severity.DISCREPANCY, "LC_EXPIRED", "Abgelaufen"),
            new CheckResult(Severity.DISCREPANCY, "INVOICE_AMOUNT_EXCEEDED", "Zu hoch"), new CheckResult(Severity.DISCREPANCY, "SHIPMENT_DATE_EXCEEDED", "Zu spät")), "AUTOMATIC"));
        var p = service.proposal(lcId, LocalDate.of(2026, 10, 20));
        assertThat(p.toBank()).isFalse();
        assertThat(p.recipient()).startsWith("MUSTER IMPORT");
        assertThat(p.newExpiryDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(p.newAmount()).isEqualByComparingTo("104000.00");
        assertThat(p.newLatestShipmentDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(p.hints()).hasSize(3);
    }

    @Test void theSwiftTextUsesIncreaseOrDecreaseAndTheFieldTags() {
        String text = service.swiftText(lcId, input(new BigDecimal("104000"), LocalDate.of(2026, 11, 30), null, "2 UNITS MODEL B"));
        assertThat(text).contains(":21:TEST-LC-1", ":32B:EUR4000,00", ":31E:261130", ":45B:/REPALL/2 UNITS MODEL B").doesNotContain(":44C:");
        assertThat(service.swiftText(lcId, input(new BigDecimal("90000"), null, null, null))).contains(":33B:EUR10000,00");
        assertThatThrownBy(() -> service.swiftText(lcId, input(new BigDecimal("100000.00"), LocalDate.of(2026, 10, 15), null, " "))).hasMessageContaining("mindestens eine Änderung");
    }

    @Test void theLetterListsCurrentAndRequestedTerms() throws Exception {
        byte[] pdf = service.pdf(lcId, input(new BigDecimal("104000"), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 10, 10), null));
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertThat(text).contains("Request for amendment of Documentary Credit No. TEST-LC-1", "100,000.00", "104,000.00", "15 Oct 2026, FRANKFURT", "30 Nov 2026", "Production delayed.", "Beispiel GmbH");
            Path dir = Path.of("target", "docx-samples"); Files.createDirectories(dir);
            Files.write(dir.resolve("muster-aenderungsantrag.pdf"), pdf);
            ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 80), "png", dir.resolve("muster-aenderungsantrag-1.png").toFile());
        }
    }
}
