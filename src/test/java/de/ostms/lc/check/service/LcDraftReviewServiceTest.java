package de.ostms.lc.check.service;

import de.ostms.lc.swift.Mt700Parser;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class LcDraftReviewServiceTest {
 final LcDraftReviewService service = new LcDraftReviewService(new Mt700Parser());
 final LocalDate today = LocalDate.of(2026, 10, 28);

 static String draft(String shipment, String expiry, String extra) {
  return ":27:1/1\n:40A:IRREVOCABLE\n:20:LC-DRAFT-1\n:31C:261028\n:31D:" + expiry + "HAMBURG\n:50:IMPORT TRADING LLC\nDUBAI\n:59:BEISPIEL GMBH\nSTUTTGART\n:32B:EUR125000,00\n:41A:BANKDEFFXXX\nBY PAYMENT\n:44C:" + shipment
   + "\n:45A:2 UNITS CNC MACHINE\n:46A:+SIGNED COMMERCIAL INVOICE IN 3 ORIGINALS\n+PACKING LIST\n:47A:" + extra + "\n:48:21 DAYS AFTER SHIPMENT\n";
 }
 static boolean has(LcDraftReviewService.Result r, String code) { return r.findings().stream().anyMatch(f -> f.code().equals(code)); }

 @Test void aCleanDraftHasNoDiscrepancies() {
  var r = service.check(draft("261215", "270115", "ALL DOCUMENTS IN ENGLISH"), today);
  assertThat(r.discrepancies()).isZero();assertThat(r.reference()).isEqualTo("LC-DRAFT-1");assertThat(r.presentationDays()).isEqualTo(21);
 }
 @Test void checksWithoutProblemsAreListedAsMet() {
  var clean = service.check(draft("261215", "270115", "ALL DOCUMENTS IN ENGLISH"), today);
  assertThat(clean.passed()).extracting(LcDraftReviewService.Finding::code).contains("DRAFT_OK_20", "DRAFT_OK_59", "DRAFT_OK_WINDOW", "DRAFT_OK_APPLICANT").allMatch(c -> c.startsWith("DRAFT_OK_"));
  assertThat(clean.passed()).allSatisfy(f -> { assertThat(f.severity()).isEqualTo("OK"); assertThat(f.messageEn()).isNotBlank(); });
  var bad = service.check(draft("261215", "261201", "PAYMENT SUBJECT TO THE APPLICANT'S APPROVAL"), today);
  assertThat(bad.passed()).extracting(LcDraftReviewService.Finding::code).doesNotContain("DRAFT_OK_WINDOW", "DRAFT_OK_APPLICANT").contains("DRAFT_OK_20");
 }
 @Test void expiryBeforeShipmentAndPastDatesAreDiscrepancies() {
  var r = service.check(draft("261215", "261201", ""), today);
  assertThat(has(r, "DRAFT_EXPIRY_BEFORE_SHIPMENT")).isTrue();
  var past = service.check(draft("260101", "260201", ""), today);
  assertThat(has(past, "DRAFT_EXPIRED")).isTrue();assertThat(has(past, "DRAFT_SHIPMENT_PAST")).isTrue();
 }
 @Test void aShortWindowBetweenShipmentAndExpiryIsAWarning() {
  var r = service.check(draft("261215", "261225", ""), today);
  assertThat(has(r, "DRAFT_SHORT_WINDOW")).isTrue();assertThat(has(r, "DRAFT_EXPIRY_BEFORE_SHIPMENT")).isFalse();
 }
 @Test void missingMandatoryFieldsAreReported() {
  var r = service.check(":20:X\n:32B:EUR100,00\n", today);
  assertThat(has(r, "DRAFT_MISSING_59")).isTrue();assertThat(has(r, "DRAFT_MISSING_46A")).isTrue();assertThat(has(r, "DRAFT_MISSING_31D")).isTrue();
  assertThat(r.discrepancies()).isGreaterThanOrEqualTo(5);
 }
 @Test void riskClausesAreFoundInTheFreeText() {
  var r = service.check(draft("261215", "270115", "INSPECTION CERTIFICATE SIGNED BY THE APPLICANT\nPAYMENT TERMS AS PER CONTRACT\nDOCUMENTS TO BE PRESENTED WITHIN 5 DAYS\nCERTIFICATE OF ORIGIN LEGALISED BY THE EMBASSY"), today);
  assertThat(has(r, "DRAFT_APPLICANT_DEPENDENT")).isTrue();assertThat(has(r, "DRAFT_VAGUE")).isTrue();assertThat(has(r, "DRAFT_SHORT_DEADLINE")).isTrue();assertThat(has(r, "DRAFT_LEGALISATION")).isTrue();
  assertThat(r.findings()).allSatisfy(f -> assertThat(f.messageEn()).isNotBlank());
 }
 @Test void revocableAndShortPresentationPeriodAreFlaggedAndNoMessageGivesNothing() {
  var r = service.check(draft("261215", "270115", "").replace(":40A:IRREVOCABLE", ":40A:REVOCABLE").replace("21 DAYS", "5 DAYS"), today);
  assertThat(has(r, "DRAFT_REVOCABLE")).isTrue();assertThat(has(r, "DRAFT_SHORT_PRESENTATION")).isTrue();
  assertThatThrownBy(() -> service.check("Das ist kein Akkreditiv.", today)).hasMessageContaining("MT700");
  assertThatThrownBy(() -> service.check(" ", today)).hasMessageContaining("einfügen");
 }
}
