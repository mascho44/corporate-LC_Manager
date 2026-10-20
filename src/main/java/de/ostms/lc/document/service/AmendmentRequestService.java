package de.ostms.lc.document.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.AmendmentRequestInput;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A request to amend a credit: the importer asks the issuing bank, the exporter asks the buyer to have it amended. Suggestions come from the open
 * findings (expiry, amount, shipment date); the output is a letter (PDF) and the same changes in the field layout of an MT707 to copy into a bank portal.
 */
@Service
public class AmendmentRequestService {
    private static final DateTimeFormatter LETTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH), SWIFT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Set<DocumentType> TRANSPORT = EnumSet.of(DocumentType.BILL_OF_LADING, DocumentType.AIR_WAYBILL, DocumentType.ROAD_CONSIGNMENT_NOTE, DocumentType.SEA_WAYBILL,
        DocumentType.CHARTER_PARTY_BILL_OF_LADING, DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT);

    public record Proposal(LocalDate date, String recipient, boolean toBank, String currency, BigDecimal currentAmount, LocalDate currentExpiry, LocalDate currentLatestShipment,
                           BigDecimal newAmount, LocalDate newExpiryDate, LocalDate newLatestShipmentDate, List<String> hints) {}
    public record Change(String field, String tag, String current, String requested) {}

    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;
    private final DocumentCheckService checks;
    private final DocxTemplateService templates;

    public AmendmentRequestService(LetterOfCreditRepository lcs, LcDocumentRepository documents, DocumentCheckService checks, DocxTemplateService templates) {
        this.lcs = lcs; this.documents = documents; this.checks = checks; this.templates = templates;
    }

    /** Recipient by role and new values where an open finding shows the credit cannot be met as it is. */
    @Transactional(readOnly = true)
    public Proposal proposal(UUID lcId, LocalDate today) {
        LetterOfCredit lc = credit(lcId);
        boolean toBank = "APPLICANT".equals(lc.getPartyRole());
        var hints = new ArrayList<String>();
        BigDecimal amount = null; LocalDate expiry = null, shipment = null;
        var files = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        for (CheckResult r : checks.precheck(lcId).results()) {
            if (r.severity() != CheckResult.Severity.DISCREPANCY || r.code() == null) continue;
            String code = r.code();
            if ((code.equals("LC_EXPIRED") || code.equals("DOCUMENT_AFTER_EXPIRY")) && expiry == null) {
                LocalDate base = lc.getExpiryDate() == null || lc.getExpiryDate().isBefore(today) ? today : lc.getExpiryDate();
                expiry = base.plusDays(30); hints.add(r.message());
            } else if ((code.equals("INVOICE_AMOUNT_EXCEEDED") || code.startsWith("CUMULATIVE_INVOICE_AMOUNT_")) && amount == null) {
                amount = invoiced(files); hints.add(r.message());
            } else if (code.contains("SHIPMENT_DATE_") && shipment == null) {
                shipment = latestTransportDate(files); hints.add(r.message());
            }
        }
        return new Proposal(today, toBank ? DocxTemplateService.oneLine(lc.getIssuingBank()) : lc.getApplicant(), toBank, lc.getCurrency(), lc.getAmount(), lc.getExpiryDate(), lc.getLatestShipmentDate(),
            amount, expiry, shipment, hints);
    }

    /** The requested changes compared with the credit as it is; at least one is required. */
    static List<Change> changes(LetterOfCredit lc, AmendmentRequestInput in) {
        var out = new ArrayList<Change>();
        String cur = lc.getCurrency() == null ? "" : lc.getCurrency() + " ";
        if (in.newAmount() != null && (lc.getAmount() == null || in.newAmount().compareTo(lc.getAmount()) != 0)) {
            String tag = lc.getAmount() == null || in.newAmount().compareTo(lc.getAmount()) > 0 ? "32B" : "33B";
            out.add(new Change("Credit amount", tag, lc.getAmount() == null ? "-" : cur + DocxTemplateService.money(lc.getAmount()), cur + DocxTemplateService.money(in.newAmount())));
        }
        if (in.newExpiryDate() != null && !in.newExpiryDate().equals(lc.getExpiryDate()))
            out.add(new Change("Date and place of expiry", "31E", date(lc.getExpiryDate()) + place(lc.getExpiryPlace()), date(in.newExpiryDate()) + place(blank(in.newExpiryPlace()) ? lc.getExpiryPlace() : in.newExpiryPlace())));
        if (in.newLatestShipmentDate() != null && !in.newLatestShipmentDate().equals(lc.getLatestShipmentDate()))
            out.add(new Change("Latest date of shipment", "44C", date(lc.getLatestShipmentDate()), date(in.newLatestShipmentDate())));
        if (!blank(in.goods())) out.add(new Change("Description of goods", "45B", "as per credit", in.goods().trim()));
        if (!blank(in.documents())) out.add(new Change("Documents required", "46B", "as per credit", in.documents().trim()));
        if (!blank(in.conditions())) out.add(new Change("Additional conditions", "47B", "as per credit", in.conditions().trim()));
        if (!blank(in.other())) out.add(new Change("Other", "", "", in.other().trim()));
        if (out.isEmpty()) throw new IllegalArgumentException("Bitte mindestens eine Änderung angeben, die vom Akkreditiv abweicht.");
        return out;
    }

    /** The changes in MT707 field layout, as a template for the bank portal (not a SWIFT message). */
    @Transactional(readOnly = true)
    public String swiftText(UUID lcId, AmendmentRequestInput in) {
        LetterOfCredit lc = credit(lcId);
        var changes = changes(lc, in);
        var text = new StringBuilder(":20:").append(blank(in.ourReference()) ? "-" : in.ourReference().trim()).append('\n').append(":21:").append(lc.getReference()).append('\n');
        text.append(":30:").append(in.date().format(SWIFT)).append('\n');
        for (Change c : changes) {
            switch (c.tag()) {
                case "32B", "33B" -> text.append(':').append(c.tag()).append(':').append(lc.getCurrency() == null ? "" : lc.getCurrency())
                    .append(swiftAmount(in.newAmount().subtract(lc.getAmount() == null ? BigDecimal.ZERO : lc.getAmount()).abs())).append('\n');
                case "31E" -> text.append(":31E:").append(in.newExpiryDate().format(SWIFT)).append(blank(in.newExpiryPlace()) ? "" : in.newExpiryPlace().trim().toUpperCase(Locale.ROOT)).append('\n');
                case "44C" -> text.append(":44C:").append(in.newLatestShipmentDate().format(SWIFT)).append('\n');
                case "45B", "46B", "47B" -> text.append(':').append(c.tag()).append(":/REPALL/").append(c.requested()).append('\n');
                default -> text.append(":79:").append(c.requested()).append('\n');
            }
        }
        return text.toString().stripTrailing();
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID lcId, AmendmentRequestInput in) throws IOException {
        LetterOfCredit lc = credit(lcId);
        var changes = changes(lc, in);
        var data = templates.companyData(lc);
        data.put("lcReference", DocxTemplateService.value(lc.getReference()));
        data.put("date", in.date().format(LETTER));
        data.put("recipient", in.recipient().trim());
        data.put("ourReference", DocxTemplateService.value(in.ourReference()));
        data.put("placeDate", DocxTemplateService.joined(", ", (String) data.get("companyCityOnly"), in.date().format(LETTER)));
        String toBank = "APPLICANT".equals(lc.getPartyRole()) ? "issued by you" : (blank(lc.getIssuingBank()) ? "issued" : "issued by " + DocxTemplateService.oneLine(lc.getIssuingBank()));
        data.put("intro", "With reference to the above documentary credit " + toBank + (blank(lc.getBeneficiary()) || "BENEFICIARY".equals(lc.getPartyRole()) ? "" : " in favour of " + DocxTemplateService.oneLine(lc.getBeneficiary()))
            + ", we kindly request the following amendment" + (changes.size() == 1 ? "" : "s") + ":");
        data.put("reason", blank(in.reason()) ? "" : in.reason().trim());
        return ProfessionalPdf.amendmentRequest(data, changes);
    }

    private LetterOfCredit credit(UUID lcId) { return lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden.")); }
    private static BigDecimal invoiced(List<LcDocument> files) {
        BigDecimal sum = BigDecimal.ZERO; boolean any = false;
        for (LcDocument d : files) if (d.getDocumentType() == DocumentType.COMMERCIAL_INVOICE) { BigDecimal v = d.getAmount() != null ? d.getAmount() : d.getExtractedAmount(); if (v != null) { sum = sum.add(v); any = true; } }
        return any ? sum : null;
    }
    private static LocalDate latestTransportDate(List<LcDocument> files) {
        return files.stream().filter(d -> TRANSPORT.contains(d.getDocumentType()) && d.getDocumentDate() != null).map(LcDocument::getDocumentDate).max(LocalDate::compareTo).orElse(null);
    }
    private static String date(LocalDate d) { return d == null ? "-" : d.format(LETTER); }
    private static String place(String p) { return blank(p) ? "" : ", " + p.trim(); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String swiftAmount(BigDecimal v) { return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ','); }
}
