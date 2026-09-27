package de.corporate.lc.check.service;

import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.check.repository.DocumentCheckDecisionRepository;
import de.corporate.lc.check.domain.DocumentCheckDecision;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentCheckServiceTest {
    @Test void detectsMissingDocumentAndExcessInvoiceAmount() {
        UUID id = UUID.randomUUID();
        LetterOfCredit lc = new LetterOfCredit();
        lc.setAmount(new BigDecimal("1000.00")); lc.setCurrency("EUR");
        lc.setExpiryDate(LocalDate.now().plusDays(30));
        lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE", "PACKING LIST"));
        LcDocument invoice = new LcDocument(); invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
        invoice.setOriginalFilename("invoice.pdf"); invoice.setAmount(new BigDecimal("1200.00"));
        invoice.setCurrency("EUR"); invoice.setDocumentDate(LocalDate.now());
        var lcs = mock(LetterOfCreditRepository.class); var docs = mock(LcDocumentRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc));
        when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));
        var result = service(lcs, docs).check(id);
        assertThat(result.discrepancies()).isEqualTo(2);
        assertThat(result.results()).extracting("code").contains("MISSING_DOCUMENT", "INVOICE_AMOUNT_EXCEEDED");
    }

    @Test void checksGoodsDescriptionAgainstLcAndBetweenDocuments() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-4711");lc.setRawMessage(":20:LC-4711\n:45A:100 INDUSTRIAL PUMPS TYPE PX\n:46A:SIGNED COMMERCIAL INVOICE");
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Description: 100 industrial pumps type PX\nQuantity: 100 pcs\nLC Reference: LC-4711");
        LcDocument packing=document(DocumentType.PACKING_LIST,"packing.pdf","Description: 100 industrial pumps type PX\nQuantity: 100 pieces\nPackages: 10\nNet weight: 500 kg\nGross weight: 550 kg\nLC Reference: LC-4711");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,packing));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("GOODS_DESCRIPTION_OK")).hasSize(2);
        assertThat(result.results()).extracting("code").contains("INVOICE_PACKING_DESCRIPTION_CONSISTENCY","INVOICE_PACKING_QUANTITY_CONSISTENCY","PACKING_WEIGHT_OK","PACKAGES_OK");
    }

    @Test void detectsQuantityAndWeightContradictions() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-99");
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Quantity: 100 pcs");
        LcDocument packing=document(DocumentType.PACKING_LIST,"packing.pdf","Quantity: 90 pcs\nPackages: 5\nNet weight: 600 kg\nGross weight: 550 kg");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,packing));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("INVOICE_PACKING_QUANTITY_CONSISTENCY")||item.code().equals("PACKING_WEIGHT_IMPLAUSIBLE")).allMatch(item->item.severity()==de.corporate.lc.check.api.CheckResult.Severity.DISCREPANCY);
    }

    @Test void flagsManualOriginalCountAndFindsSignatureEvidence() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-12");lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS AND 2 COPIES"));
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Authorized signature: Jane Doe");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("SIGNATURE_REQUIREMENT_EVIDENCED","DOCUMENT_COPIES_MANUAL_REVIEW");
    }

    @Test void acceptedManualDecisionClosesWarning() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-13");lc.setRequiredDocuments(List.of("COMMERCIAL INVOICE IN 2 ORIGINALS"));LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Invoice");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));DocumentCheckDecision decision=new DocumentCheckDecision();decision.setFindingCode("DOCUMENT_COPIES_MANUAL_REVIEW");decision.setDocumentName("invoice.pdf");decision.setDecision("ACCEPTED");decision.setReviewedBy("checker");when(decisions.findByLcId(id)).thenReturn(List.of(decision));
        var result=new DocumentCheckService(lcs,docs,decisions).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("DOCUMENT_COPIES_MANUAL_REVIEW")).allMatch(item->item.severity()==de.corporate.lc.check.api.CheckResult.Severity.OK&&"ACCEPTED".equals(item.reviewDecision()));
    }

    @Test void exposesEffectiveAdditionalConditionsForManualReview() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-47");lc.setRawMessage(":20:LC-47\n:47A:OLD CONDITION");lc.getAdditionalFields().put("47A - gültige Zusatzbedingungen","INSPECTION CERTIFICATE REQUIRED");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of());
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("ADDITIONAL_CONDITIONS_REVIEW")).singleElement().satisfies(item->{assertThat(item.lcCondition()).contains("INSPECTION CERTIFICATE REQUIRED");assertThat(item.lcCondition()).doesNotContain("OLD CONDITION");});
    }

    @Test void checksBillOfLadingShipmentDateAndClauses() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-BL-1");lc.setLatestShipmentDate(LocalDate.of(2026,9,20));lc.setRequiredDocuments(List.of("FULL SET CLEAN ON BOARD BILL OF LADING"));LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl.pdf","LC Reference: LC-BL-1\nClean shipped on board\nFull set");bill.setExtractedReference("LC-BL-1");bill.setDocumentDate(LocalDate.of(2026,9,21));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(bill));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("BILL_OF_LADING_REFERENCE_OK","SHIPMENT_DATE_EXCEEDED","CLEAN_ON_BOARD_EVIDENCED","FULL_SET_BILL_OF_LADING_EVIDENCED");
    }

    @Test void checksCertificateOfOriginReferenceGoodsAndIssuer() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-COO-1");lc.setBeneficiary("Exporter GmbH");lc.setRawMessage(":20:LC-COO-1\n:45A:INDUSTRIAL PUMPS TYPE PX");lc.setRequiredDocuments(List.of("CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE"));LcDocument certificate=document(DocumentType.CERTIFICATE_OF_ORIGIN,"origin.pdf","LC Reference: LC-COO-1\nExporter GmbH\nDescription: Industrial pumps type PX\nChamber of Commerce");certificate.setExtractedReference("LC-COO-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(certificate));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("ORIGIN_CERTIFICATE_REFERENCE_OK","ORIGIN_CERTIFICATE_GOODS_OK","CHAMBER_ISSUER_EVIDENCED");
    }

    @Test void detectsInsufficientInsuranceCoverage() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INS-1");lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of("INSURANCE CERTIFICATE FOR 110 PERCENT OF LC VALUE"));LcDocument insurance=document(DocumentType.INSURANCE_CERTIFICATE,"insurance.pdf","LC Reference: LC-INS-1\nSum insured EUR 1050");insurance.setExtractedReference("LC-INS-1");insurance.setAmount(new BigDecimal("1050"));insurance.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(insurance));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INSURANCE_REFERENCE_OK","INSURANCE_COVERAGE_INSUFFICIENT");
    }

    @Test void detectsExceededPresentationPeriodFromField48() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-48");lc.setRawMessage(":20:LC-48\n:48:21 DAYS AFTER DATE OF SHIPMENT");lc.setExpiryDate(LocalDate.now().plusDays(30));LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl.pdf","LC Reference: LC-48");bill.setExtractedReference("LC-48");bill.setDocumentDate(LocalDate.now().minusDays(30));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(bill));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("PRESENTATION_PERIOD_EXCEEDED","PRESENTATION_BEFORE_EXPIRY_OK");
    }

    @Test void reportsContradictingPackingListDescription() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-4711");lc.setRawMessage(":20:LC-4711\n:45A:100 INDUSTRIAL PUMPS TYPE PX");
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Description: 100 industrial pumps type PX");
        LcDocument packing=document(DocumentType.PACKING_LIST,"packing.pdf","Description: 50 wooden chairs");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,packing));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("GOODS_DESCRIPTION_MISMATCH")||item.code().equals("INVOICE_PACKING_DESCRIPTION_CONSISTENCY")).allMatch(item->item.severity()==de.corporate.lc.check.api.CheckResult.Severity.DISCREPANCY);
    }

    private LcDocument document(DocumentType type,String name,String text){LcDocument document=new LcDocument();document.setDocumentType(type);document.setOriginalFilename(name);document.setExtractionStatus("GENERATED");document.setExtractedText(text);document.setDocumentDate(LocalDate.now());return document;}
    private DocumentCheckService service(LetterOfCreditRepository lcs,LcDocumentRepository docs){DocumentCheckDecisionRepository decisions=mock(DocumentCheckDecisionRepository.class);when(decisions.findByLcId(any())).thenReturn(List.of());return new DocumentCheckService(lcs,docs,decisions);}
}
