package de.ostms.lc.ebics;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.tenant.domain.Tenant;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:ebicsmessage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class EbicsMessageServiceTest {
 @Autowired EbicsConnectionRepository connections;@Autowired EbicsMessageRepository messages;@Autowired de.ostms.lc.tenant.repository.TenantRepository tenants;
 final AuditService audit=Mockito.mock(AuditService.class);final SwiftImportService swift=Mockito.mock(SwiftImportService.class);final DocumentCheckService checks=Mockito.mock(DocumentCheckService.class);
 final Map<String,byte[]> bank=new HashMap<>();
 class Port extends EbicsClientPortImpl {
  Port(){super(new EbicsClientFactory());}
  @Override public void sendIni(org.kopi.ebics.client.User u)throws Exception{}
  @Override public void sendHia(org.kopi.ebics.client.User u)throws Exception{}
  @Override public void fetchBankKeys(org.kopi.ebics.client.User u)throws Exception{}
  @Override public byte[] downloadTradeMessage(org.kopi.ebics.client.User u,String type)throws Exception{
   byte[] data=bank.get(type);if(data!=null&&data.length==1&&data[0]=='!')throw new Exception("Wrong returned HTTP code: 500");return data;}
 }
 EbicsMessageService service;EbicsConnectionService connection;
 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  var port=new Port();
  connection=new EbicsConnectionService(connections,port,new EbicsCipher("synthetic-test-key-0123456789abcdef0123"),new EbicsUrlPolicy("localhost"),audit);
  service=new EbicsMessageService(connections,messages,port,connection,swift,audit,checks);
  bank.clear();
 }
 void activate(){
  connection.save(new EbicsConnectionService.Request("https://localhost:8443/ebicsweb","EVILSBANK","P1","U1"),null);
  connection.initialise(null);connection.fetchBankKeys(null);
 }
 static byte[] text(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static final String MT700=":20:LC100\n:31D:261201BERLIN\n:32B:EUR100,\n:59:BENE\n";
 static final String MT710=":20:ADV9\n:21:LC200\n:52A:ISSUER\n:32B:EUR1,\n";

 @Test void requiresAnActiveConnection(){
  assertThatThrownBy(()->service.fetch(null)).isInstanceOf(IllegalStateException.class);
  connection.save(new EbicsConnectionService.Request("https://localhost:8443/ebicsweb","EVILSBANK","P1","U1"),null);
  assertThatThrownBy(()->service.fetch(null)).hasMessageContaining("nicht aktiv");
 }
 @Test void storesNewMessagesOnceAndReportsKnownOnes(){
  activate();bank.put("MT700",text(MT700));bank.put("MT710",text(MT710));
  var first=service.fetch(null);
  assertThat(first.fetched()).isEqualTo(2);assertThat(first.alreadyKnown()).isZero();assertThat(first.errors()).isEmpty();
  var second=service.fetch(null);
  assertThat(second.fetched()).isZero();assertThat(second.alreadyKnown()).isEqualTo(2);
  var list=service.list();
  assertThat(list).extracting(EbicsMessageService.View::reference).containsExactlyInAnyOrder("LC100","LC200");
  assertThat(list).allMatch(EbicsMessageService.View::importable);
 }
 @Test void mt760IsStoredButNotImportable(){
  activate();bank.put("MT760",text(":20:G1\n:27:1/1\n:40C:URDG\n:77C:TERMS\n"));
  service.fetch(null);
  var view=service.list().get(0);
  assertThat(view.messageType()).isEqualTo("MT760");assertThat(view.importable()).isFalse();
  assertThatThrownBy(()->service.importMessage(view.id(),null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void badAnswersAreReportedPerTypeWithoutStoppingTheOthers(){
  activate();bank.put("MT700",new byte[]{'!'});bank.put("MT707",text("no swift here"));bank.put("MT710",text(MT710));
  var r=service.fetch(null);
  assertThat(r.fetched()).isEqualTo(1);assertThat(r.errors()).hasSize(2);
  assertThat(r.errors()).anyMatch(e->e.startsWith("MT700")).anyMatch(e->e.startsWith("MT707"));
 }
 @Test void importHappensOnlyOnRequestAndOnlyOnce(){
  activate();bank.put("MT700",text(MT700));service.fetch(null);
  var id=service.list().get(0).id();
  Mockito.when(swift.execute(any())).thenReturn(new Object());
  service.importMessage(id,null);
  assertThat(service.list().get(0).status()).isEqualTo("IMPORTED");
  assertThatThrownBy(()->service.importMessage(id,null)).isInstanceOf(IllegalStateException.class);
  Mockito.verify(swift,Mockito.times(1)).execute(any());
 }
 @Test void failedImportKeepsTheMessageOpen(){
  activate();bank.put("MT700",text(MT700));service.fetch(null);
  var id=service.list().get(0).id();
  Mockito.when(swift.execute(any())).thenThrow(new IllegalArgumentException("Ein Akkreditiv mit dieser Referenz existiert bereits."));
  assertThatThrownBy(()->service.importMessage(id,null)).hasMessageContaining("existiert bereits");
  assertThat(service.list().get(0).status()).isEqualTo("NEW");
 }
 @Test void discardMarksTheMessage(){
  activate();bank.put("MT700",text(MT700));service.fetch(null);
  var id=service.list().get(0).id();
  assertThat(service.discard(id,null).status()).isEqualTo("DISCARDED");
  assertThat(service.list().get(0).importable()).isFalse();
 }
 @Test void referencePrefersCreditNumberForMt710(){
  assertThat(EbicsMessageService.reference(MT710)).isEqualTo("LC200");
  assertThat(EbicsMessageService.reference(MT700)).isEqualTo("LC100");
 }
}
