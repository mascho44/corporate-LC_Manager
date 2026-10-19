package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.ReviewSummary;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.company.domain.CompanyProfile;
import de.ostms.lc.company.service.CompanyProfileService;
import de.ostms.lc.document.api.SubmissionPackageRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubmissionPackageServiceTest {
    private final UUID lcId = UUID.randomUUID();
    private LetterOfCredit lc;
    private DocumentCheckService checks;
    private SubmissionPackageService service;
    private final List<LcDocument> stored = new ArrayList<>();

    private LcDocument document(DocumentType type, String filename, String number, LocalDate date) {
        var d = new LcDocument();
        d.setDocumentType(type); d.setOriginalFilename(filename); d.setExtractedDocumentNumber(number); d.setDocumentDate(date);
        d.setContent(("content of " + filename).getBytes()); d.setContentType("application/octet-stream");
        stored.add(d);
        return d;
    }

    @BeforeEach void setUp() {
        var lcs = mock(LetterOfCreditRepository.class);
        var documents = mock(LcDocumentRepository.class);
        var companies = mock(CompanyProfileService.class);
        checks = mock(DocumentCheckService.class);
        lc = new LetterOfCredit();
        lc.setReference("TEST-LC-1"); lc.setCurrency("EUR"); lc.setAmount(new BigDecimal("125000")); lc.setApplicant("Buyer LLC"); lc.setIssuingBank("Testbank Dubai");
        lc.setAvailableWith("ANY BANK BY NEGOTIATION"); lc.setAdvisingBank("Musterbank AG\nFrankfurt"); lc.setExpiryDate(LocalDate.of(2026, 11, 30)); lc.setLatestShipmentDate(LocalDate.of(2026, 10, 10));
        lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS", "PACKING LIST IN 2 COPIES", "FULL SET 3/3 CLEAN ON BOARD OCEAN BILLS OF LADING"));
        lc.setCompanyId(1);
        lc.setRawMessage(":20:TEST-LC-1\n:46A:+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST IN 2 COPIES\n+FULL SET 3/3 CLEAN ON BOARD OCEAN BILLS OF LADING");
        var company = new CompanyProfile(); company.setLegalName("Seller GmbH"); company.setCity("Stuttgart"); company.setBankName("Testbank AG"); company.setIban("DE00 1"); company.setBic("TESTDEFF");
        when(companies.profileForLetterhead(any(), any())).thenReturn(company);
        when(lcs.findById(lcId)).thenReturn(Optional.of(lc));
        when(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)).thenAnswer(call -> stored);
        when(checks.precheck(lcId)).thenReturn(new ReviewSummary("OK", 0, 0, 0, List.of(), "AUTOMATIC"));
        service = new SubmissionPackageService(lcs, documents, new DocxTemplateService(mock(DocumentTemplateService.class), companies), checks);
        // given in a deliberately wrong order, plus documents that are never presented
        for (var d : List.of(
            document(DocumentType.CERTIFICATE_OF_ORIGIN, "coo.docx", "COO-1", LocalDate.of(2026, 10, 4)),
            document(DocumentType.BILL_OF_LADING, "bl.pdf", "BL-77", LocalDate.of(2026, 10, 5)),
            document(DocumentType.PACKING_LIST, "pl.docx", "PL-1", LocalDate.of(2026, 10, 5)),
            document(DocumentType.COMMERCIAL_INVOICE, "inv.docx", "INV-1", LocalDate.of(2026, 10, 5)),
            document(DocumentType.COMMERCIAL_INVOICE, "inv-zweit.docx", "INV-2", LocalDate.of(2026, 10, 6)),
            document(DocumentType.SWIFT_MT700, "lc.txt", null, null),
            document(DocumentType.ADVISING_LETTER, "avis.pdf", null, null)))
            org.springframework.test.util.ReflectionTestUtils.setField(d, "id", UUID.randomUUID());
    }

    private UUID id(String filename) { return stored.stream().filter(d -> filename.equals(d.getOriginalFilename())).findFirst().orElseThrow().getId(); }

    @Test void planListsOnlyPresentableDocumentsInPresentationOrderWithTheCountsOfTheCredit() {
        var plan = service.plan(lcId);
        assertThat(plan.documents()).extracting(SubmissionPackageService.Candidate::filename).containsExactly("inv.docx", "inv-zweit.docx", "pl.docx", "bl.pdf", "coo.docx");
        var invoice = plan.documents().get(0);
        assertThat(invoice.included()).isTrue(); assertThat(invoice.originals()).isEqualTo(3); assertThat(invoice.copies()).isZero();
        var second = plan.documents().get(1);
        assertThat(second.included()).isFalse(); assertThat(second.originals()).isZero();
        var packing = plan.documents().get(2);
        assertThat(packing.copies()).isEqualTo(2); assertThat(packing.originals()).isEqualTo(1);
        assertThat(plan.documents().get(3).originals()).isEqualTo(3);
        assertThat(plan.recipient()).isEqualTo("Musterbank AG\nFrankfurt");
    }

    @Test void planWarnsAboutOpenDiscrepanciesOfThePreCheck() {
        when(checks.precheck(lcId)).thenReturn(new ReviewSummary("DISCREPANCY", 2, 0, 0, List.of(
            new CheckResult(CheckResult.Severity.DISCREPANCY, "A", "Betrag zu hoch", null, "inv.docx", null),
            new CheckResult(CheckResult.Severity.DISCREPANCY, "B", "Versand zu spät", null, null, null),
            new CheckResult(CheckResult.Severity.WARNING, "C", "nur Hinweis", null, null, null)), "AUTOMATIC"));
        var plan = service.plan(lcId);
        assertThat(plan.openDiscrepancies()).isEqualTo(2);
        assertThat(plan.warnings()).containsExactly("Betrag zu hoch (inv.docx)", "Versand zu spät");
    }

    @Test void theRecipientSkipsAnyBankAndFallsBackToTheIssuingBank() {
        assertThat(SubmissionPackageService.recipient(lc)).isEqualTo("Musterbank AG\nFrankfurt");
        lc.setAdvisingBank(null);
        assertThat(SubmissionPackageService.recipient(lc)).isEqualTo("Testbank Dubai");
        lc.setAvailableWith("Hausbank AG");
        assertThat(SubmissionPackageService.recipient(lc)).isEqualTo("Hausbank AG");
    }

    @Test void copiesAreReadFromTheRequiredDocumentsOnlyWhenTheyAgree() {
        assertThat(SubmissionPackageService.requiredCopies(lc)).containsEntry(DocumentType.PACKING_LIST, 2).doesNotContainKey(DocumentType.COMMERCIAL_INVOICE);
        lc.setRequiredDocuments(List.of("PACKING LIST IN 2 COPIES", "PACKING LIST IN THREE COPIES"));
        assertThat(SubmissionPackageService.requiredCopies(lc)).doesNotContainKey(DocumentType.PACKING_LIST);
    }

    @Test void thePackageHoldsTheLetterAndTheNumberedDocumentsInOrder() throws Exception {
        var request = new SubmissionPackageRequest(LocalDate.of(2026, 10, 19), "Musterbank AG\nFrankfurt", "HB-4711", "Please credit on due date.", List.of(
            new SubmissionPackageRequest.Entry(id("coo.docx"), 1, 0), new SubmissionPackageRequest.Entry(id("inv.docx"), 3, 0),
            new SubmissionPackageRequest.Entry(id("bl.pdf"), 3, 2), new SubmissionPackageRequest.Entry(id("pl.docx"), 1, 2)));
        var result = service.build(lcId, request);

        assertThat(result.filename()).isEqualTo("Einreichung-TEST-LC-1-2026-10-19.zip");
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(result.content()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) entries.put(entry.getName(), zip.readAllBytes());
        }
        assertThat(entries.keySet()).containsExactly("00-Einreichungsschreiben.docx", "01-inv.docx", "02-pl.docx", "03-bl.pdf", "04-coo.docx");
        assertThat(new String(entries.get("03-bl.pdf"))).isEqualTo("content of bl.pdf");
        try (var letter = new XWPFDocument(new ByteArrayInputStream(entries.get("00-Einreichungsschreiben.docx")))) {
            String text = letter.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b) + "\n" + letter.getTables().stream().map(t -> t.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertThat(text).contains("Presentation of documents under Documentary Credit No. TEST-LC-1", "Musterbank AG", "HB-4711", "19 Oct 2026", "Testbank Dubai", "EUR 125,000.00", "INV-1", "BL-77", "COO-1", "Please credit on due date.", "Testbank AG");
            assertThat(text).doesNotContain("{{");
            var table = letter.getTables().stream().filter(t -> t.getText().contains("Originals")).findFirst().orElseThrow();
            assertThat(table.getRows()).hasSize(5);
            assertThat(table.getRow(1).getCell(1).getText()).isEqualTo("Commercial Invoice");
            assertThat(table.getRow(1).getCell(3).getText()).isEqualTo("3");
            assertThat(table.getRow(3).getCell(4).getText()).isEqualTo("2");
        }
    }

    @Test void refusesUnknownDuplicateAndEmptyEntries() {
        var other = UUID.randomUUID();
        assertThatThrownBy(() -> service.build(lcId, new SubmissionPackageRequest(LocalDate.of(2026, 10, 19), "Bank", null, null, List.of(new SubmissionPackageRequest.Entry(other, 1, 0)))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht zu dieser Akte");
        var invoice = id("inv.docx");
        assertThatThrownBy(() -> service.build(lcId, new SubmissionPackageRequest(LocalDate.of(2026, 10, 19), "Bank", null, null, List.of(new SubmissionPackageRequest.Entry(invoice, 1, 0), new SubmissionPackageRequest.Entry(invoice, 1, 0)))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("doppelt");
        assertThatThrownBy(() -> service.build(lcId, new SubmissionPackageRequest(LocalDate.of(2026, 10, 19), "Bank", null, null, List.of(new SubmissionPackageRequest.Entry(invoice, 0, 0)))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("weder ein Original noch eine Kopie");
    }
}
