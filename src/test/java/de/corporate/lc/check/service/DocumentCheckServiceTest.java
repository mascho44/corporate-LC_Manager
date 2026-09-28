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
    @Test void invalidatesDecisionsWithoutPrimitiveDeleteResult() {
        UUID id=UUID.randomUUID();var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);when(decisions.findByLcId(id)).thenReturn(List.of(new DocumentCheckDecision(),new DocumentCheckDecision()));
        long count=new DocumentCheckService(lcs,docs,decisions).invalidateDecisions(id);
        assertThat(count).isEqualTo(2);verify(decisions).deleteAllByLcId(id);
    }

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

    @Test void checksAirWaybillReferenceShipmentDateAndClauses() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-AWB-1");lc.setLatestShipmentDate(LocalDate.of(2026,9,20));lc.setRequiredDocuments(List.of("AIR WAYBILL SHOWING FREIGHT PREPAID, AIRPORT OF DEPARTURE AND AIRPORT OF DESTINATION"));LcDocument awb=document(DocumentType.AIR_WAYBILL,"awb.pdf","LC Reference: LC-AWB-1\nFreight charges prepaid\nAirport of departure: Frankfurt\nAirport of destination: Cairo");awb.setExtractedReference("LC-AWB-1");awb.setDocumentDate(LocalDate.of(2026,9,19));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(awb));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("AIR_WAYBILL_REFERENCE_OK","AIR_SHIPMENT_DATE_OK","AWB_FREIGHT_PREPAID_EVIDENCED","AWB_DEPARTURE_AIRPORT_EVIDENCED","AWB_DESTINATION_AIRPORT_EVIDENCED");
    }

    @Test void usesAirWaybillForPresentationPeriod() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-AWB-48");lc.setRawMessage(":20:LC-AWB-48\n:48:21 DAYS AFTER DATE OF SHIPMENT");lc.setExpiryDate(LocalDate.now().plusDays(30));LcDocument awb=document(DocumentType.AIR_WAYBILL,"awb.pdf","LC Reference: LC-AWB-48");awb.setExtractedReference("LC-AWB-48");awb.setDocumentDate(LocalDate.now().minusDays(30));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(awb));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("PRESENTATION_PERIOD_EXCEEDED");
    }

    @Test void checksInspectionCertificateReferenceGoodsAndIssuer() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INSP-1");lc.setBeneficiary("Exporter GmbH");lc.setRawMessage(":20:LC-INSP-1\n:45A:INDUSTRIAL PUMPS TYPE PX");lc.setRequiredDocuments(List.of("INSPECTION CERTIFICATE ISSUED BY SGS"));LcDocument certificate=document(DocumentType.INSPECTION_CERTIFICATE,"inspection.pdf","LC Reference: LC-INSP-1\nExporter GmbH\nDescription: Industrial pumps type PX\nIssued by SGS");certificate.setExtractedReference("LC-INSP-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(certificate));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INSPECTION_REFERENCE_OK","INSPECTION_BENEFICIARY_OK","INSPECTION_GOODS_OK","INSPECTION_ISSUER_EVIDENCED");
    }

    @Test void checksBillOfExchangeAmountCurrencyDrawerAndTenor() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-DRAFT-1");lc.setBeneficiary("Exporter GmbH");lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of("BILL OF EXCHANGE AT SIGHT DRAWN BY BENEFICIARY"));LcDocument draft=document(DocumentType.BILL_OF_EXCHANGE,"draft.pdf","LC Reference: LC-DRAFT-1\nExporter GmbH\nAt sight\nAmount EUR 900");draft.setExtractedReference("LC-DRAFT-1");draft.setExtractedAmount(new BigDecimal("900"));draft.setExtractedCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(draft));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("DRAFT_REFERENCE_OK","DRAFT_DRAWER_OK","DRAFT_AMOUNT_OK","DRAFT_TENOR_OK");
    }

    @Test void checksCmrReferenceShipmentDateSignatureAndPlaces() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-CMR-1");lc.setLatestShipmentDate(LocalDate.of(2026,9,20));lc.setRequiredDocuments(List.of("SIGNED CMR ROAD CONSIGNMENT NOTE SHOWING PLACE OF TAKING OVER AND PLACE OF DELIVERY"));LcDocument cmr=document(DocumentType.ROAD_CONSIGNMENT_NOTE,"cmr.pdf","LC Reference: LC-CMR-1\nCarrier signature\nPlace of taking over: Stuttgart\nPlace of delivery: Paris");cmr.setExtractedReference("LC-CMR-1");cmr.setDocumentDate(LocalDate.of(2026,9,19));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(cmr));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("CMR_REFERENCE_OK","CMR_SHIPMENT_DATE_OK","CMR_CARRIER_SIGNATURE_EVIDENCED","CMR_PLACE_OF_TAKING_EVIDENCED","CMR_PLACE_OF_DELIVERY_EVIDENCED");
    }

    @Test void checksBeneficiaryCertificateReferenceIssuerAndStatement() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-BC-1");lc.setBeneficiary("Exporter GmbH");lc.setRequiredDocuments(List.of("BENEFICIARY'S CERTIFICATE CERTIFYING THAT ONE SET OF NON-NEGOTIABLE DOCUMENTS WAS SENT TO APPLICANT"));LcDocument certificate=document(DocumentType.BENEFICIARY_CERTIFICATE,"beneficiary-certificate.pdf","LC Reference: LC-BC-1\nExporter GmbH hereby certifies that one set of non-negotiable documents was sent to applicant");certificate.setExtractedReference("LC-BC-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(certificate));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("BENEFICIARY_CERTIFICATE_REFERENCE_OK","BENEFICIARY_CERTIFICATE_ISSUER_OK","BENEFICIARY_STATEMENT_EVIDENCED");
    }

    @Test void checksTransportRouteAndTransshipmentProhibition() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-ROUTE-1");lc.setRawMessage(":20:LC-ROUTE-1\n:43T:NOT ALLOWED\n:44E:HAMBURG PORT\n:44F:ALEXANDRIA PORT");LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl-route.pdf","LC Reference: LC-ROUTE-1\nPort of loading: Hamburg Port\nPort of discharge: Alexandria Port\nTransshipment allowed");bill.setExtractedReference("LC-ROUTE-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(bill));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("TRANSPORT_PORT_OF_LOADING_OK","TRANSPORT_PORT_OF_DISCHARGE_OK","TRANSSHIPMENT_PROHIBITION_VIOLATED");
    }

    @Test void flagsMultipleTransportDocumentsWhenPartialShipmentsAreProhibited() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-PART-1");lc.setRawMessage(":20:LC-PART-1\n:43P:NOT ALLOWED");LcDocument first=document(DocumentType.BILL_OF_LADING,"bl-1.pdf","LC Reference: LC-PART-1");first.setExtractedReference("LC-PART-1");LcDocument second=document(DocumentType.BILL_OF_LADING,"bl-2.pdf","LC Reference: LC-PART-1");second.setExtractedReference("LC-PART-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(first,second));var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("PARTIAL_SHIPMENT_MANUAL_REVIEW")).singleElement().satisfies(item->{assertThat(item.severity()).isEqualTo(de.corporate.lc.check.api.CheckResult.Severity.WARNING);assertThat(item.documentEvidence()).contains("bl-1.pdf","bl-2.pdf");});
    }

    @Test void appliesPositiveAmountToleranceFromField39A() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRawMessage(":20:LC-TOL-1\n:32B:EUR1000,00\n:39A:10/05");LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice-tolerance.pdf","Amount: EUR 1050");invoice.setAmount(new BigDecimal("1050"));invoice.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("INVOICE_AMOUNT_OK")).singleElement().satisfies(item->assertThat(item.lcCondition()).contains("10/05","Höchstbetrag: EUR 1100"));
    }

    @Test void rejectsInvoiceAbovePositiveAmountTolerance() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRawMessage(":20:LC-TOL-2\n:39A:10/05");LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice-too-high.pdf","Amount: EUR 1150");invoice.setAmount(new BigDecimal("1150"));invoice.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INVOICE_AMOUNT_EXCEEDED");
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
