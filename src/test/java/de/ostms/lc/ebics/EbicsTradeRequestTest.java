package de.ostms.lc.ebics;
import org.junit.jupiter.api.Test;
import org.kopi.ebics.client.EbicsDownloadParams;
import org.kopi.ebics.session.EbicsSession;
import org.kopi.ebics.session.OrderType;
import org.kopi.ebics.xml.DownloadInitializationRequestElement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The client library validates every request against the H005 schema before sending; this test builds the real BTD request offline. */
class EbicsTradeRequestTest {
 private static EbicsSession session()throws Exception{
  var keys=new EbicsClientPortImpl(new EbicsClientFactory()).createKeys(new java.net.URL("https://localhost/ebicsweb"),"EVILSBANK","P1","U1");
  var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
  var pub=(java.security.interfaces.RSAPublicKey)generator.generateKeyPair().getPublic();
  keys.bank().setBankKeys(pub,pub);keys.bank().setDigests("00".repeat(32).getBytes(),"00".repeat(32).getBytes());
  var factory=new EbicsClientFactory();var client=factory.newClient();client.createUserDirectories(keys.user());
  var field=org.kopi.ebics.client.EbicsClient.class.getDeclaredField("configuration");field.setAccessible(true);
  var session=new EbicsSession(keys.user(),(org.kopi.ebics.interfaces.Configuration)field.get(client));
  session.setProduct(new org.kopi.ebics.session.Product("CorporateLCManager","de",null));
  return session;
 }
 private static void validate(EbicsSession session,EbicsDownloadParams params)throws Exception{
  var element=new DownloadInitializationRequestElement(session,OrderType.STA,params);
  element.build();element.validate();
 }
 @Test void tradeRequestsPassTheH005SchemaValidation()throws Exception{
  var session=session();
  for(String type:EbicsMessageService.TYPES)validate(session,EbicsClientPortImpl.tradeParams(type));
 }
 @Test void upperCaseNameWithoutVersionIsRejectedByTheSchema()throws Exception{
  var session=session();
  assertThatThrownBy(()->validate(session,new EbicsDownloadParams("TRC",null,null,"MT700",null,null,null,null))).hasMessageContaining("does not match pattern");
  assertThat(EbicsClientPortImpl.tradeParams("MT700").messageName()).isEqualTo("mt700");
 }
}
