package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.api.DocumentInboxAttachRequest.Metadata;
import de.ostms.lc.tenant.domain.TenantContext;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpatialLayoutTrainingTest {
 OcrEvidence evidence(String reference,String number){return new OcrEvidence("test","positions",200,.8,List.of(new OcrEvidence.Word("Credit",null,1,0,10,50,20),new OcrEvidence.Word("ref:",null,1,60,10,40,20),new OcrEvidence.Word(reference,null,1,120,10,60,20),new OcrEvidence.Word("Bill",null,1,0,70,40,20),new OcrEvidence.Word("number:",null,1,50,70,70,20),new OcrEvidence.Word(number,null,1,140,70,60,20)));}
 @Test void confirmedCustomLabelsLearnFreshValuesAndRemainTenantScoped()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var mapper=new ObjectMapper().findAndRegisterModules();var service=new SpatialLayoutTraining(jdbc,mapper);
  var request=new SpatialLayoutTraining.Confirmation("Synthetic company invoices",new DocumentMetadataTraining.Confirmation(new Metadata("LC123","INV123",null,null),null));
  service.confirm(evidence("LC123","INV123"),request,"tester");
  var captured=org.mockito.ArgumentCaptor.forClass(String.class);
  verify(jdbc).update(anyString(),any(UUID.class),eq(TenantContext.currentId()),eq(request.profile()),captured.capture(),eq("tester"));
  assertThat(captured.getValue()).doesNotContain("LC123","INV123");
  String sql="select distinct labels_json from document_spatial_layout where tenant_id=? and profile=? limit 2";
  when(jdbc.queryForList(sql,String.class,TenantContext.currentId(),request.profile())).thenReturn(List.of(captured.getValue()));
  var result=service.suggest(evidence("LC999","INV999"),request.profile());assertThat(result.get("reference").value()).isEqualTo("LC999");assertThat(result.get("documentNumber").value()).isEqualTo("INV999");
  when(jdbc.queryForList(sql,String.class,TenantContext.currentId(),request.profile())).thenReturn(List.of("one","two"));assertThatThrownBy(()->service.suggest(evidence("LC999","INV999"),request.profile())).isInstanceOf(IllegalArgumentException.class);
 }
}
