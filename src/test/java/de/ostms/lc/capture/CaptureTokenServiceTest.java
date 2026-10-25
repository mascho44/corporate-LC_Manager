package de.ostms.lc.capture;

import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:capturetokens;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class CaptureTokenServiceTest {
 @Autowired CaptureTokenRepository repository;@Autowired TenantRepository tenants;
 CaptureTokenService service;Tenant other;

 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  other=tenants.saveAndFlush(new Tenant("poststelle-"+UUID.randomUUID().toString().substring(0,8),"Andere GmbH","de",false,true));
  service=new CaptureTokenService(repository,tenants);
 }

 @Test void secretIsShownOnceAndOnlyItsHashIsStored(){
  var created=service.create("Poststelle Scanner 2","admin");
  assertThat(created.secret()).startsWith("lcc_").hasSizeGreaterThan(40);
  assertThat(created.token().prefix()).isEqualTo(created.secret().substring(0,8));
  var stored=repository.findById(created.token().id()).orElseThrow();
  assertThat(repository.findByTokenHash(created.secret())).isEmpty();
  assertThat(repository.findByTokenHash(CaptureTokenService.hash(created.secret()))).contains(stored);
  assertThat(service.list()).extracting(CaptureTokenService.View::name).containsExactly("Poststelle Scanner 2");
 }

 @Test void authenticationAcceptsValidTokensOnlyAndNotesTheUse(){
  var created=service.create("Scanner","admin");
  var identity=service.authenticate(created.secret()).orElseThrow();
  assertThat(identity.tenantId()).isEqualTo(Tenant.DEFAULT_ID);assertThat(identity.name()).isEqualTo("Scanner");
  assertThat(repository.findById(created.token().id()).orElseThrow().getLastUsedAt()).isNotNull();
  assertThat(service.authenticate(created.secret()+"x")).isEmpty();
  assertThat(service.authenticate("lcc_unbekannt")).isEmpty();
  assertThat(service.authenticate("kein-token")).isEmpty();
  assertThat(service.authenticate(null)).isEmpty();
 }

 @Test void revokedTokensAndSuspendedTenantsAreRefused(){
  var created=service.create("Scanner","admin");
  service.revoke(created.token().id());
  assertThat(service.authenticate(created.secret())).isEmpty();
  assertThat(service.list().get(0).revoked()).isTrue();
  try(var scope=TenantContext.open(other.getId())){
   var theirs=service.create("Anderer Scanner","admin");
   assertThat(service.authenticate(theirs.secret())).isPresent();
   var tenant=tenants.findById(other.getId()).orElseThrow();tenant.setActive(false);tenants.saveAndFlush(tenant);
   assertThat(service.authenticate(theirs.secret())).isEmpty();
  }
 }

 @Test void tokensBelongToTheirTenantAndTheNumberIsLimited(){
  var mine=service.create("Meiner","admin");
  try(var scope=TenantContext.open(other.getId())){
   assertThat(service.list()).isEmpty();
   assertThatThrownBy(()->service.revoke(mine.token().id())).isInstanceOf(NoSuchElementException.class);
   assertThat(service.authenticate(mine.secret()).orElseThrow().tenantId()).isEqualTo(Tenant.DEFAULT_ID);
  }
  for(int i=1;i<CaptureTokenService.MAX_ACTIVE;i++)service.create("Gerät "+i,"admin");
  assertThatThrownBy(()->service.create("Zu viel","admin")).hasMessageContaining("bereits");
  assertThatThrownBy(()->service.create("  ","admin")).hasMessageContaining("Namen");
 }
 @Test void stationsAreSilentAfterAFullWorkingDayWithoutDeliveryWeekendsNotCounted(){
  var friday=java.time.LocalDateTime.of(2026,10,9,16,0);
  assertThat(CaptureTokenService.workingDaysSince(friday,java.time.LocalDate.of(2026,10,9))).isZero();
  assertThat(CaptureTokenService.workingDaysSince(friday,java.time.LocalDate.of(2026,10,12))).isEqualTo(1);
  assertThat(CaptureTokenService.workingDaysSince(friday,java.time.LocalDate.of(2026,10,13))).isEqualTo(2);
  var created=service.create("Scanner","admin");
  assertThat(service.silent(java.time.LocalDate.now())).isEmpty();
  var later=java.time.LocalDate.now().plusDays(10);
  assertThat(service.silent(later)).extracting(CaptureTokenService.Silent::name).containsExactly("Scanner");
  assertThat(service.silent(later).get(0).neverUsed()).isTrue();
  service.revoke(created.token().id());
  assertThat(service.silent(later)).isEmpty();
 }
}
