package de.ostms.lc.lc.service;

import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.ProcurementItemStatus;
import de.ostms.lc.lc.domain.ProcurementLeadTime;
import de.ostms.lc.lc.repository.ProcurementItemStatusRepository;
import de.ostms.lc.lc.repository.ProcurementLeadTimeRepository;
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
    public record Item(String requirement, DocumentType documentType, String documentName, boolean present, LocalDate dueDate, int leadWorkingDays, Integer daysLeft, Risk risk, String hint, ProcurementItemStatus.Status status, String note) {
        /** English wording of the hint for the English interface. */
        @com.fasterxml.jackson.annotation.JsonProperty("hintEn")
        public String hintEn() {
            return documentType != null && isTransport(documentType)
                ? "Goods must be shipped by the latest shipment date; this document is then available."
                : "Lead time " + leadWorkingDays + " working days before the latest shipment date.";
        }
    }
    public record LeadTime(DocumentType documentType, String documentName, int workingDays, int defaultDays, boolean custom) {}
    public record Plan(LocalDate latestShipment, LocalDate expiry, Integer daysToShipment, Risk shipmentRisk, long open, long overdue, long critical, List<Item> items) {}

    /** Short form for lists: the shipment light plus how many documents are missing and which one is due next. */
    public record Summary(UUID lcId, Risk shipmentRisk, Integer daysToShipment, long open, long overdue, long critical, String nextDocument, LocalDate nextDue) {}

    static final int CRITICAL_DAYS = 3, SOON_DAYS = 10;
    static final Map<DocumentType, Integer> LEAD = Map.ofEntries(
            Map.entry(DocumentType.COMMERCIAL_INVOICE, 2), Map.entry(DocumentType.PACKING_LIST, 3), Map.entry(DocumentType.WEIGHT_LIST, 3),
            Map.entry(DocumentType.CERTIFICATE_OF_ORIGIN, 7), Map.entry(DocumentType.INSPECTION_CERTIFICATE, 10), Map.entry(DocumentType.QUALITY_CERTIFICATE, 10),
            Map.entry(DocumentType.INSURANCE_CERTIFICATE, 3), Map.entry(DocumentType.BENEFICIARY_CERTIFICATE, 2), Map.entry(DocumentType.BILL_OF_EXCHANGE, 2));
    static final int DEFAULT_LEAD = 5;

    private final LetterOfCreditRepository lettersOfCredit;
    private final LcDocumentRepository documents;
    private final DocumentCheckService checks;
    private final ProcurementLeadTimeRepository leadTimes;
    private final ProcurementItemStatusRepository statuses;

    public ProcurementPlanService(LetterOfCreditRepository lettersOfCredit, LcDocumentRepository documents, DocumentCheckService checks,
                                  ProcurementLeadTimeRepository leadTimes, ProcurementItemStatusRepository statuses) {
        this.lettersOfCredit = lettersOfCredit; this.documents = documents; this.checks = checks; this.leadTimes = leadTimes; this.statuses = statuses;
    }

    static int defaultLead(DocumentType type) { return isTransport(type) ? 0 : LEAD.getOrDefault(type, DEFAULT_LEAD); }

    /** All planned document types with the tenant's lead time (custom) or the built-in default. Transport documents are fixed on the shipment date. */
    public List<LeadTime> leadTimes() {
        Map<DocumentType, Integer> custom = customLeadTimes();
        List<LeadTime> out = new ArrayList<>();
        for (DocumentType type : planned()) out.add(new LeadTime(type, type.getDisplayName(), custom.getOrDefault(type, defaultLead(type)), defaultLead(type), custom.containsKey(type)));
        return out;
    }
    static List<DocumentType> planned() {
        return Arrays.stream(DocumentType.values()).filter(t -> !isTransport(t) && t != DocumentType.SWIFT_MT700 && t != DocumentType.ADVISING_LETTER
                && t != DocumentType.DOCUMENT_SUBMISSION && t != DocumentType.ANNEX).toList();
    }
    private Map<DocumentType, Integer> customLeadTimes() {
        Map<DocumentType, Integer> map = new EnumMap<>(DocumentType.class);
        leadTimes.findAll().forEach(l -> map.put(l.getDocumentType(), l.getWorkingDays()));
        return map;
    }
    public void setLeadTime(DocumentType type, int days, String user) {
        if (!planned().contains(type)) throw new IllegalArgumentException("Für diese Dokumentart gibt es keine einstellbare Vorlaufzeit.");
        if (days < 0 || days > 60) throw new IllegalArgumentException("Die Vorlaufzeit muss zwischen 0 und 60 Arbeitstagen liegen.");
        var row = leadTimes.byType(type).orElseGet(() -> new ProcurementLeadTime(type));
        row.change(days, user); leadTimes.save(row);
    }
    public void resetLeadTime(DocumentType type) { leadTimes.byType(type).ifPresent(leadTimes::delete); }

    public void setPartyRole(UUID lcId, String role) {
        LetterOfCredit lc = lettersOfCredit.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        lc.setPartyRole(role); lettersOfCredit.save(lc);
    }

    public void setStatus(UUID lcId, String requirement, ProcurementItemStatus.Status status, String note, String user) {
        LetterOfCredit lc = lettersOfCredit.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        if (!lc.getRequiredDocuments().contains(requirement)) throw new IllegalArgumentException("Diese Dokumentenanforderung gehört nicht zur LC.");
        String clean = note == null || note.isBlank() ? null : note.strip();
        if (clean != null && clean.length() > 500) throw new IllegalArgumentException("Die Notiz darf höchstens 500 Zeichen lang sein.");
        var row = statuses.one(lcId, requirement).orElseGet(() -> new ProcurementItemStatus(lcId, requirement));
        row.change(status, clean, user); statuses.save(row);
    }

    public Plan forLc(UUID lcId) { return forLc(lcId, LocalDate.now()); }

    /** One summary per running LC that has a shipment date or requirements (at most 300, soonest shipment first). */
    public List<Summary> summaries() {
        LocalDate today = LocalDate.now();
        List<Summary> out = new ArrayList<>();
        for (LetterOfCredit lc : lettersOfCredit.findAll()) {
            if (lc.getStatus() == de.ostms.lc.lc.domain.LetterOfCreditStatus.CLOSED || lc.getStatus() == de.ostms.lc.lc.domain.LetterOfCreditStatus.EXPIRED) continue;
            if (!lc.isExporterSide()) continue;
            if (lc.getRequiredDocuments().isEmpty() && lc.getLatestShipmentDate() == null) continue;
            Plan plan = forLc(lc.getId(), today);
            Item next = plan.items().stream().filter(i -> i.risk() != Risk.DONE && i.dueDate() != null).min(Comparator.comparing(Item::dueDate)).orElse(null);
            out.add(new Summary(lc.getId(), plan.shipmentRisk(), plan.daysToShipment(), plan.open(), plan.overdue(), plan.critical(), next == null ? null : next.documentName(), next == null ? null : next.dueDate()));
            if (out.size() >= 300) break;
        }
        out.sort(Comparator.comparing(Summary::daysToShipment, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    public Plan forLc(UUID lcId, LocalDate today) {
        LetterOfCredit lc = lettersOfCredit.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        var uploaded = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        Set<DocumentType> present = new HashSet<>();
        uploaded.forEach(d -> present.add(d.getDocumentType()));
        LocalDate shipment = lc.getLatestShipmentDate();
        Map<DocumentType, Integer> custom = customLeadTimes();
        Map<String, ProcurementItemStatus> manual = new HashMap<>();
        statuses.forLc(lcId).forEach(row -> manual.put(row.getRequirement(), row));
        List<Item> items = new ArrayList<>();
        for (String requirement : lc.getRequiredDocuments()) {
            Optional<DocumentType> type = checks.expectedType(lcId, requirement);
            if (type.isEmpty()) { items.add(new Item(requirement, null, null, false, null, 0, null, Risk.UNKNOWN, "Dokumentenart nicht erkannt – bitte Anforderung einem Dokumenttyp zuordnen.", ProcurementItemStatus.Status.NOT_STARTED, null)); continue; }
            var mine = manual.get(requirement);
            items.add(item(requirement, type.get(), present.contains(type.get()), shipment, today, custom.getOrDefault(type.get(), defaultLead(type.get())),
                    mine == null ? ProcurementItemStatus.Status.NOT_STARTED : mine.getStatus(), mine == null ? null : mine.getNote()));
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
        return item(requirement, type, present, shipment, today, defaultLead(type), ProcurementItemStatus.Status.NOT_STARTED, null);
    }

    static Item item(String requirement, DocumentType type, boolean present, LocalDate shipment, LocalDate today, int leadDays, ProcurementItemStatus.Status status, String note) {
        boolean transport = isTransport(type);
        int lead = transport ? 0 : leadDays;
        LocalDate due = shipment == null ? null : minusWorkingDays(shipment, lead);
        Integer left = due == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(today, due);
        Risk risk = present ? Risk.DONE : left == null ? Risk.UNKNOWN : riskFor(left);
        String hint = transport ? "Ware muss bis zum spätesten Versanddatum verladen sein; dieses Dokument liegt dann vor."
                : "Vorlauf " + lead + " Arbeitstage vor dem spätesten Versanddatum.";
        return new Item(requirement, type, type.getDisplayName(), present, due, lead, left, risk, hint, status, note);
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
