package de.ostms.lc.apikey;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.api.DocumentController;
import de.ostms.lc.document.api.DocumentView;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.service.DocumentService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.service.LetterOfCreditService;
import de.ostms.lc.payment.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The external REST API, version 1. Reached with an API key (Authorization: Bearer lca_…); the scopes of the key decide: READ for the GET calls,
 * LC_CREATE for creating a file from an MT700, DOCUMENT_UPLOAD for attaching documents. The views are deliberately small and stable.
 */
@RestController @RequestMapping("/api/v1")
public class ApiV1Controller {
 public record LcSummary(UUID id,String reference,String status,String instrumentType,String applicant,String beneficiary,String currency,BigDecimal amount,LocalDate issueDate,LocalDate expiryDate,LocalDate latestShipmentDate){
  static LcSummary of(LetterOfCredit lc){return new LcSummary(lc.getId(),lc.getReference(),String.valueOf(lc.getStatus()),lc.getInstrumentType(),lc.getApplicant(),lc.getBeneficiary(),lc.getCurrency(),lc.getAmount(),lc.getIssueDate(),lc.getExpiryDate(),lc.getLatestShipmentDate());}
 }
 public record Review(String status,long discrepancies,long warnings,long passed){}
 public record LcDetail(LcSummary lc,List<String> requiredDocuments,List<DocumentView> documents,Review review,PaymentService.Summary payment){}
 public record Finding(String severity,String automaticSeverity,String code,String message,String document,String lcCondition,String evidence,String decision){
  static Finding of(CheckResult r){return new Finding(r.severity().name(),r.automaticSeverity()==null?null:r.automaticSeverity().name(),r.code(),r.message(),r.documentName(),r.lcCondition(),r.documentEvidence(),r.reviewDecision());}
 }
 public record Findings(Review review,List<Finding> findings){}
 public record CreateRequest(String rawMessage){}
 private final LetterOfCreditService lcs;private final DocumentService documents;private final DocumentCheckService checks;private final DocumentController uploads;private final AuditService audit;private final PaymentService payments;
 public ApiV1Controller(LetterOfCreditService lcs,DocumentService documents,DocumentCheckService checks,DocumentController uploads,AuditService audit,PaymentService payments){this.lcs=lcs;this.documents=documents;this.checks=checks;this.uploads=uploads;this.audit=audit;this.payments=payments;}

 @GetMapping("/lcs") public List<LcSummary> list(@RequestParam(required=false) String reference){
  return lcs.all().stream().filter(lc->reference==null||reference.isBlank()||reference.equalsIgnoreCase(lc.getReference())).map(LcSummary::of).toList();
 }
 @GetMapping("/lcs/{id}") public LcDetail one(@PathVariable UUID id){
  var lc=lcs.one(id);var review=checks.check(id);
  return new LcDetail(LcSummary.of(lc),lc.getRequiredDocuments(),documents.forLc(id),new Review(review.status(),review.discrepancies(),review.warnings(),review.passed()),payments.summary(id,java.time.LocalDate.now()));
 }
 @GetMapping("/lcs/{id}/findings") public Findings findings(@PathVariable UUID id){
  lcs.one(id);var review=checks.check(id);
  return new Findings(new Review(review.status(),review.discrepancies(),review.warnings(),review.passed()),review.results().stream().map(Finding::of).toList());
 }
 @GetMapping("/lcs/{id}/documents") public List<DocumentView> documents(@PathVariable UUID id){lcs.one(id);return documents.forLc(id);}

 @GetMapping("/lcs/{id}/payments") public PaymentService.Payments payments(@PathVariable UUID id){return payments.forLc(id,java.time.LocalDate.now());}
 /** Reports a payment received for a file (JSON: receivedOn, amount, currency, reference, note); another system, for example accounting, tells LCM. */
 @PostMapping(value="/lcs/{id}/payments",consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public PaymentService.View recordPayment(@PathVariable UUID id,@RequestBody PaymentService.Request request,Authentication authentication){return payments.record(id,request,"API",authentication);}

 /** Creates a file from an MT700 (plain text body or JSON {"rawMessage":"…"}). */
 @PostMapping(value="/lcs",consumes=MediaType.TEXT_PLAIN_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public LcSummary createFromText(@RequestBody String raw,Authentication authentication){return create(raw,authentication);}
 @PostMapping(value="/lcs",consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public LcSummary createFromJson(@RequestBody CreateRequest request,Authentication authentication){return create(request==null?null:request.rawMessage(),authentication);}
 private LcSummary create(String raw,Authentication authentication){
  if(raw==null||raw.isBlank())throw new IllegalArgumentException("Bitte die SWIFT-Nachricht (MT700) mitschicken.");
  LetterOfCredit lc=lcs.importMt700(raw);
  audit.record(authentication,"LC_IMPORTED","LETTER_OF_CREDIT",lc.getId(),lc.getReference());
  return LcSummary.of(lc);
 }

 /** Attaches one document to a file (multipart: file, type, optional documentDate, amount, currency, copyNumber). */
 @PostMapping(value="/lcs/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
 public DocumentView upload(@PathVariable UUID id,@RequestPart("file") MultipartFile file,@RequestParam DocumentType type,
   @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate documentDate,@RequestParam(required=false) BigDecimal amount,
   @RequestParam(required=false) String currency,@RequestParam(required=false) Integer copyNumber,Authentication authentication)throws IOException{
  return uploads.upload(id,file,type,documentDate,amount,currency,copyNumber,authentication);
 }
}
