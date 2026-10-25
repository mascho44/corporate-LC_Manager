package de.ostms.lc.audit.service;

import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:auditsearch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class AuditSearchServiceTest {
 @Autowired TenantRepository tenants;@Autowired EntityManager em;
 private void insert(UUID tenant,String user,String action,String entityId,LocalDateTime at){
  em.createNativeQuery("insert into audit_event(id,username,action,entity_id,successful,occurred_at,tenant_id) values(:id,:u,:a,:e,true,:at,:t)")
   .setParameter("id",UUID.randomUUID()).setParameter("u",user).setParameter("a",action).setParameter("e",entityId).setParameter("at",at).setParameter("t",tenant).executeUpdate();
 }
 private AuditSearchService service(int years){var s=new AuditSearchService(years);ReflectionTestUtils.setField(s,"em",em);return s;}

 @Test void filtersByPeriodUserActionAndObjectWithinTheTenantOnly(){
  tenants.saveAndFlush(new Tenant());var other=new Tenant("other","Other","en",true,true);tenants.saveAndFlush(other);
  var home=Tenant.DEFAULT_ID;
  insert(home,"Markus","DOCUMENT_DELETED","d1",LocalDateTime.of(2026,10,1,10,0));
  insert(home,"anna","LC_UPDATED","lc1",LocalDateTime.of(2026,10,5,10,0));
  insert(home,"Markus.Test","LC_UPDATED","lc1",LocalDateTime.of(2026,10,9,23,30));
  insert(other.getId(),"markus","LC_UPDATED","lc1",LocalDateTime.of(2026,10,5,10,0));
  em.flush();em.clear();
  var s=service(10);
  assertThat(s.search(new AuditSearchService.Filter(null,null,null,null,null),200)).hasSize(3);
  assertThat(s.search(new AuditSearchService.Filter(LocalDate.of(2026,10,2),LocalDate.of(2026,10,9),null,null,null),200)).hasSize(2); // end date inclusive until 23:59
  assertThat(s.search(new AuditSearchService.Filter(null,null,"MARKUS",null,null),200)).extracting("username").containsExactly("Markus.Test","Markus");
  assertThat(s.search(new AuditSearchService.Filter(null,null,null,"lc_upd",null),200)).hasSize(2);
  assertThat(s.search(new AuditSearchService.Filter(null,null,null,null,"d1"),200)).hasSize(1);
  assertThat(s.search(new AuditSearchService.Filter(null,null,"100%",null,null),200)).isEmpty(); // wildcard characters are literal
  assertThat(s.search(new AuditSearchService.Filter(null,null,null,null,null),2)).hasSize(2);
  try(var scope=TenantContext.open(other.getId())){assertThat(s.search(new AuditSearchService.Filter(null,null,null,null,null),200)).extracting("username").containsExactly("markus");}
 }
 @Test void invalidPeriodIsRejected(){
  assertThatThrownBy(()->new AuditSearchService.Filter(LocalDate.of(2026,10,9),LocalDate.of(2026,10,1),null,null,null)).isInstanceOf(IllegalArgumentException.class);
  assertThat(new AuditSearchService.Filter(LocalDate.of(2026,1,1),null,"anna",null,null).describe()).isEqualTo("von 2026-01-01, Benutzer anna");
 }
 @Test void retentionCountsRowsBeyondTheFrameAndNeverDeletes(){
  tenants.saveAndFlush(new Tenant());var home=Tenant.DEFAULT_ID;
  insert(home,"old","LC_UPDATED","x",LocalDateTime.now().minusYears(11));
  insert(home,"older","LC_UPDATED","x",LocalDateTime.now().minusYears(12));
  insert(home,"new","LC_UPDATED","x",LocalDateTime.now().minusDays(1));
  em.flush();em.clear();
  var retention=service(10).retention();
  assertThat(retention.retentionYears()).isEqualTo(10);assertThat(retention.eventsBeyondRetention()).isEqualTo(2);assertThat(retention.automaticDeletion()).isFalse();
  assertThat(retention.oldestEvent()).isBefore(LocalDateTime.now().minusYears(11));
  assertThat(service(1000).retention().retentionYears()).isEqualTo(100);assertThat(service(0).retention().retentionYears()).isEqualTo(1);
  assertThat(service(10).search(new AuditSearchService.Filter(null,null,null,null,null),200)).hasSize(3);
 }
}
