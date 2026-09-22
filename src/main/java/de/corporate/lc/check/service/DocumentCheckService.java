package de.corporate.lc.check.service;

import de.corporate.lc.check.api.CheckResult;
import de.corporate.lc.check.api.ReviewSummary;
import de.corporate.lc.document.domain.DocumentType;
import de.corporate.lc.document.repository.LcDocumentRepository;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static de.corporate.lc.check.api.CheckResult.Severity.*;

@Service
public class DocumentCheckService {
    private final LetterOfCreditRepository lcs;
    private final LcDocumentRepository documents;

    public DocumentCheckService(LetterOfCreditRepository lcs, LcDocumentRepository documents) {
        this.lcs = lcs;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public ReviewSummary check(UUID lcId) {
        LetterOfCredit lc = lcs.findById(lcId).orElseThrow();
        var uploaded = documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
        List<CheckResult> results = new ArrayList<>();

        for (String requirement : lc.getRequiredDocuments()) {
            Optional<DocumentType> expected = classify(requirement);
            if (expected.isEmpty()) {
                results.add(new CheckResult(WARNING, "UNCLASSIFIED_REQUIREMENT", "Manual review required: " + requirement));
            } else if (uploaded.stream().noneMatch(d -> d.getDocumentType() == expected.get())) {
                results.add(new CheckResult(DISCREPANCY, "MISSING_DOCUMENT", expected.get().getDisplayName() + " is required but missing."));
            } else {
                results.add(new CheckResult(OK, "DOCUMENT_PRESENT", expected.get().getDisplayName() + " is present."));
            }
        }

        uploaded.forEach(document -> {
            if ("NO_TEXT".equals(document.getExtractionStatus()))
                results.add(new CheckResult(WARNING, "PDF_REQUIRES_OCR", document.getOriginalFilename() + ": no embedded text found; OCR/manual review required."));
            else if ("UNSUPPORTED".equals(document.getExtractionStatus()))
                results.add(new CheckResult(WARNING, "FORMAT_NOT_EXTRACTED", document.getOriginalFilename() + ": this format is stored but not automatically read."));
            else if ("FAILED".equals(document.getExtractionStatus()))
                results.add(new CheckResult(WARNING, "EXTRACTION_FAILED", document.getOriginalFilename() + ": text extraction failed."));

            if (document.getDocumentDate() == null) {
                results.add(new CheckResult(WARNING, "DATE_NOT_CAPTURED", document.getOriginalFilename() + ": document date not captured."));
            } else if (lc.getExpiryDate() != null && document.getDocumentDate().isAfter(lc.getExpiryDate())) {
                results.add(new CheckResult(DISCREPANCY, "DOCUMENT_AFTER_EXPIRY", document.getOriginalFilename() + ": date is after LC expiry."));
            }
            if (document.getDocumentType() == DocumentType.COMMERCIAL_INVOICE && document.getAmount() != null) {
                if (document.getExtractedAmount() != null && document.getAmount().compareTo(document.getExtractedAmount()) != 0)
                    results.add(new CheckResult(DISCREPANCY, "CAPTURED_AMOUNT_MISMATCH", "Captured invoice amount differs from the amount extracted from the document."));
                if (document.getExtractedCurrency() != null && document.getCurrency() != null && !document.getCurrency().equalsIgnoreCase(document.getExtractedCurrency()))
                    results.add(new CheckResult(DISCREPANCY, "CAPTURED_CURRENCY_MISMATCH", "Captured invoice currency differs from the currency extracted from the document."));
                if (document.getCurrency() != null && lc.getCurrency() != null && !document.getCurrency().equalsIgnoreCase(lc.getCurrency())) {
                    results.add(new CheckResult(DISCREPANCY, "CURRENCY_MISMATCH", "Invoice currency differs from the LC currency."));
                } else if (lc.getAmount() != null && document.getAmount().compareTo(lc.getAmount()) > 0) {
                    results.add(new CheckResult(DISCREPANCY, "INVOICE_AMOUNT_EXCEEDED", "Invoice amount exceeds the LC amount."));
                } else {
                    results.add(new CheckResult(OK, "INVOICE_AMOUNT_OK", "Invoice amount is within the LC amount."));
                }
            }
            if (document.getDocumentType() == DocumentType.COMMERCIAL_INVOICE && "EXTRACTED".equals(document.getExtractionStatus())) {
                if (document.getExtractedReference() == null)
                    results.add(new CheckResult(WARNING, "LC_REFERENCE_NOT_FOUND", document.getOriginalFilename() + ": no LC reference could be extracted."));
                else if (!sameReference(document.getExtractedReference(), lc.getReference()))
                    results.add(new CheckResult(DISCREPANCY, "LC_REFERENCE_MISMATCH", document.getOriginalFilename() + ": extracted LC reference differs from the LC."));
                else results.add(new CheckResult(OK, "LC_REFERENCE_OK", "Invoice references the correct LC."));

                if (lc.getBeneficiary() != null && !mentionsParty(document.getExtractedText(), lc.getBeneficiary()))
                    results.add(new CheckResult(DISCREPANCY, "BENEFICIARY_NOT_FOUND", "Beneficiary could not be matched in the invoice text."));
                else if (lc.getBeneficiary() != null)
                    results.add(new CheckResult(OK, "BENEFICIARY_OK", "Beneficiary is present in the invoice text."));
                if (lc.getApplicant() != null && !mentionsParty(document.getExtractedText(), lc.getApplicant()))
                    results.add(new CheckResult(WARNING, "APPLICANT_NOT_FOUND", "Applicant could not be matched in the invoice text."));
                else if (lc.getApplicant() != null)
                    results.add(new CheckResult(OK, "APPLICANT_OK", "Applicant is present in the invoice text."));
            }
        });

        uploaded.stream().filter(d -> d.getExtractedDocumentNumber() != null)
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getExtractedDocumentNumber().toUpperCase(Locale.ROOT)))
                .forEach((number, matches) -> {
                    if (matches.size() > 1) results.add(new CheckResult(WARNING, "DUPLICATE_DOCUMENT_NUMBER", "Document number " + number + " occurs more than once."));
                });

        if (lc.getExpiryDate() != null && lc.getExpiryDate().isBefore(LocalDate.now()))
            results.add(new CheckResult(WARNING, "LC_EXPIRED", "The LC has expired."));
        if (results.isEmpty()) results.add(new CheckResult(WARNING, "NO_RULES_APPLIED", "No automated rule could be applied."));

        return new ReviewSummary(count(results, DISCREPANCY), count(results, WARNING), count(results, OK), results);
    }

    private long count(List<CheckResult> results, CheckResult.Severity severity) {
        return results.stream().filter(result -> result.severity() == severity).count();
    }

    private Optional<DocumentType> classify(String requirement) {
        String text = requirement.toLowerCase(Locale.ROOT);
        if (text.contains("invoice")) return Optional.of(DocumentType.COMMERCIAL_INVOICE);
        if (text.contains("packing") || text.contains("weight list")) return Optional.of(DocumentType.PACKING_LIST);
        if (text.contains("bill of lading") || text.matches(".*\\bb/?l\\b.*")) return Optional.of(DocumentType.BILL_OF_LADING);
        if (text.contains("certificate of origin") || text.contains("origin certificate")) return Optional.of(DocumentType.CERTIFICATE_OF_ORIGIN);
        if (text.contains("insurance")) return Optional.of(DocumentType.INSURANCE_CERTIFICATE);
        return Optional.empty();
    }

    private boolean sameReference(String left, String right) {
        if (left == null || right == null) return false;
        return left.replaceAll("[^A-Za-z0-9]", "").equalsIgnoreCase(right.replaceAll("[^A-Za-z0-9]", ""));
    }

    private boolean mentionsParty(String text, String party) {
        if (text == null || party == null) return false;
        String haystack = normalize(text);
        var tokens = Arrays.stream(normalize(party).split(" "))
                .filter(token -> token.length() > 2)
                .filter(token -> !Set.of("ltd", "llc", "inc", "gmbh", "corp", "company", "limited").contains(token))
                .limit(2).toList();
        return !tokens.isEmpty() && tokens.stream().allMatch(haystack::contains);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
