package de.ostms.lc.document.service;

import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.DocumentSetRequest;
import de.ostms.lc.document.api.DocumentView;
import de.ostms.lc.document.api.GeneratedDocumentItemRequest;
import de.ostms.lc.document.api.GeneratedDocumentRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.rulepack.Mt700Facts;
import de.ostms.lc.rulepack.Mt700Requirements;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All documents of a shipment in one step: invoice, packing list and the certificates the credit asks for, with consistent numbers, dates,
 * goods lines, totals and weights, followed by the automatic check of the new documents against the credit.
 */
@Service
public class DocumentSetService {
    /** Document types a set can contain, in the order they are created. */
    static final List<DocumentType> ORDER = List.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.CERTIFICATE_OF_ORIGIN, DocumentType.BENEFICIARY_CERTIFICATE, DocumentType.QUALITY_CERTIFICATE);
    private static final Map<DocumentType, String> PREFIX = Map.of(DocumentType.COMMERCIAL_INVOICE, "INV-", DocumentType.PACKING_LIST, "PL-", DocumentType.CERTIFICATE_OF_ORIGIN, "COO-", DocumentType.BENEFICIARY_CERTIFICATE, "BC-", DocumentType.QUALITY_CERTIFICATE, "QC-");
    private static final Map<DocumentType, Pattern> NAMED_IN_CREDIT = Map.of(
        DocumentType.COMMERCIAL_INVOICE, Pattern.compile("INVOICE"),
        DocumentType.PACKING_LIST, Pattern.compile("PACKING\\s+LIST"),
        DocumentType.CERTIFICATE_OF_ORIGIN, Pattern.compile("CERTIFICATE\\s+OF\\s+ORIGIN"),
        DocumentType.BENEFICIARY_CERTIFICATE, Pattern.compile("BENEFICIARY'?S?\\s+(?:CERTIFICATE|STATEMENT)"),
        DocumentType.QUALITY_CERTIFICATE, Pattern.compile("(?:QUALITY|ANALYSIS)\\s+CERTIFICATE|CERTIFICATE\\s+OF\\s+(?:QUALITY|ANALYSIS)"));
    private static final Pattern NUMBER_WITH_PREFIX = Pattern.compile("^[A-Za-z]{1,6}[-_/]\\s*(.+)$");

    public record Requirement(DocumentType documentType, String label, boolean namedInCredit, String originals, boolean signed) {}
    public record Proposal(LocalDate documentDate, String invoiceNumber, List<GeneratedDocumentItemRequest> items, List<DocumentType> types, List<Requirement> requirements, String currency, BigDecimal maximumInvoiceTotal, boolean companyConfigured) {}
    public record Finding(String severity, String code, String message, String documentName) {}
    public record Result(List<DocumentView> documents, List<String> warnings, BigDecimal total, String currency, List<Finding> findings) {}

    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;
    private final GeneratedDocumentService generated;
    private final DocumentCheckService checks;
    private final de.ostms.lc.company.service.CompanyProfileService companies;

    public DocumentSetService(LetterOfCreditRepository lcs, LcDocumentRepository documents, GeneratedDocumentService generated, DocumentCheckService checks, de.ostms.lc.company.service.CompanyProfileService companies) {
        this.lcs = lcs; this.documents = documents; this.generated = generated; this.checks = checks; this.companies = companies;
    }

    // ---- proposal ----------------------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Proposal proposal(UUID lcId) {
        LetterOfCredit lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        var types = typesNamedInCredit(lc);
        var named = new HashSet<>(types);
        if (types.isEmpty()) types = List.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST);
        var requirements = new ArrayList<Requirement>();
        var detected = Mt700Requirements.detect(lc);
        for (DocumentType type : ORDER) {
            String originals = detected.stream().filter(s -> s.documentType() == type && s.field() == de.ostms.lc.rulepack.PackDefinition.Field.LC_REQUIRED_ORIGINAL_COUNT).map(Mt700Requirements.Suggestion::value).findFirst().orElse(null);
            boolean signed = detected.stream().anyMatch(s -> s.documentType() == type && s.field() == de.ostms.lc.rulepack.PackDefinition.Field.LC_SIGNATURE_REQUIRED);
            requirements.add(new Requirement(type, type.getDisplayName(), named.contains(type), originals, signed));
        }
        BigDecimal maximum = lc.getAmount() == null ? null : checks.maximumCreditAmount(lc);
        return new Proposal(LocalDate.now(), nextInvoiceNumber(lcId), proposedItems(lc), types, requirements, lc.getCurrency(), maximum, companies.hasLetterhead(lc.getCompanyId()));
    }

    List<DocumentType> typesNamedInCredit(LetterOfCredit lc) {
        var found = new ArrayList<DocumentType>();
        var lines = lc.getRequiredDocuments() == null ? List.<String>of() : lc.getRequiredDocuments();
        for (DocumentType type : ORDER)
            for (String line : lines)
                if (NAMED_IN_CREDIT.get(type).matcher(line.toUpperCase(Locale.ROOT)).find()) { found.add(type); break; }
        return found;
    }

    private String nextInvoiceNumber(UUID lcId) {
        long existing = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId).stream().filter(d -> d.getDocumentType() == DocumentType.COMMERCIAL_INVOICE).count();
        return String.format("INV-%d-%03d", LocalDate.now().getYear(), existing + 1);
    }

    /** One line from the credit: goods text, quantity and unit when the credit names them, the credit amount as the line amount. */
    List<GeneratedDocumentItemRequest> proposedItems(LetterOfCredit lc) {
        var facts = new HashMap<de.ostms.lc.rulepack.PackDefinition.Field, String>();
        for (var fact : Mt700Facts.detect(lc)) facts.putIfAbsent(fact.field(), fact.value());
        String description = facts.get(de.ostms.lc.rulepack.PackDefinition.Field.LC_GOODS_DESCRIPTION);
        if (description == null) description = de.ostms.lc.lc.service.LcConditions.value(lc, de.ostms.lc.lc.domain.LcCondition.GOODS_DESCRIPTION).orElse("");
        if (description.length() > 1000) description = description.substring(0, 1000);
        BigDecimal quantity = number(facts.get(de.ostms.lc.rulepack.PackDefinition.Field.LC_QUANTITY));
        String unit = facts.get(de.ostms.lc.rulepack.PackDefinition.Field.LC_QUANTITY_UNIT);
        BigDecimal amount = lc.getAmount();
        BigDecimal unitPrice = quantity != null && quantity.signum() > 0 && amount != null ? amount.divide(quantity, 2, RoundingMode.HALF_UP) : null;
        return List.of(new GeneratedDocumentItemRequest("1", description, quantity, unit == null ? null : unit.toLowerCase(Locale.ROOT), unitPrice, amount, null, null, null));
    }

    private static BigDecimal number(String text) {
        try { return text == null ? null : new BigDecimal(text); } catch (NumberFormatException invalid) { return null; }
    }

    // ---- creation ----------------------------------------------------------------------------------------------------------------------------

    @Transactional
    public Result create(UUID lcId, DocumentSetRequest request) throws IOException {
        LetterOfCredit lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        var types = ORDER.stream().filter(request.types()::contains).toList();
        if (types.isEmpty()) throw new IllegalArgumentException("Bitte mindestens ein Dokument auswählen, das erzeugt werden kann.");
        var items = withAmounts(request.items());
        var warnings = new ArrayList<String>();
        BigDecimal total = total(items);
        if (types.contains(DocumentType.COMMERCIAL_INVOICE)) invoiceWarnings(lc, total, warnings);
        if (lc.getExpiryDate() != null && request.documentDate().isAfter(lc.getExpiryDate()))
            warnings.add("Das Dokumentdatum " + request.documentDate() + " liegt nach dem Verfall des Akkreditivs (" + lc.getExpiryDate() + ").");
        if (types.contains(DocumentType.PACKING_LIST) && request.packages() == null && items.stream().noneMatch(item -> item.packages() != null))
            warnings.add("Für die Packliste fehlen die Packstücke.");
        if (types.contains(DocumentType.PACKING_LIST) && request.grossWeight() == null && items.stream().noneMatch(item -> item.grossWeight() != null))
            warnings.add("Für die Packliste fehlt das Bruttogewicht.");

        String suffix = suffix(request.invoiceNumber().trim());
        var created = new ArrayList<DocumentView>();
        for (DocumentType type : types) {
            String number = type == DocumentType.COMMERCIAL_INVOICE ? request.invoiceNumber().trim() : PREFIX.get(type) + suffix;
            var single = new GeneratedDocumentRequest(type, number, request.documentDate(), description(items), quantity(request, items),
                    packages(request, items), weight(request.netWeight(), items, GeneratedDocumentItemRequest::netWeight), weight(request.grossWeight(), items, GeneratedDocumentItemRequest::grossWeight),
                    request.notes(), items);
            created.add(request.pdf() ? generated.create(lcId, single) : generated.createDocx(lcId, single));
        }
        var names = new HashSet<String>();
        created.forEach(view -> names.add(view.originalFilename()));
        var findings = new ArrayList<Finding>();
        for (var result : checks.precheck(lcId).results())
            if (result.documentName() != null && names.contains(result.documentName()) && result.severity() != de.ostms.lc.check.api.CheckResult.Severity.OK)
                findings.add(new Finding(result.severity().name(), result.code(), result.message(), result.documentName()));
        return new Result(created, warnings, total, lc.getCurrency(), findings);
    }

    private void invoiceWarnings(LetterOfCredit lc, BigDecimal total, List<String> warnings) {
        if (total == null) { warnings.add("Für die Rechnung fehlen Beträge in den Positionen; die Summe kann nicht gegen das Akkreditiv geprüft werden."); return; }
        if (lc.getAmount() == null) return;
        BigDecimal maximum = checks.maximumCreditAmount(lc);
        if (maximum != null && total.compareTo(maximum) > 0)
            warnings.add("Die Rechnungssumme " + money(total) + " übersteigt den zulässigen Höchstbetrag " + money(maximum) + " um " + money(total.subtract(maximum)) + " (Akkreditivbetrag " + money(lc.getAmount()) + ").");
    }

    /** Amounts of lines that only name quantity and unit price are computed; the others are kept. */
    static List<GeneratedDocumentItemRequest> withAmounts(List<GeneratedDocumentItemRequest> items) {
        var out = new ArrayList<GeneratedDocumentItemRequest>();
        int position = 1;
        for (var item : items) {
            BigDecimal amount = item.amount();
            if (amount == null && item.quantity() != null && item.unitPrice() != null) amount = item.quantity().multiply(item.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            out.add(new GeneratedDocumentItemRequest(item.position() == null || item.position().isBlank() ? String.valueOf(position) : item.position(), item.description(), item.quantity(), item.unit(), item.unitPrice(), amount, item.packages(), item.netWeight(), item.grossWeight()));
            position++;
        }
        return out;
    }

    /** The sum of all line amounts, or null when a line has none. */
    static BigDecimal total(List<GeneratedDocumentItemRequest> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (var item : items) { if (item.amount() == null) return null; sum = sum.add(item.amount()); }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /** "INV-2026-0815" -> "2026-0815"; a number without a letter prefix is used whole. */
    static String suffix(String invoiceNumber) {
        var match = NUMBER_WITH_PREFIX.matcher(invoiceNumber);
        return match.matches() ? match.group(1).trim() : invoiceNumber;
    }

    private static String description(List<GeneratedDocumentItemRequest> items) {
        var parts = new ArrayList<String>();
        for (var item : items) if (item.description() != null && !item.description().isBlank()) parts.add(item.description().trim());
        String joined = String.join("; ", parts);
        return joined.length() > 4000 ? joined.substring(0, 4000) : joined;
    }

    private static String quantity(DocumentSetRequest request, List<GeneratedDocumentItemRequest> items) {
        if (request.quantity() != null && !request.quantity().isBlank()) return request.quantity();
        BigDecimal sum = BigDecimal.ZERO; String unit = null; boolean any = false;
        for (var item : items) {
            if (item.quantity() == null) continue;
            if (unit != null && item.unit() != null && !unit.equalsIgnoreCase(item.unit())) return null;
            if (item.unit() != null) unit = item.unit();
            sum = sum.add(item.quantity()); any = true;
        }
        return any ? sum.stripTrailingZeros().toPlainString() + (unit == null ? "" : " " + unit) : null;
    }

    private static Integer packages(DocumentSetRequest request, List<GeneratedDocumentItemRequest> items) {
        if (request.packages() != null) return request.packages();
        int sum = 0; boolean any = false;
        for (var item : items) if (item.packages() != null) { sum += item.packages(); any = true; }
        return any ? sum : null;
    }

    private static BigDecimal weight(BigDecimal given, List<GeneratedDocumentItemRequest> items, java.util.function.Function<GeneratedDocumentItemRequest, BigDecimal> of) {
        if (given != null) return given;
        BigDecimal sum = BigDecimal.ZERO; boolean any = false;
        for (var item : items) if (of.apply(item) != null) { sum = sum.add(of.apply(item)); any = true; }
        return any ? sum : null;
    }

    private static String money(BigDecimal value) {
        return new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(value);
    }
}
