package de.ostms.lc.check.service;

import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.check.repository.DocumentCheckDecisionRepository;
import de.ostms.lc.check.domain.DocumentCheckDecision;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.Amendment;
import de.ostms.lc.lc.repository.AmendmentRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentCheckServiceTest {
    @Test void precheckShowsAutomaticFindingsWithoutReadingOrWritingHumanDecisions(){
        UUID id=UUID.randomUUID();var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);
        var lc=new LetterOfCredit();lc.setRequiredDocuments(List.of("COMMERCIAL INVOICE"));
        when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of());
        var result=new DocumentCheckService(lcs,docs,decisions).precheck(id);
        assertThat(result.results()).anyMatch(r->r.code().equals("MISSING_DOCUMENT"));
        assertThat(result.results()).allMatch(r->r.reviewDecision()==null);
        assertThat(result.mode()).isEqualTo("PRECHECK");assertThat(result.finalReview()).isFalse();assertThat(result.reviewedFindings()).isZero();
        verifyNoInteractions(decisions);verify(lcs,never()).save(any());verify(docs,never()).save(any());
    }
    @Test void requiresAndStoresAcceptanceReason(){
        UUID id=UUID.randomUUID();var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(new LetterOfCredit()));
        var service=new DocumentCheckService(lcs,docs,decisions);
        for(String comment:Arrays.asList(null,"","  ")){
            var request=new de.ostms.lc.check.api.CheckDecisionRequest("TEST","invoice.pdf","ACCEPTED",comment);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.decide(id,request,"checker")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("begründen");
        }
        verify(decisions,never()).save(any());
        var invoice=new LcDocument();invoice.setOriginalFilename("invoice.pdf");invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
        when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));when(decisions.findByLcId(id)).thenReturn(List.of());
        var finding=service.check(id).results().stream().filter(r->r.code().equals("DATE_NOT_CAPTURED")).findFirst().orElseThrow();
        service.decide(id,new de.ostms.lc.check.api.CheckDecisionRequest("DATE_NOT_CAPTURED","invoice.pdf","ACCEPTED"," Original geprüft ",finding.reviewFingerprint()),"checker");
        verify(decisions).save(argThat(d->"Original geprüft".equals(d.getComment())&&"checker".equals(d.getReviewedBy())&&d.getReviewedAt()!=null));
    }

    @Test void identifiesEffectiveLcVersionIncludingAmendments(){
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-VERSION");lc.setCurrency("EUR");lc.setAmount(new BigDecimal("1250"));lc.setExpiryDate(LocalDate.of(2026,12,31));lc.setRequiredDocuments(List.of());
        Amendment amendment=new Amendment();amendment.setAmendmentNumber("2");amendment.setAmendmentDate(LocalDate.of(2026,9,28));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);var mappings=mock(de.ostms.lc.check.repository.LcRequirementMappingRepository.class);var amendments=mock(AmendmentRepository.class);
        when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of());when(decisions.findByLcId(id)).thenReturn(List.of());when(amendments.findByLetterOfCreditIdOrderByImportedAtDesc(null)).thenReturn(List.of(amendment));
        var result=new DocumentCheckService(lcs,docs,decisions,mappings,amendments).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("LC_EFFECTIVE_VERSION")).singleElement().satisfies(item->{assertThat(item.message()).contains("MT707");assertThat(item.lcCondition()).contains("2","2026-09-28");assertThat(item.documentEvidence()).contains("EUR 1250","2026-12-31");});
    }
    @Test void invalidatesDecisionsWithoutPrimitiveDeleteResult() {
        UUID id=UUID.randomUUID();var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);when(decisions.findByLcId(id)).thenReturn(List.of(new DocumentCheckDecision(),new DocumentCheckDecision()));
        long count=new DocumentCheckService(lcs,docs,decisions).invalidateDecisions(id);
        assertThat(count).isEqualTo(2);verify(decisions).saveAll(argThat(rows->java.util.stream.StreamSupport.stream(rows.spliterator(),false).allMatch(d->d.getInvalidatedAt()!=null)));verify(decisions,never()).deleteAllByLcId(id);
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
        assertThat(result.results()).filteredOn(item->item.code().equals("INVOICE_PACKING_QUANTITY_CONSISTENCY")||item.code().equals("PACKING_WEIGHT_IMPLAUSIBLE")).allMatch(item->item.severity()==de.ostms.lc.check.api.CheckResult.Severity.DISCREPANCY);
    }

    @Test void flagsManualOriginalCountAndFindsSignatureEvidence() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-12");lc.setRequiredDocuments(List.of("SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS AND 2 COPIES"));
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Authorized signature: Jane Doe");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("SIGNATURE_REQUIREMENT_EVIDENCED","DOCUMENT_ORIGINAL_COUNT","DOCUMENT_COPY_COUNT");
    }

    @Test void acceptedManualDecisionClosesWarning() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-13");lc.setRequiredDocuments(List.of("COMMERCIAL INVOICE IN 2 ORIGINALS"));LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Invoice");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);var decisions=mock(DocumentCheckDecisionRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));DocumentCheckDecision decision=new DocumentCheckDecision();decision.setFindingCode("DOCUMENT_ORIGINAL_COUNT");decision.setDocumentName("invoice.pdf");decision.setDecision("ACCEPTED");decision.setReviewedBy("checker");when(decisions.findByLcId(id)).thenReturn(List.of(decision));
        when(decisions.findByLcId(id)).thenReturn(List.of());var checkService=new DocumentCheckService(lcs,docs,decisions);
        decision.setFindingFingerprint(checkService.check(id).results().stream().filter(item->item.code().equals("DOCUMENT_ORIGINAL_COUNT")).findFirst().orElseThrow().reviewFingerprint());
        when(decisions.findByLcId(id)).thenReturn(List.of(decision));var result=checkService.check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("DOCUMENT_ORIGINAL_COUNT")).allMatch(item->item.severity()==de.ostms.lc.check.api.CheckResult.Severity.OK&&"ACCEPTED".equals(item.reviewDecision()));
        assertThat(result.results()).filteredOn(item->item.code().equals("DOCUMENT_ORIGINAL_COUNT")).isNotEmpty().allMatch(item->item.automaticSeverity()==de.ostms.lc.check.api.CheckResult.Severity.WARNING);
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

    @Test void checksBillOfLadingConsignmentEndorsementFreightAndNotifyParty() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-BL-2");lc.setApplicant("Importer AG");lc.setRequiredDocuments(List.of("FULL SET BILL OF LADING CONSIGNED TO ORDER, BLANK ENDORSED, FREIGHT PREPAID, NOTIFY APPLICANT"));LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl-terms.pdf","LC Reference: LC-BL-2\nConsignee: To order of issuing bank\nBlank endorsement\nFreight charges prepaid\nNotify party: Importer AG");bill.setExtractedReference("LC-BL-2");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(bill));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("BILL_OF_LADING_TO_ORDER_EVIDENCED","BILL_OF_LADING_BLANK_ENDORSEMENT_EVIDENCED","BILL_OF_LADING_FREIGHT_PREPAID_EVIDENCED","BILL_OF_LADING_NOTIFY_APPLICANT_EVIDENCED");
    }

    @Test void checksCertificateOfOriginReferenceGoodsAndIssuer() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-COO-1");lc.setBeneficiary("Exporter GmbH");lc.setRawMessage(":20:LC-COO-1\n:45A:INDUSTRIAL PUMPS TYPE PX");lc.setRequiredDocuments(List.of("CERTIFICATE OF ORIGIN ISSUED BY CHAMBER OF COMMERCE"));LcDocument certificate=document(DocumentType.CERTIFICATE_OF_ORIGIN,"origin.pdf","LC Reference: LC-COO-1\nExporter GmbH\nDescription: Industrial pumps type PX\nChamber of Commerce");certificate.setExtractedReference("LC-COO-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(certificate));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("ORIGIN_CERTIFICATE_REFERENCE_OK","ORIGIN_CERTIFICATE_GOODS_OK","CHAMBER_ISSUER_EVIDENCED");
    }

    @Test void checksRequiredOriginCountryAndNamedIssuer() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-COO-2");lc.setRequiredDocuments(List.of("CERTIFICATE OF ORIGIN SHOWING COUNTRY OF ORIGIN GERMANY, ISSUED BY SGS"));LcDocument certificate=document(DocumentType.CERTIFICATE_OF_ORIGIN,"origin-country.pdf","LC Reference: LC-COO-2\nCountry of origin: Germany\nIssued by SGS");certificate.setExtractedReference("LC-COO-2");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(certificate));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("ORIGIN_COUNTRY_OK","ORIGIN_CERTIFICATE_ISSUER_EVIDENCED");
    }

    @Test void detectsInsufficientInsuranceCoverage() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INS-1");lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of("INSURANCE CERTIFICATE FOR 110 PERCENT OF LC VALUE"));LcDocument insurance=document(DocumentType.INSURANCE_CERTIFICATE,"insurance.pdf","LC Reference: LC-INS-1\nSum insured EUR 1050");insurance.setExtractedReference("LC-INS-1");insurance.setAmount(new BigDecimal("1050"));insurance.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(insurance));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INSURANCE_REFERENCE_OK","INSURANCE_COVERAGE_INSUFFICIENT");
    }

    @Test void calculatesInsuranceCoverageFromInvoiceValueWhenRequired() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INS-INV");lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of("INSURANCE CERTIFICATE FOR 110 PERCENT OF INVOICE VALUE"));LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Amount: EUR 800");invoice.setAmount(new BigDecimal("800"));invoice.setCurrency("EUR");LcDocument insurance=document(DocumentType.INSURANCE_CERTIFICATE,"insurance.pdf","LC Reference: LC-INS-INV\nSum insured EUR 880");insurance.setExtractedReference("LC-INS-INV");insurance.setAmount(new BigDecimal("880"));insurance.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,insurance));var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("INSURANCE_COVERAGE_OK")).singleElement().satisfies(item->assertThat(item.lcCondition()).contains("Rechnungssumme: EUR 800","Mindestdeckung: EUR 880"));
    }

    @Test void checksRequiredInsuranceClauses() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INS-CLAUSE");lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRequiredDocuments(List.of("INSURANCE CERTIFICATE FOR 110 PERCENT OF LC VALUE COVERING ALL RISKS, INSTITUTE CARGO CLAUSES A, WAREHOUSE TO WAREHOUSE, CLAIMS PAYABLE IN GERMANY"));LcDocument insurance=document(DocumentType.INSURANCE_CERTIFICATE,"insurance-clauses.pdf","LC Reference: LC-INS-CLAUSE\nSum insured EUR 1100\nAgainst all risks\nInstitute Cargo Clauses A\nWarehouse to warehouse\nClaims payable in Germany");insurance.setExtractedReference("LC-INS-CLAUSE");insurance.setAmount(new BigDecimal("1100"));insurance.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(insurance));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INSURANCE_ALL_RISKS_EVIDENCED","INSURANCE_ICC_A_EVIDENCED","INSURANCE_WAREHOUSE_TO_WAREHOUSE_EVIDENCED","INSURANCE_CLAIMS_PAYABLE_EVIDENCED");
    }

    @Test void flagsInsuranceDocumentIssuedAfterShipment() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INS-DATE");LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl.pdf","LC Reference: LC-INS-DATE");bill.setDocumentDate(LocalDate.of(2026,9,10));bill.setExtractedReference("LC-INS-DATE");LcDocument insurance=document(DocumentType.INSURANCE_CERTIFICATE,"insurance.pdf","LC Reference: LC-INS-DATE");insurance.setDocumentDate(LocalDate.of(2026,9,12));insurance.setExtractedReference("LC-INS-DATE");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(bill,insurance));var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("INSURANCE_DATE_AFTER_SHIPMENT_REVIEW")).singleElement().satisfies(item->{assertThat(item.severity()).isEqualTo(de.ostms.lc.check.api.CheckResult.Severity.WARNING);assertThat(item.documentEvidence()).contains("2026-09-12","2026-09-10","bl.pdf");});
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

    @Test void checksAirWaybillConsigneeNotifyPartyAndOriginal() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-AWB-2");lc.setIssuingBank("Example Bank AG");lc.setApplicant("Importer GmbH");lc.setRequiredDocuments(List.of("AIR WAYBILL CONSIGNED TO ISSUING BANK, NOTIFY APPLICANT, ORIGINAL 3 FOR SHIPPER, FREIGHT COLLECT"));LcDocument awb=document(DocumentType.AIR_WAYBILL,"awb-parties.pdf","LC Reference: LC-AWB-2\nConsignee: Example Bank AG\nNotify party: Importer GmbH\nOriginal No. 3 for shipper\nFreight charges collect");awb.setExtractedReference("LC-AWB-2");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(awb));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("AWB_CONSIGNEE_ISSUING_BANK_EVIDENCED","AWB_NOTIFY_APPLICANT_EVIDENCED","AWB_ORIGINAL_FOR_SHIPPER_EVIDENCED","AWB_FREIGHT_COLLECT_EVIDENCED");
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

    @Test void checksInspectionStatementsAndPreShipmentDate() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-INSP-2");lc.setRequiredDocuments(List.of("PRE-SHIPMENT INSPECTION CERTIFICATE CERTIFYING QUALITY, QUANTITY AND PACKING"));LcDocument inspection=document(DocumentType.INSPECTION_CERTIFICATE,"inspection-details.pdf","LC Reference: LC-INSP-2\nPre-shipment inspection\nQuality and quantity satisfactory\nPacking inspected");inspection.setExtractedReference("LC-INSP-2");inspection.setDocumentDate(LocalDate.of(2026,9,9));LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl.pdf","LC Reference: LC-INSP-2");bill.setExtractedReference("LC-INSP-2");bill.setDocumentDate(LocalDate.of(2026,9,10));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(inspection,bill));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INSPECTION_QUALITY_EVIDENCED","INSPECTION_QUANTITY_EVIDENCED","INSPECTION_PACKING_EVIDENCED","INSPECTION_PRE_SHIPMENT_EVIDENCED","INSPECTION_DATE_OK");
    }

    @Test void checksQualityCertificateGoodsIssuerAndResult() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-QA-1");lc.setRawMessage(":20:LC-QA-1\n:45A:INDUSTRIAL PUMPS TYPE PX");lc.setRequiredDocuments(List.of("CERTIFICATE OF ANALYSIS ISSUED BY SGS"));LcDocument quality=document(DocumentType.QUALITY_CERTIFICATE,"analysis.pdf","LC Reference: LC-QA-1\nIssued by SGS\nDescription: Industrial pumps type PX\nTest passed - conforms to specification");quality.setExtractedReference("LC-QA-1");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(quality));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("QUALITY_CERTIFICATE_REFERENCE_OK","QUALITY_CERTIFICATE_GOODS_OK","QUALITY_CERTIFICATE_ISSUER_EVIDENCED","QUALITY_RESULT_EVIDENCED");
    }

    @Test void checksCourierRecipientTrackingAndDeadline() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-COUR-1");lc.setApplicant("Importer AG");lc.setRequiredDocuments(List.of("COURIER RECEIPT EVIDENCING DOCUMENTS SENT TO APPLICANT WITHIN 3 DAYS AFTER SHIPMENT"));LcDocument bill=document(DocumentType.BILL_OF_LADING,"bl.pdf","LC Reference: LC-COUR-1");bill.setExtractedReference("LC-COUR-1");bill.setDocumentDate(LocalDate.of(2026,9,10));LcDocument courier=document(DocumentType.COURIER_RECEIPT,"dhl.pdf","Recipient: Importer AG\nTracking No: DHL123456");courier.setDocumentDate(LocalDate.of(2026,9,12));
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(courier,bill));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("COURIER_RECIPIENT_APPLICANT_OK","COURIER_TRACKING_OK","COURIER_DEADLINE_OK");
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

    @Test void checksCmrSenderConsigneeAndFreightTerm() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-CMR-2");lc.setBeneficiary("Exporter GmbH");lc.setApplicant("Importer AG");lc.setRequiredDocuments(List.of("SIGNED CMR SHOWING SENDER BENEFICIARY, CONSIGNEE APPLICANT AND FREIGHT PREPAID"));LcDocument cmr=document(DocumentType.ROAD_CONSIGNMENT_NOTE,"cmr-parties.pdf","LC Reference: LC-CMR-2\nSender: Exporter GmbH\nConsignee: Importer AG\nCarriage paid\nCarrier signature");cmr.setExtractedReference("LC-CMR-2");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(cmr));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("CMR_SENDER_BENEFICIARY_EVIDENCED","CMR_CONSIGNEE_APPLICANT_EVIDENCED","CMR_FREIGHT_PREPAID_EVIDENCED");
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
        assertThat(result.results()).filteredOn(item->item.code().equals("PARTIAL_SHIPMENT_MANUAL_REVIEW")).singleElement().satisfies(item->{assertThat(item.severity()).isEqualTo(de.ostms.lc.check.api.CheckResult.Severity.WARNING);assertThat(item.documentEvidence()).contains("bl-1.pdf","bl-2.pdf");});
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

    @Test void rejectsCumulativeInvoiceAmountAboveCreditLimit() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");LcDocument first=document(DocumentType.COMMERCIAL_INVOICE,"invoice-1.pdf","Amount: EUR 600");first.setAmount(new BigDecimal("600"));first.setCurrency("EUR");LcDocument second=document(DocumentType.COMMERCIAL_INVOICE,"invoice-2.pdf","Amount: EUR 500");second.setAmount(new BigDecimal("500"));second.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(first,second));var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("CUMULATIVE_INVOICE_AMOUNT_EXCEEDED")).singleElement().satisfies(item->{assertThat(item.severity()).isEqualTo(de.ostms.lc.check.api.CheckResult.Severity.DISCREPANCY);assertThat(item.documentEvidence()).contains("EUR 1100","invoice-1.pdf","invoice-2.pdf");});
    }

    @Test void acceptsCumulativeInvoicesWithinAmountTolerance() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");lc.setRawMessage(":39A:10/05");LcDocument first=document(DocumentType.COMMERCIAL_INVOICE,"invoice-a.pdf","Amount: EUR 600");first.setAmount(new BigDecimal("600"));first.setCurrency("EUR");LcDocument second=document(DocumentType.COMMERCIAL_INVOICE,"invoice-b.pdf","Amount: EUR 500");second.setAmount(new BigDecimal("500"));second.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(first,second));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("CUMULATIVE_INVOICE_AMOUNT_OK");
    }

    @Test void detectsInvoiceAndDraftAmountMismatch() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Amount: EUR 900");invoice.setAmount(new BigDecimal("900"));invoice.setCurrency("EUR");LcDocument draft=document(DocumentType.BILL_OF_EXCHANGE,"draft.pdf","Amount: EUR 850");draft.setAmount(new BigDecimal("850"));draft.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,draft));var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("INVOICE_DRAFT_AMOUNT_MISMATCH")).singleElement().satisfies(item->{assertThat(item.severity()).isEqualTo(de.ostms.lc.check.api.CheckResult.Severity.DISCREPANCY);assertThat(item.documentEvidence()).contains("Rechnungen: EUR 900","Wechsel: EUR 850");});
    }

    @Test void acceptsMatchingInvoiceAndDraftTotals() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setAmount(new BigDecimal("1000"));lc.setCurrency("EUR");LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Amount: EUR 900");invoice.setAmount(new BigDecimal("900"));invoice.setCurrency("EUR");LcDocument draft=document(DocumentType.BILL_OF_EXCHANGE,"draft.pdf","Amount: EUR 900");draft.setAmount(new BigDecimal("900"));draft.setCurrency("EUR");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,draft));var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("INVOICE_DRAFT_AMOUNT_OK");
    }

    @Test void reportsContradictingPackingListDescription() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-4711");lc.setRawMessage(":20:LC-4711\n:45A:100 INDUSTRIAL PUMPS TYPE PX");
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Description: 100 industrial pumps type PX");
        LcDocument packing=document(DocumentType.PACKING_LIST,"packing.pdf","Description: 50 wooden chairs");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,packing));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("GOODS_DESCRIPTION_MISMATCH")||item.code().equals("INVOICE_PACKING_DESCRIPTION_CONSISTENCY")).allMatch(item->item.severity()==de.ostms.lc.check.api.CheckResult.Severity.DISCREPANCY);
    }

    @Test void detectsConflictingCountriesOfOriginAcrossDocuments() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-ORIGIN-1");lc.setRequiredDocuments(List.of("CERTIFICATE OF ORIGIN SHOWING ORIGIN GERMANY"));
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Country of origin: Germany");LcDocument certificate=document(DocumentType.CERTIFICATE_OF_ORIGIN,"origin.pdf","Country of origin: France");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice,certificate));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).extracting("code").contains("COUNTRY_OF_ORIGIN_OK","COUNTRY_OF_ORIGIN_MISMATCH","COUNTRY_OF_ORIGIN_DOCUMENT_CONFLICT");
    }

    @Test void comparesInvoiceIncotermWithEffectiveLcTerms() {
        UUID id=UUID.randomUUID();LetterOfCredit lc=new LetterOfCredit();lc.setReference("LC-TERM-1");lc.setRawMessage(":20:LC-TERM-1\n:45A:INDUSTRIAL PUMPS CIF HAMBURG");
        LcDocument invoice=document(DocumentType.COMMERCIAL_INVOICE,"invoice.pdf","Description: Industrial pumps\nDelivery term: FOB Alexandria");
        var lcs=mock(LetterOfCreditRepository.class);var docs=mock(LcDocumentRepository.class);when(lcs.findById(id)).thenReturn(Optional.of(lc));when(docs.findByLetterOfCreditIdOrderByUploadedAtDesc(id)).thenReturn(List.of(invoice));
        var result=service(lcs,docs).check(id);
        assertThat(result.results()).filteredOn(item->item.code().equals("INCOTERM_MISMATCH")).singleElement().satisfies(item->{assertThat(item.severity()).isEqualTo(de.ostms.lc.check.api.CheckResult.Severity.DISCREPANCY);assertThat(item.lcCondition()).contains("CIF");assertThat(item.documentEvidence()).contains("FOB");});
    }

    private LcDocument document(DocumentType type,String name,String text){LcDocument document=new LcDocument();document.setDocumentType(type);document.setOriginalFilename(name);document.setExtractionStatus("GENERATED");document.setExtractedText(text);document.setDocumentDate(LocalDate.now());return document;}
    private DocumentCheckService service(LetterOfCreditRepository lcs,LcDocumentRepository docs){DocumentCheckDecisionRepository decisions=mock(DocumentCheckDecisionRepository.class);when(decisions.findByLcId(any())).thenReturn(List.of());return new DocumentCheckService(lcs,docs,decisions);}
}
