package de.ostms.lc.check.service;

import de.ostms.lc.swift.Mt700Parser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class LcDraftEvidenceTest {
 static byte[] pdf(String text) throws Exception {
  try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
   var page = new PDPage(); doc.addPage(page);
   try (var cs = new PDPageContentStream(doc, page)) {
    cs.beginText(); cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 10); cs.setLeading(14); cs.newLineAtOffset(40, 780);
    for (String line : text.split("\n")) { cs.showText(line); cs.newLine(); }
    cs.endText();
   }
   doc.save(out); return out.toByteArray();
  }
 }

 @Test void findingsPointToTheLineInThePdf() throws Exception {
  String text = LcDraftReviewServiceTest.draft("261215", "261201", "PAYMENT SUBJECT TO THE APPLICANT'S APPROVAL OF THE GOODS");
  var result = new LcDraftReviewService(new Mt700Parser()).check(text, LocalDate.of(2026, 10, 28));
  var withEvidence = LcDraftEvidence.attach(result, text, pdf(text));
  var expiry = withEvidence.findings().stream().filter(f -> f.code().equals("DRAFT_EXPIRY_BEFORE_SHIPMENT")).findFirst().orElseThrow();
  assertThat(expiry.evidence()).isNotEmpty();
  assertThat(expiry.evidence().get(0).page()).isEqualTo(1);
  assertThat(expiry.evidence().get(0).quote()).contains("31D").contains("261201");
  assertThat(expiry.evidence().get(0).w()).isGreaterThan(0.1);
  assertThat(withEvidence.pages()).hasSize(1);
  assertThat(withEvidence.pages().get(0).image()).startsWith("data:image/jpeg;base64,");
  var clause = withEvidence.findings().stream().filter(f -> f.code().equals("DRAFT_APPLICANT_DEPENDENT")).findFirst().orElseThrow();
  assertThat(clause.evidence()).isNotEmpty();
  assertThat(clause.evidence().get(0).quote()).containsIgnoringCase("APPLICANT'S APPROVAL");
 }

 @Test void aFileWithoutTextLayerOrWithUnrelatedTextKeepsTheResult() throws Exception {
  String text = LcDraftReviewServiceTest.draft("261215", "261201", "");
  var result = new LcDraftReviewService(new Mt700Parser()).check(text, LocalDate.of(2026, 10, 28));
  assertThat(LcDraftEvidence.attach(result, text, pdf("NOTHING RELATED HERE")).findings()).allSatisfy(f -> assertThat(f.evidence()).isEmpty());
  assertThat(LcDraftEvidence.attach(result, text, new byte[] {1, 2, 3})).isSameAs(result);
 }
}
