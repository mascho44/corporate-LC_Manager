package de.ostms.lc.ebics;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:ebicsconnection;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class EbicsConnectionServiceTest {
 @Autowired EbicsConnectionRepository connections;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 final AuditService audit=Mockito.mock(AuditService.class);
 /** Real key generation (no network); INI/HIA/HPB are recorded instead of sent. */
 static class RecordingPort extends EbicsClientPortImpl {
  final java.util.List<String> calls=new java.util.ArrayList<>();boolean failHia;
  RecordingPort(){super(new EbicsClientFactory());}
  @Override public void sendIni(org.kopi.ebics.client.User u)throws Exception{calls.add("INI");}
  @Override public void sendHia(org.kopi.ebics.client.User u){calls.add("HIA");if(failHia)throw new IllegalStateException("bank unreachable");}
  @Override public void fetchBankKeys(org.kopi.ebics.client.User u){calls.add("HPB");}
 }
 RecordingPort port=new RecordingPort();
 EbicsConnectionService service(){return new EbicsConnectionService(connections,port,new EbicsCipher("synthetic-test-key-0123456789abcdef0123"),new EbicsUrlPolicy("localhost"),audit);}
 @BeforeEach void tenant(){if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());}
 EbicsConnectionService.Request request(){return new EbicsConnectionService.Request("https://localhost:8443/ebicsweb","EVILSBANK","PARTNER1","USER1");}

 @Test void setupRunsThroughIniHiaAndHpbWithEncryptedKeys(){
  var service=service();
  assertThat(service.view().configured()).isFalse();
  service.save(request(),null);
  var prints=service.initialise(null);
  assertThat(prints.a005()).hasSize(95);
  var c=connections.findCurrent().orElseThrow();
  assertThat(c.getStatus()).isEqualTo(EbicsStatus.KEYS_SENT);
  assertThat(c.getUserBlob()).isNotEmpty();
  assertThat(new String(c.getUserBlob(),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("USER1");
  assertThat(port.calls).containsExactly("INI","HIA");
  assertThat(service.fingerprints()).isEqualTo(prints);
  service.fetchBankKeys(null);
  assertThat(connections.findCurrent().orElseThrow().getStatus()).isEqualTo(EbicsStatus.ACTIVE);
  assertThat(port.calls).containsExactly("INI","HIA","HPB");
  assertThatThrownBy(()->service.initialise(null)).isInstanceOf(IllegalStateException.class);
  Mockito.verify(audit,Mockito.atLeast(3)).recordInTransaction(Mockito.any(),Mockito.anyString(),Mockito.anyString(),Mockito.any(),Mockito.anyString());
 }
 @Test void connectionDetailsAreLockedOnceKeysExistUntilReset(){
  var service=service();service.save(request(),null);service.initialise(null);
  var changed=new EbicsConnectionService.Request("https://localhost:8443/ebicsweb","OTHERBANK","PARTNER1","USER1");
  assertThatThrownBy(()->service.save(changed,null)).isInstanceOf(IllegalStateException.class);
  service.reset(null);
  assertThat(connections.findCurrent().orElseThrow().getUserBlob()).isNull();
  assertThat(service.save(changed,null).hostId()).isEqualTo("OTHERBANK");
 }
 @Test void bankKeysRequireSentKeys(){
  var service=service();service.save(request(),null);
  assertThatThrownBy(()->service.fetchBankKeys(null)).isInstanceOf(IllegalStateException.class);
 }
 @Test void rejectsBadIdentifiersAndUrls(){
  var service=service();
  assertThatThrownBy(()->service.save(new EbicsConnectionService.Request("https://localhost/x","BAD ID","P","U"),null)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.save(new EbicsConnectionService.Request("http://localhost/x","H","P","U"),null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void failedSetupAllowsCorrectingTheDetailsAndShowsAUsefulMessage(){
  port.failHia=false;
  var failing=new RecordingPort(){@Override public void sendIni(org.kopi.ebics.client.User u)throws Exception{throw new Exception("Wrong returned HTTP code: 302");}};
  var service=new EbicsConnectionService(connections,failing,new EbicsCipher("synthetic-test-key-0123456789abcdef0123"),new EbicsUrlPolicy("localhost"),audit);
  service.save(new EbicsConnectionService.Request("https://localhost:8443/ebicswwb","EVILSBANK","P1","U1"),null);
  assertThatThrownBy(()->service.initialise(null)).hasMessageContaining("leitet um").hasMessageContaining("ebicsweb");
  assertThat(connections.findCurrent().orElseThrow().getStatus()).isEqualTo(EbicsStatus.ERROR);
  var fixed=service.save(new EbicsConnectionService.Request("https://localhost:8443/ebicsweb","EVILSBANK","P1","U1"),null);
  assertThat(fixed.status()).isEqualTo("NEW");assertThat(fixed.lastError()).isNull();
  assertThat(connections.findCurrent().orElseThrow().getUserBlob()).isNull();
 }
}
