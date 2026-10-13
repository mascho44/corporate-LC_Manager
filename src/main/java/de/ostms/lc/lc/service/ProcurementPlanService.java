package de.ostms.lc.lc.service;

import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Procurement plan for the exporter: every document the LC requires, with a target date counted back from the latest date of shipment
 * (the transport document itself is due on that date). Lead times are working-day defaults per document type; holidays are not considered.
 */
@Service
public class ProcurementPlanService {
    public enum Risk { DONE, OVERDUE, CRITICAL, SOON, OK, UNKNOWN }
    public record Item(String requirement, DocumentType documentType, String documentName, boolean present, LocalDate dueDate, int leadWorkingDays, Integer daysLeft, Risk risk, String hint) {}
    public record Plan(LocalDate latestShipment, LocalDate expiry, Integer daysToShipment, Risk shipmentRisk, long open, long overdue, long critical, List<Item> items) {}

    static final int CRITICAL_DAYS = 3, SOON_DAYS = 10;
    private static final Map<DocumentType, Integer> LEAD = Map.ofEntries(
            Map.entry(DocumentType.COMMERCIAL_INVOICE, 2), Map.entry(DocumentType.PACKING_LIST, 3), Map.entry(DocumentType.WEIGHT_LIST, 3),
            Map.entry(DocumentType.CERTIFICATE_OF_ORIGIN, 7), Map.entry(DocumentType.INSPECTION_CERTIFICATE, 10), Map.entry(DocumentType.QUALITY_CERTIFICATE, 10),
            Map.entry(DocumentType.INSURANCE_CERTIFICATE, 3), Map.entry(DocumentType.BENEFICIARY_CERTIFICATE, 2), Map.entry(DocumentType.BILL_OF_EXCHANGE, 2));
    private static final int DEFAULT_LEAD = 5;

    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDocumentRepository documents;
    private final DocumentCheckService checks;

    public ProcurementPlanService(LetterOfCreditRepository lettersOfCredit, LcDocumentRepository documents, DocumentCheckService checks) {
        this.lettersOfCredit = lettersOfCredit; this.documents = documents; this.checks = checks;
    }

    public Plan forLc(UUID lcId) { return forLc(lcId, LocalDate.now()); }

    Plan forLc(UUID lcId, LocalDate today) {
        LetterOfCredit lc = lettersOfCredit.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        var uploaded = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        Set<DocumentType> present = new HashSet<>();
        uploaded.forEach(d -> present.add(d.getDocumentType()));
        LocalDate shipment = lc.getLatestShipmentDate();
        List<Item> items = new ArrayList<>();
        for (String requirement : lc.getRequiredDocuments()) {
            Optional<DocumentType> type = checks.expectedType(lcId, requirement);
            if (type.isEmpty()) { items.add(new Item(requirement, null, null, false, null, 0, null, Risk.UNKNOWN, "Dokumentenart nicht erkannt – bitte Anforderung einem Dokumenttyp zuordnen.")); continue; }
            items.add(item(requirement, type.get(), present.contains(type.get()), shipment, today));
        }
        items.sort(Comparator.comparing((Item i) -> i.risk() == Risk.DONE).thenComparing(Item::dueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        long overdue = items.stream().filter(i -> i.risk() == Risk.OVERDUE).count(), critical = items.stream().filter(i -> i.risk() == Risk.CRITICAL).count();
        long open = items.stream().filter(i -> i.risk() != Risk.DONE).count();
        Integer toShipment = shipment == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(today, shipment);
        boolean transportPresent = present.stream().anyMatch(ProcurementPlanService::isTransport);
        Risk shipmentRisk = shipment == null ? Risk.UNKNOWN : transportPresent ? Risk.DONE : riskFor(toShipment);
        return new Plan(shipment, lc.getExpiryDate(), toShipment, shipmentRisk, open, overdue, critical, List.copyOf(items));
    }

    static Item item(String requirement, DocumentType type, boolean present, LocalDate shipment, LocalDate today) {
        boolean transport = isTransport(type);
        int lead = transport ? 0 : LEAD.getOrDefault(type, DEFAULT_LEAD);
        LocalDate due = shipment == null ? null : minusWorkingDays(shipment, lead);
        Integer left = due == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(today, due);
        Risk risk = present ? Risk.DONE : left == null ? Risk.UNKNOWN : riskFor(left);
        String hint = transport ? "Ware muss bis zum spätesten Versanddatum verladen sein; dieses Dokument liegt dann vor."
                : "Richtwert " + lead + " Arbeitstage Vorlauf vor dem spätesten Versanddatum.";
        return new Item(requirement, type, type.getDisplayName(), present, due, lead, left, risk, hint);
    }

    static Risk riskFor(int daysLeft) { return daysLeft < 0 ? Risk.OVERDUE : daysLeft <= CRITICAL_DAYS ? Risk.CRITICAL : daysLeft <= SOON_DAYS ? Risk.SOON : Risk.OK; }

    static LocalDate minusWorkingDays(LocalDate date, int days) {
        LocalDate d = date; int remaining = days;
        while (remaining > 0) { d = d.minusDays(1); if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) remaining--; }
        return d;
    }

    static boolean isTransport(DocumentType t) {
        return t == DocumentType.BILL_OF_LADING || t == DocumentType.AIR_WAYBILL || t == DocumentType.ROAD_CONSIGNMENT_NOTE || t == DocumentType.SEA_WAYBILL
                || t == DocumentType.CHARTER_PARTY_BILL_OF_LADING || t == DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT || t == DocumentType.COURIER_RECEIPT || t == DocumentType.POST_RECEIPT;
    }
}
