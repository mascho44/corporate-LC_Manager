package de.ostms.lc.document.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.SubmissionPackageRequest;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.rulepack.Mt700Requirements;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The package for the bank when documents are presented under a credit: a cover letter that lists the documents with originals and copies, and the
 * documents themselves in the usual order, numbered like the list. The internal check report and the SWIFT text of the credit stay out.
 */
@Service
public class SubmissionPackageService {
    /** Documents that are presented to the bank, in the order a presentation lists them. */
    static final List<DocumentType> ORDER = List.of(DocumentType.COMMERCIAL_INVOICE, DocumentType.PACKING_LIST, DocumentType.WEIGHT_LIST, DocumentType.BILL_OF_LADING,
        DocumentType.SEA_WAYBILL, DocumentType.CHARTER_PARTY_BILL_OF_LADING, DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT, DocumentType.AIR_WAYBILL,
        DocumentType.ROAD_CONSIGNMENT_NOTE, DocumentType.COURIER_RECEIPT, DocumentType.POST_RECEIPT, DocumentType.INSURANCE_CERTIFICATE, DocumentType.CERTIFICATE_OF_ORIGIN,
        DocumentType.INSPECTION_CERTIFICATE, DocumentType.QUALITY_CERTIFICATE, DocumentType.BENEFICIARY_CERTIFICATE, DocumentType.BILL_OF_EXCHANGE);
    private static final Pattern COPIES = Pattern.compile("\\b(\\d|ONE|TWO|THREE|FOUR|FIVE)\\s*(?:\\(\\d\\)\\s*)?(?:COPIES|COPY)\\b");

    public record Candidate(UUID documentId, String filename, DocumentType documentType, String typeLabel, String documentNumber, LocalDate documentDate, boolean included, int originals, int copies, Integer pages) {}
    public record Plan(LocalDate date, String recipient, String ourReference, List<Candidate> documents, List<String> warnings, long openDiscrepancies) {}
    public record Package(String filename, byte[] content) {}

    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;
    private final DocxTemplateService letters;
    private final DocumentCheckService checks;

    public SubmissionPackageService(LetterOfCreditRepository lcs, LcDocumentRepository documents, DocxTemplateService letters, DocumentCheckService checks) {
        this.lcs = lcs; this.documents = documents; this.letters = letters; this.checks = checks;
    }

    // ---- plan ---------------------------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Plan plan(UUID lcId) {
        LetterOfCredit lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        var originals = requiredOriginals(lc);
        var copies = requiredCopies(lc);
        var candidates = new ArrayList<Candidate>();
        var seen = new HashSet<DocumentType>();
        for (LcDocument document : presentable(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId))) {
            DocumentType type = document.getDocumentType();
            boolean first = seen.add(type);
            candidates.add(new Candidate(document.getId(), document.getOriginalFilename(), type, type.getDisplayName(), document.getExtractedDocumentNumber(), document.getDocumentDate(),
                first, first ? originals.getOrDefault(type, 1) : 0, first ? copies.getOrDefault(type, 0) : 0, pages(document)));
        }
        var warnings = new ArrayList<String>();
        long open = 0;
        for (CheckResult result : checks.precheck(lcId).results()) {
            if (result.severity() != CheckResult.Severity.DISCREPANCY) continue;
            open++;
            if (warnings.size() < 12) warnings.add(result.message() + (result.documentName() == null ? "" : " (" + result.documentName() + ")"));
        }
        if (candidates.isEmpty()) warnings.add(0, "In der Akte liegen noch keine Dokumente, die der Bank vorgelegt werden können.");
        return new Plan(LocalDate.now(), recipient(lc), null, candidates, warnings, open);
    }

    /** Documents of a presentation: the types in ORDER, sorted like ORDER and then by date. */
    static List<LcDocument> presentable(List<LcDocument> all) {
        var out = new ArrayList<LcDocument>();
        for (LcDocument document : all) if (document.getDocumentType() != null && ORDER.contains(document.getDocumentType()) && document.getContent() != null) out.add(document);
        out.sort(Comparator.comparingInt((LcDocument d) -> ORDER.indexOf(d.getDocumentType()))
            .thenComparing(d -> d.getDocumentDate() == null ? LocalDate.MAX : d.getDocumentDate())
            .thenComparing(d -> d.getOriginalFilename() == null ? "" : d.getOriginalFilename(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    static String recipient(LetterOfCredit lc) {
        for (String bank : new String[]{lc.getAvailableWith(), lc.getAdvisingBank(), lc.getIssuingBank()})
            if (bank != null && !bank.isBlank() && !bank.trim().toUpperCase(Locale.ROOT).startsWith("ANY BANK")) return bank.trim();
        return "";
    }

    private static Map<DocumentType, Integer> requiredOriginals(LetterOfCredit lc) {
        var out = new EnumMap<DocumentType, Integer>(DocumentType.class);
        for (var s : Mt700Requirements.detect(lc)) if (s.field() == de.ostms.lc.rulepack.PackDefinition.Field.LC_REQUIRED_ORIGINAL_COUNT) out.putIfAbsent(s.documentType(), Integer.parseInt(s.value()));
        return out;
    }

    /** "PACKING LIST IN 2 COPIES" -> packing list: 2; only when the items of one type agree. */
    static Map<DocumentType, Integer> requiredCopies(LetterOfCredit lc) {
        var found = new EnumMap<DocumentType, Set<Integer>>(DocumentType.class);
        for (String line : lc.getRequiredDocuments() == null ? List.<String>of() : lc.getRequiredDocuments()) {
            String upper = line.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            DocumentType type = Mt700Requirements.typeOf(upper);
            var match = COPIES.matcher(upper);
            if (type == null || !match.find()) continue;
            String v = match.group(1);
            found.computeIfAbsent(type, t -> new HashSet<>()).add(switch (v) { case "ONE" -> 1; case "TWO" -> 2; case "THREE" -> 3; case "FOUR" -> 4; case "FIVE" -> 5; default -> Integer.parseInt(v); });
        }
        var out = new EnumMap<DocumentType, Integer>(DocumentType.class);
        found.forEach((type, numbers) -> { if (numbers.size() == 1) out.put(type, numbers.iterator().next()); });
        return out;
    }

    private static Integer pages(LcDocument document) {
        if (document.getContent() == null || !"application/pdf".equalsIgnoreCase(document.getContentType())) return null;
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(document.getContent())) { return pdf.getNumberOfPages(); }
        catch (IOException | RuntimeException unreadable) { return null; }
    }

    // ---- package ------------------------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Package build(UUID lcId, SubmissionPackageRequest request) throws IOException {
        LetterOfCredit lc = lcs.findById(lcId).orElseThrow(() -> new NoSuchElementException("Akkreditiv nicht gefunden."));
        var byId = new HashMap<UUID, LcDocument>();
        for (LcDocument document : documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId)) byId.put(document.getId(), document);
        var chosen = new ArrayList<LcDocument>();
        var counts = new HashMap<UUID, SubmissionPackageRequest.Entry>();
        for (var entry : request.documents()) {
            LcDocument document = byId.get(entry.documentId());
            if (document == null || document.getContent() == null) throw new IllegalArgumentException("Ein gewähltes Dokument gehört nicht zu dieser Akte.");
            if (counts.put(entry.documentId(), entry) != null) throw new IllegalArgumentException("Ein Dokument ist doppelt gewählt.");
            if (entry.originals() + entry.copies() == 0) throw new IllegalArgumentException("Für „" + document.getOriginalFilename() + "“ ist weder ein Original noch eine Kopie angegeben.");
            chosen.add(document);
        }
        chosen.sort(Comparator.comparingInt((LcDocument d) -> ORDER.contains(d.getDocumentType()) ? ORDER.indexOf(d.getDocumentType()) : ORDER.size())
            .thenComparing(d -> d.getDocumentDate() == null ? LocalDate.MAX : d.getDocumentDate())
            .thenComparing(d -> d.getOriginalFilename() == null ? "" : d.getOriginalFilename(), String.CASE_INSENSITIVE_ORDER));
        var rows = new ArrayList<DocxTemplateService.SubmissionRow>();
        int n = 1;
        for (LcDocument document : chosen) {
            var entry = counts.get(document.getId());
            Integer pages = pages(document);
            rows.add(new DocxTemplateService.SubmissionRow(String.valueOf(n++), document.getDocumentType() == null ? "Document" : document.getDocumentType().getDisplayName(),
                blankToDash(document.getExtractedDocumentNumber()), document.getDocumentDate() == null ? "" : document.getDocumentDate().toString(),
                String.valueOf(entry.originals()), String.valueOf(entry.copies()), pages == null ? "–" : String.valueOf(pages)));
        }
        byte[] letter = letters.renderSubmissionLetter(lc, new DocxTemplateService.SubmissionLetter(request.date(), request.recipient().trim(), blank(request.ourReference()), blank(request.remarks()), rows));
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "00-Einreichungsschreiben.docx", letter);
            int number = 1;
            var used = new HashSet<String>();
            for (LcDocument document : chosen) {
                String name = String.format("%02d-%s", number++, safe(document.getOriginalFilename() == null ? "Dokument" : document.getOriginalFilename()));
                if (!used.add(name)) name = number + "-" + name;
                put(zip, name, document.getContent());
            }
            zip.finish();
            return new Package("Einreichung-" + safe(lc.getReference()) + "-" + request.date() + ".zip", output.toByteArray());
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String blankToDash(String value) { return value == null || value.isBlank() ? "-" : value.trim(); }
    static String safe(String name) { return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim(); }
}
