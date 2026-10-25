package de.ostms.lc.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AuditSnapshotsTest {
 private static final ObjectMapper JSON=new ObjectMapper();
 private LcDocument document(String name){var d=new LcDocument();d.setOriginalFilename(name);d.setDocumentType(DocumentType.COMMERCIAL_INVOICE);d.setContentType("application/pdf");d.setContent(("content of "+name).getBytes());d.setFileSize(20);d.setDocumentDate(LocalDate.of(2026,7,21));d.setAmount(new BigDecimal("66252.52"));d.setCurrency("EUR");d.setExtractedText("SECRET EXTRACTED TEXT");return d;}

 @Test void documentSnapshotHasIdentityAndHashButNeverContentOrText()throws Exception{
  String json=AuditSnapshots.document(document("rechnung.pdf"));
  var node=JSON.readTree(json);
  assertThat(node.get("filename").asText()).isEqualTo("rechnung.pdf");assertThat(node.get("type").asText()).isEqualTo("COMMERCIAL_INVOICE");assertThat(node.get("currency").asText()).isEqualTo("EUR");
  assertThat(node.get("sha256").asText()).hasSize(64).isEqualTo(AuditSnapshots.sha256(("content of rechnung.pdf").getBytes()));
  assertThat(json).doesNotContain("SECRET").doesNotContain("content of");
 }
 @Test void lcSnapshotListsDocumentsAndStaysValidJsonWithinTheColumnLimit()throws Exception{
  var lc=new LetterOfCredit();lc.setReference("LC-1");lc.setApplicant("A".repeat(250));lc.setBeneficiary("B".repeat(250));lc.setRequiredDocuments(new ArrayList<>(List.of("x")));
  var docs=new ArrayList<LcDocument>();for(int i=0;i<30;i++)docs.add(document("d".repeat(200)+i+".pdf"));
  String json=AuditSnapshots.letterOfCredit(lc,docs);
  assertThat(json.length()).isLessThanOrEqualTo(4000);var node=JSON.readTree(json);
  assertThat(node.get("reference").asText()).isEqualTo("LC-1");assertThat(node.get("documentCount").asInt()).isEqualTo(30);
  String small=AuditSnapshots.letterOfCredit(lc,docs.subList(0,2));
  assertThat(JSON.readTree(small).get("documents")).hasSize(2);
 }
 @Test void assignmentSnapshotWorksForUnassigned()throws Exception{
  assertThat(JSON.readTree(AuditSnapshots.assignment(null)).get("assignedTo").isNull()).isTrue();
  assertThat(JSON.readTree(AuditSnapshots.assignment("markus")).get("assignedTo").asText()).isEqualTo("markus");
 }
}
