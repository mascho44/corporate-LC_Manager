package de.ostms.lc.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SplitTrainingServiceTest {
 private static final String FIRST="Unusual shipment document customer reference 12345 description of goods and detailed quantities";
 private static final String SECOND="Unusual continuation document customer reference 12345 description of packages and weights";
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final ObjectMapper json=new ObjectMapper();
 private final SplitTrainingService service=new SplitTrainingService(jdbc,json);
 private final List<PdfDocumentSplitter.Part> parts=List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.COMMERCIAL_INVOICE),new PdfDocumentSplitter.Part(2,2,DocumentType.PACKING_LIST));
 @Test void confirmedTemplatesAreReusedOnlyAsReviewAndScopedToTenant()throws Exception{
  var tenant=UUID.randomUUID();try(var scope=TenantContext.open(tenant)){
   byte[] pdf=PdfDocumentSplitterTest.pdf(FIRST,SECOND);String hash=PdfDocumentSplitter.trainingPattern(pdf,null);
   when(jdbc.queryForList(anyString(),eq(String.class),eq(tenant),eq(hash))).thenReturn(List.of(json.writeValueAsString(parts)));
   var suggestion=service.suggest(pdf,null,PdfDocumentSplitter.propose(pdf,null));
   assertThat(suggestion.parts()).isEqualTo(parts);
   assertThat(suggestion.pages()).allMatch(p->p.classification().status().equals("REVIEW")&&p.classification().method().equals("CONFIRMED_SPLIT_PATTERN_V1"));
   assertThat(InboxAutomaticSplitter.eligible(suggestion)).isFalse();
   verify(jdbc).queryForList(anyString(),eq(String.class),eq(tenant),eq(hash));
  }
 }
 @Test void conflictingExamplesAndUnseenPatternsLeaveBaselineUnchanged()throws Exception{
  byte[] pdf=PdfDocumentSplitterTest.pdf(FIRST,SECOND);var baseline=PdfDocumentSplitter.propose(pdf,null);
  when(jdbc.queryForList(anyString(),eq(String.class),any(),any())).thenReturn(List.of("one","two"));
  assertThat(service.suggest(pdf,null,baseline)).isSameAs(baseline);
  when(jdbc.queryForList(anyString(),eq(String.class),any(),any())).thenReturn(List.of());
  assertThat(service.suggest(pdf,null,baseline)).isSameAs(baseline);
 }
 @Test void patternIgnoresNumbersButNotPageOrderOrWordingAndRejectsEmptyPages()throws Exception{
  String hash=PdfDocumentSplitter.trainingPattern(PdfDocumentSplitterTest.pdf(FIRST,SECOND),null);
  assertThat(PdfDocumentSplitter.trainingPattern(PdfDocumentSplitterTest.pdf(FIRST.replace("12345","987"),SECOND.replace("12345","987")),null)).isEqualTo(hash);
  assertThat(PdfDocumentSplitter.trainingPattern(PdfDocumentSplitterTest.pdf(SECOND,FIRST),null)).isNotEqualTo(hash);
  assertThat(PdfDocumentSplitter.trainingPattern(PdfDocumentSplitterTest.pdf(FIRST+" changed wording",SECOND),null)).isNotEqualTo(hash);
  assertThat(PdfDocumentSplitter.trainingPattern(PdfDocumentSplitterTest.pdf(FIRST,""),null)).isNull();
 }
 @Test void confirmationStoresMetadataAndNoDocumentBytesOrRecognizedText()throws Exception{
  service.confirm(PdfDocumentSplitterTest.pdf(FIRST,SECOND),null,parts,"reviewer");
  verify(jdbc).update(anyString(),any(UUID.class),eq(TenantContext.currentId()),matches("[a-f0-9]{64}"),eq(json.writeValueAsString(parts)),eq("reviewer"),anyString(),eq("CONFIRM_TIME_RULE_BASED"));
 }
}
