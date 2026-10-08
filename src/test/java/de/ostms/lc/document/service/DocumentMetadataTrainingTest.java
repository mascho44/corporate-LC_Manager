package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.document.api.DocumentInboxAttachRequest.Metadata;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class DocumentMetadataTrainingTest {
 @Test void replayIsTenantBoundAndConflictsNeverChooseAWinner()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var mapper=new ObjectMapper().findAndRegisterModules();var service=new DocumentMetadataTraining(jdbc,mapper);
  var value=new DocumentMetadataTraining.Confirmation(new Metadata("LC123",null,null,null),null);String encoded=mapper.writeValueAsString(value),hash=DocumentMetadataTraining.hash("text");
  String sql="select distinct confirmed_json from document_metadata_training where tenant_id=? and text_hash=? limit 2";
  when(jdbc.queryForList(sql,String.class,TenantContext.currentId(),hash)).thenReturn(List.of(encoded));
  assertThat(service.suggest("text").values()).isEqualTo(value);
  verify(jdbc).queryForList(sql,String.class,TenantContext.currentId(),hash);
  when(jdbc.queryForList(sql,String.class,TenantContext.currentId(),hash)).thenReturn(List.of(encoded,"conflicting"));
  assertThat(service.suggest("text").status()).isEqualTo("CONFLICT");assertThat(service.suggest("text").values()).isNull();
 }
 @Test void differentDocumentValuesDoNotReuseDatesOrAmounts(){
  assertThat(DocumentMetadataTraining.hash("Invoice amount 100")).isNotEqualTo(DocumentMetadataTraining.hash("Invoice amount 200"));
  assertThatThrownBy(()->DocumentMetadataTraining.hash(" ")).isInstanceOf(IllegalArgumentException.class);
  var service=new DocumentMetadataTraining(mock(JdbcTemplate.class),new ObjectMapper());assertThat(service.suggest(null).status()).isEqualTo("NO_TEXT");
 }
}
