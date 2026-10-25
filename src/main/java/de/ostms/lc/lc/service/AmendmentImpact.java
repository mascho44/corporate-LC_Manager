package de.ostms.lc.lc.service;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.Amendment;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.rulepack.Mt700Requirements;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What an amendment means for the file as it is: amount, dates, goods, documents and conditions compared with the documents already there.
 * Pure calculation, so it can be shown before the amendment is accepted.
 */
public final class AmendmentImpact {
    public enum Level { INFO, WARNING, ACTION }

    public record Item(String area, Level level, String message, List<String> documents) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final Set<DocumentType> TRANSPORT = EnumSet.of(DocumentType.BILL_OF_LADING, DocumentType.AIR_WAYBILL, DocumentType.ROAD_CONSIGNMENT_NOTE, DocumentType.SEA_WAYBILL,
        DocumentType.CHARTER_PARTY_BILL_OF_LADING, DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT, DocumentType.COURIER_RECEIPT, DocumentType.POST_RECEIPT);
    private static final Set<DocumentType> DESCRIBES_GOODS = EnumSet.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.CERTIFICATE_OF_ORIGIN,
        DocumentType.BENEFICIARY_CERTIFICATE, DocumentType.QUALITY_CERTIFICATE, DocumentType.INSPECTION_CERTIFICATE);

    private AmendmentImpact() {}

    public static List<Item> of(LetterOfCredit lc, Amendment amendment, List<LcDocument> documents, LocalDate today) {
        var items = new ArrayList<Item>();
        amount(items, lc, amendment, documents);
        expiry(items, lc, amendment, documents, today);
        shipment(items, amendment, documents);
        if (present(amendment.getChangedGoods())) {
            var affected = names(documents, d -> DESCRIBES_GOODS.contains(d.getDocumentType()));
            items.add(new Item("Warenbeschreibung (45B)", Level.ACTION, affected.isEmpty()
                ? "Die Warenbeschreibung ändert sich. Alle künftigen Dokumente müssen den neuen Wortlaut verwenden."
                : "Die Warenbeschreibung ändert sich. Diese Dokumente nennen die Ware und müssen gegen den neuen Wortlaut geprüft und meist neu ausgestellt werden.", affected));
        }
        requirements(items, amendment, documents);
        if (present(amendment.getChangedConditions()))
            items.add(new Item("Zusatzbedingungen (47B)", Level.WARNING, "Die Zusatzbedingungen ändern sich. Alle bereits erstellten Dokumente gegen die neuen Bedingungen halten.", names(documents, d -> true)));
        if (present(amendment.getOtherChanges()))
            items.add(new Item("Weitere Felder", Level.INFO, "Die Änderung enthält weitere Felder (zum Beispiel Zahlungsbedingungen oder Gebühren); sie sind im Änderungstext aufgeführt und nicht automatisch bewertet.", List.of()));
        if (items.isEmpty()) items.add(new Item("Inhalt", Level.INFO, "Die Änderung betrifft weder Betrag, Fristen, Ware, Dokumente noch Bedingungen.", List.of()));
        return items;
    }

    private static void amount(List<Item> items, LetterOfCredit lc, Amendment a, List<LcDocument> documents) {
        BigDecimal delta = (a.getAmountIncrease() == null ? BigDecimal.ZERO : a.getAmountIncrease()).subtract(a.getAmountDecrease() == null ? BigDecimal.ZERO : a.getAmountDecrease());
        if (delta.signum() == 0 || lc.getAmount() == null) return;
        BigDecimal now = lc.getAmount().add(delta);
        String cur = lc.getCurrency() == null ? "" : " " + lc.getCurrency();
        String change = (delta.signum() > 0 ? "Erhöhung" : "Minderung") + " um " + delta.abs().toPlainString() + cur + " auf " + now.toPlainString() + cur + ".";
        BigDecimal invoiced = BigDecimal.ZERO;
        var invoices = new ArrayList<String>();
        for (LcDocument d : documents) {
            if (d.getDocumentType() != DocumentType.COMMERCIAL_INVOICE) continue;
            BigDecimal value = d.getAmount() != null ? d.getAmount() : d.getExtractedAmount();
            if (value == null) continue;
            invoiced = invoiced.add(value);
            invoices.add(d.getOriginalFilename());
        }
        if (delta.signum() < 0 && invoiced.compareTo(now) > 0)
            items.add(new Item("Betrag (32B/33B)", Level.ACTION, "Betrag: " + change + " Die vorhandenen Rechnungen (" + invoiced.toPlainString() + cur + ") übersteigen den neuen Betrag; Rechnung kürzen oder die Minderung ablehnen.", invoices));
        else items.add(new Item("Betrag (32B/33B)", delta.signum() < 0 ? Level.WARNING : Level.INFO, "Betrag: " + change + (invoices.isEmpty() ? "" : " Vorhandene Rechnungen: " + invoiced.toPlainString() + cur + "."), invoices));
    }

    private static void expiry(List<Item> items, LetterOfCredit lc, Amendment a, List<LcDocument> documents, LocalDate today) {
        LocalDate expiry = a.getNewExpiryDate();
        if (expiry == null) return;
        String place = a.getNewExpiryPlace() == null || a.getNewExpiryPlace().isBlank() ? "" : " in " + a.getNewExpiryPlace().trim();
        String was = lc.getExpiryDate() == null ? "" : " (bisher " + lc.getExpiryDate().format(DATE) + ")";
        if (expiry.isBefore(today)) {
            items.add(new Item("Ablauf (31E)", Level.ACTION, "Neuer Ablauf " + expiry.format(DATE) + place + was + " liegt in der Vergangenheit; eine Vorlage ist nicht mehr möglich.", List.of()));
            return;
        }
        var late = names(documents, d -> d.getDocumentDate() != null && d.getDocumentDate().isAfter(expiry));
        boolean shorter = lc.getExpiryDate() != null && expiry.isBefore(lc.getExpiryDate());
        items.add(new Item("Ablauf (31E)", !late.isEmpty() ? Level.ACTION : shorter ? Level.WARNING : Level.INFO,
            "Neuer Ablauf " + expiry.format(DATE) + place + was + (shorter ? ", also früher; die Einreichung muss entsprechend früher geplant werden." : ".") + (late.isEmpty() ? "" : " Diese Dokumente sind nach dem neuen Ablauf datiert."), late));
    }

    private static void shipment(List<Item> items, Amendment a, List<LcDocument> documents) {
        LocalDate latest = a.getNewLatestShipmentDate();
        if (latest == null) return;
        var late = names(documents, d -> TRANSPORT.contains(d.getDocumentType()) && d.getDocumentDate() != null && d.getDocumentDate().isAfter(latest));
        items.add(new Item("Letzter Verladetag (44C)", late.isEmpty() ? Level.INFO : Level.ACTION,
            "Neuer letzter Verladetag: " + latest.format(DATE) + "." + (late.isEmpty() ? "" : " Diese Transportdokumente sind später datiert und wären abweichend."), late));
    }

    private static void requirements(List<Item> items, Amendment a, List<LcDocument> documents) {
        if (!present(a.getChangedDocuments())) return;
        var present = new ArrayList<String>();
        var missing = new ArrayList<String>();
        var seen = EnumSet.noneOf(DocumentType.class);
        for (String line : a.getChangedDocuments().split("\\R")) {
            DocumentType type = Mt700Requirements.typeOf(line.toUpperCase(Locale.ROOT));
            if (type == null || type == DocumentType.OTHER || !seen.add(type)) continue;
            var have = names(documents, d -> d.getDocumentType() == type);
            if (have.isEmpty()) missing.add(type.getDisplayName()); else present.addAll(have);
        }
        String message = "Die Dokumentenanforderungen ändern sich." + (missing.isEmpty() ? "" : " Noch nicht vorhanden: " + String.join(", ", missing) + ".")
            + (present.isEmpty() ? "" : " Vorhandene Dokumente dieser Arten gegen die neuen Anforderungen prüfen.");
        items.add(new Item("Dokumentenanforderungen (46B)", missing.isEmpty() && present.isEmpty() ? Level.WARNING : Level.ACTION, message, present));
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static List<String> names(List<LcDocument> documents, java.util.function.Predicate<LcDocument> filter) {
        var names = new ArrayList<String>();
        for (LcDocument d : documents) if (filter.test(d)) names.add(d.getOriginalFilename());
        return names;
    }
}
