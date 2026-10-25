package de.ostms.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SplitTrainingQualityTest {
 private PdfDocumentSplitter.Part part(int from,int to,DocumentType type,Integer copy){return new PdfDocumentSplitter.Part(from,to,type,copy);}
 @Test void measuresTypesAndBoundariesSeparatelyIgnoringCopies(){
  var before=List.of(part(1,2,DocumentType.COMMERCIAL_INVOICE,0),part(3,4,DocumentType.PACKING_LIST,1));
  assertThat(SplitTrainingQuality.compare(before,List.of(part(1,2,DocumentType.COMMERCIAL_INVOICE,-2),part(3,4,DocumentType.PACKING_LIST,3)))).isEqualTo(new SplitTrainingQuality.Comparison(4,0,false));
  assertThat(SplitTrainingQuality.compare(before,List.of(part(1,1,DocumentType.COMMERCIAL_INVOICE,null),part(2,4,DocumentType.PACKING_LIST,null)))).isEqualTo(new SplitTrainingQuality.Comparison(4,1,true));
 }
 @Test void reportIsTenantBoundAndHistoricRowsAreNotMeasured()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var mapper=new ObjectMapper();var tenant=UUID.randomUUID();
  try(var scope=TenantContext.open(tenant)){
   when(jdbc.queryForObject(anyString(),eq(Long.class),eq(tenant))).thenReturn(8L);
   var parts=mapper.writeValueAsString(List.of(part(1,1,DocumentType.COMMERCIAL_INVOICE,null),part(2,2,DocumentType.PACKING_LIST,null)));
   when(jdbc.queryForList(anyString(),eq(tenant))).thenReturn(List.of(Map.of("pattern_hash","a".repeat(64),"parts_json",parts,"proposed_parts_json",parts,"proposed_method","RULE_BASED","confirmed_at","2026-10-08")));
   var report=new SplitTrainingQuality(jdbc,mapper).report();assertThat(report.confirmations()).isEqualTo(8);assertThat(report.measured()).isEqualTo(1);assertThat(report.correctedPages()).isZero();assertThat(report.recent().get(0).pattern()).hasSize(12);assertThat(report.recent().get(0).proposedParts()).isEqualTo(report.recent().get(0).confirmedParts());
   verify(jdbc).queryForList(contains("tenant_id=? and proposed_parts_json is not null"),eq(tenant));
  }
 }
 @Test void noSamplesDoNotClaimAccuracy()throws Exception{
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Long.class),any())).thenReturn(3L);
  var report=new SplitTrainingQuality(jdbc,new ObjectMapper()).report();assertThat(report.measured()).isZero();assertThat(report.recent()).isEmpty();
 }
}
