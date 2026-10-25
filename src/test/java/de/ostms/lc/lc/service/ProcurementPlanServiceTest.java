package de.ostms.lc.lc.service;

import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementPlanServiceTest {
    // Friday 2026-11-20 is the latest date of shipment.
    final LocalDate shipment = LocalDate.of(2026, 11, 20);

    @Test void workingDaysSkipWeekends() {
        assertThat(ProcurementPlanService.minusWorkingDays(shipment, 7)).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(ProcurementPlanService.minusWorkingDays(LocalDate.of(2026, 11, 23), 1)).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test void transportDocumentIsDueOnTheShipmentDate() {
        var item = ProcurementPlanService.item("Full set B/L", DocumentType.BILL_OF_LADING, false, shipment, LocalDate.of(2026, 11, 5));
        assertThat(item.dueDate()).isEqualTo(shipment);
        assertThat(item.risk()).isEqualTo(ProcurementPlanService.Risk.OK);
    }

    @Test void leadTimeAndRiskBands() {
        LocalDate origin = LocalDate.of(2026, 11, 11); // due date of the certificate of origin
        assertThat(ProcurementPlanService.item("c", DocumentType.CERTIFICATE_OF_ORIGIN, false, shipment, origin.plusDays(1)).risk()).isEqualTo(ProcurementPlanService.Risk.OVERDUE);
        assertThat(ProcurementPlanService.item("c", DocumentType.CERTIFICATE_OF_ORIGIN, false, shipment, origin.minusDays(2)).risk()).isEqualTo(ProcurementPlanService.Risk.CRITICAL);
        assertThat(ProcurementPlanService.item("c", DocumentType.CERTIFICATE_OF_ORIGIN, false, shipment, origin.minusDays(8)).risk()).isEqualTo(ProcurementPlanService.Risk.SOON);
        assertThat(ProcurementPlanService.item("c", DocumentType.CERTIFICATE_OF_ORIGIN, false, shipment, origin.minusDays(30)).risk()).isEqualTo(ProcurementPlanService.Risk.OK);
    }

    @Test void presentDocumentIsDoneAndMissingShipmentDateIsUnknown() {
        assertThat(ProcurementPlanService.item("p", DocumentType.PACKING_LIST, true, shipment, shipment.plusDays(5)).risk()).isEqualTo(ProcurementPlanService.Risk.DONE);
        var unknown = ProcurementPlanService.item("p", DocumentType.PACKING_LIST, false, null, shipment);
        assertThat(unknown.risk()).isEqualTo(ProcurementPlanService.Risk.UNKNOWN);
        assertThat(unknown.dueDate()).isNull();
    }
    @Test void tenantLeadTimeMovesTheDueDateAndStatusIsCarried() {
        var item = ProcurementPlanService.item("c", DocumentType.CERTIFICATE_OF_ORIGIN, false, shipment, LocalDate.of(2026, 11, 1), 12,
                de.ostms.lc.lc.domain.ProcurementItemStatus.Status.REQUESTED, "bei IHK beantragt");
        assertThat(item.leadWorkingDays()).isEqualTo(12);
        assertThat(item.dueDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(item.status()).isEqualTo(de.ostms.lc.lc.domain.ProcurementItemStatus.Status.REQUESTED);
        assertThat(item.note()).isEqualTo("bei IHK beantragt");
    }

    @Test void transportDocumentsKeepTheShipmentDateWhateverTheLeadTime() {
        var item = ProcurementPlanService.item("b", DocumentType.BILL_OF_LADING, false, shipment, LocalDate.of(2026, 11, 1), 30,
                de.ostms.lc.lc.domain.ProcurementItemStatus.Status.NOT_STARTED, null);
        assertThat(item.dueDate()).isEqualTo(shipment);
    }

    @Test void plannedTypesExcludeTransportAndInternalTypes() {
        var planned = ProcurementPlanService.planned();
        assertThat(planned).contains(DocumentType.CERTIFICATE_OF_ORIGIN, DocumentType.PACKING_LIST)
                .doesNotContain(DocumentType.BILL_OF_LADING, DocumentType.AIR_WAYBILL, DocumentType.SWIFT_MT700);
    }

    @Test void theHintAlsoComesInEnglish() {
        var lead = new ProcurementPlanService.Item("CERTIFICATE OF ORIGIN", de.ostms.lc.document.domain.DocumentType.CERTIFICATE_OF_ORIGIN, "Certificate of Origin", false, java.time.LocalDate.of(2026, 10, 20), 7, 0,
            ProcurementPlanService.Risk.CRITICAL, "Vorlauf 7 Arbeitstage vor dem spätesten Versanddatum.", de.ostms.lc.lc.domain.ProcurementItemStatus.Status.NOT_STARTED, null);
        var transport = new ProcurementPlanService.Item("BILL OF LADING", de.ostms.lc.document.domain.DocumentType.BILL_OF_LADING, "Bill of Lading", false, java.time.LocalDate.of(2026, 10, 29), 0, 9,
            ProcurementPlanService.Risk.SOON, "Ware muss bis zum spätesten Versanddatum verladen sein; dieses Dokument liegt dann vor.", de.ostms.lc.lc.domain.ProcurementItemStatus.Status.NOT_STARTED, null);
        org.assertj.core.api.Assertions.assertThat(lead.hintEn()).isEqualTo("Lead time 7 working days before the latest shipment date.");
        org.assertj.core.api.Assertions.assertThat(transport.hintEn()).startsWith("Goods must be shipped by the latest shipment date");
    }
}
