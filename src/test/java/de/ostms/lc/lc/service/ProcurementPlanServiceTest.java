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
}
