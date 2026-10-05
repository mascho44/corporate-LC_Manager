package de.corporate.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:packtransaction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({InternalPackService.class,PackCodec.class,PackTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PackTransactionTest {
 @TestConfiguration static class Beans {
  @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
  @Bean AuditService audit(){return mock(AuditService.class);}
 }
 @Autowired InternalPackService service;@Autowired PackVersionRepository versions;
 @Autowired PackSelectionRepository selections;@Autowired AuditService audit;
 @Test void failedAuditRollsBackImportAndActivation()throws Exception{
  var auth=new UsernamePasswordAuthenticationToken("synthetic-admin","unused");
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("Synthetic audit failure")).when(target).recordInTransaction(any(),eq("LC_RULE_PACK_IMPORTED"),anyString(),any(),anyString());
  assertThatThrownBy(()->service.importPack(PackCodecTest.example(),auth)).isInstanceOf(IllegalStateException.class);
  assertThat(versions.count()).isZero();assertThat(selections.count()).isZero();
  reset(target);var v=service.importPack(PackCodecTest.example(),auth);
  doThrow(new IllegalStateException("Synthetic audit failure")).when(target).recordInTransaction(any(),eq("LC_RULE_PACK_ACTIVATED"),anyString(),any(),anyString());
  assertThatThrownBy(()->service.activate(v.id,true,auth)).isInstanceOf(IllegalStateException.class);
  assertThat(selections.findById(v.packId).orElseThrow().activeVersionId).isNull();
  reset(target);service.activate(v.id,true,auth);
  assertThat(selections.findById(v.packId).orElseThrow().activeVersionId).isEqualTo(v.id);
  service.deactivate(v.packId,auth);assertThat(selections.findById(v.packId).orElseThrow().previousVersionId).isEqualTo(v.id);
 }
}
