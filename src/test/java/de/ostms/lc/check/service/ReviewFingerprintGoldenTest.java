package de.ostms.lc.check.service;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

class ReviewFingerprintGoldenTest {
 static LetterOfCredit lc(){
  var lc=new LetterOfCredit();lc.setReference("LC-1");lc.setApplicant("Applicant GmbH");lc.setBeneficiary("Beneficiary Ltd");lc.setIssuingBank("Bank AG");
  lc.setAmount(new BigDecimal("1000.50"));lc.setCurrency("EUR");lc.setIssueDate(LocalDate.of(2026,10,1));lc.setExpiryDate(LocalDate.of(2027,1,31));
  ReflectionTestUtils.setField(lc,"receivedAt",LocalDateTime.of(2026,10,1,9,0,0));
  return lc;
 }
 static LcDocument doc(){
  var d=new LcDocument();d.setDocumentType(DocumentType.COMMERCIAL_INVOICE);d.setOriginalFilename("a.pdf");d.setContentType("application/pdf");
  d.setAmount(new BigDecimal("1000.50"));d.setCurrency("EUR");d.setContent(new byte[]{1,2,3});d.setExtractedText("text");
  ReflectionTestUtils.setField(d,"uploadedAt",LocalDateTime.of(2026,10,1,10,30,15));
  return d;
 }
 /** The review decisions of the past are bound to these hashes: the JSON they are made from must not change when the JSON library does. Values computed with the Jackson 2 code of 0.7.1. */
 @Test void theFingerprintsStayTheSameAcrossJacksonVersions(){
  org.junit.jupiter.api.Assertions.assertEquals("f44fd871388b70748776774fd1c31f48f3f3daa4be7cd60f775c98d0dbb3f6db",ReviewInputFingerprint.scoped(lc(),List.of(doc()),"a.pdf"));
  org.junit.jupiter.api.Assertions.assertEquals("0359b681ed4bc50976f9f738aa0174bd092303f8cb4e7c50830bc0168852d452",ReviewInputFingerprint.scoped(lc(),List.of(doc()),null));
  org.junit.jupiter.api.Assertions.assertEquals("06646d1aaf85fae6efc4d8bbb37e692cae5571d97b71028a8440f18700fdc96a",ReviewInputFingerprint.of(lc(),List.of(doc())));
 }
}
