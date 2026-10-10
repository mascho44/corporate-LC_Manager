package de.ostms.lc.lc;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.repository.SwiftMessageRepository;
import de.ostms.lc.lc.service.SwiftMessageService;
import de.ostms.lc.tenant.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:swiftmessages;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class SwiftMessageServiceTest {
 @Autowired SwiftMessageRepository messages;@Autowired LetterOfCreditRepository lcs;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 SwiftMessageService service;
 @BeforeEach void setUp(){if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());service=new SwiftMessageService(messages,lcs);}
 LetterOfCredit lc(String reference){var l=new LetterOfCredit();l.setReference(reference);return lcs.saveAndFlush(l);}
 static String raw(String reference,String related){return ":20:"+reference+"\n"+(related==null?"":":21:"+related+"\n")+":79:PLEASE ADVISE STATUS.\n";}

 @Test void linksToTheDossierNamedInTheRelatedReference(){
  var dossier=lc("LC-1");
  var m=service.importMessage("MT799",raw("BANK9","LC-1"),"IMPORT","anna");
  assertThat(m.getLcId()).isEqualTo(dossier.getId());assertThat(m.getRelatedReference()).isEqualTo("LC-1");
  assertThat(service.forLc(dossier.getId())).hasSize(1);assertThat(service.unassigned()).isEmpty();
 }
 @Test void fallsBackToTheOwnReferenceAndOtherwiseStaysUnassigned(){
  var dossier=lc("LC-2");
  assertThat(service.importMessage("MT199",raw("LC-2",null),"IMPORT",null).getLcId()).isEqualTo(dossier.getId());
  var loose=service.importMessage("MT799",raw("UNKNOWN",null),"IMPORT",null);
  assertThat(loose.getLcId()).isNull();assertThat(service.unassigned()).extracting("reference").containsExactly("UNKNOWN");
  var target=lc("LC-3");service.assign(loose.getId(),target.getId());
  assertThat(service.unassigned()).isEmpty();assertThat(service.forLc(target.getId())).hasSize(1);
 }
 @Test void theSameMessageIsStoredOnlyOnce(){
  service.importMessage("MT799",raw("DUP",null),"IMPORT",null);
  assertThat(service.isDuplicate("MT799","DUP",raw("DUP",null))).isTrue();
  assertThatThrownBy(()->service.importMessage("MT799",raw("DUP",null),"IMPORT",null)).hasMessageContaining("bereits importiert");
  assertThat(service.importMessage("MT799",raw("DUP",null)+"EXTRA LINE\n","IMPORT",null)).isNotNull();
 }
 @Test void assigningToAMissingDossierFails(){
  var m=service.importMessage("MT799",raw("A1",null),"IMPORT",null);
  assertThatThrownBy(()->service.assign(m.getId(),java.util.UUID.randomUUID())).isInstanceOf(java.util.NoSuchElementException.class);
 }
}
