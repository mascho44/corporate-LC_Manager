package de.ostms.lc.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookTest {
 static final String KEY="0123456789012345678901234567890123456789";
 private final WebhookCipher cipher=new WebhookCipher(KEY);

 @Test void eventsAreMappedFromAuditActions() {
  assertThat(DomainEvent.forAction("DOCUMENT_UPLOADED",true)).contains(DomainEvent.DOCUMENT_UPLOADED);
  assertThat(DomainEvent.forAction("DOCUMENT_UPLOADED",false)).isEmpty();
  assertThat(DomainEvent.forAction("DOCUMENT_CHECK_REPORT_CREATED",true)).contains(DomainEvent.VALIDATION_COMPLETED);
  assertThat(DomainEvent.forAction("EBICS_FETCH_FAILED",false)).contains(DomainEvent.EBICS_ORDER_FAILED);
  assertThat(DomainEvent.forAction("EBICS_KEYS_FAILED",true)).contains(DomainEvent.EBICS_ORDER_FAILED);
  assertThat(DomainEvent.forAction("SLA_RULE_CREATED",true)).isEmpty();
  assertThat(DomainEvent.forAction(null,true)).isEmpty();
  assertThat(DomainEvent.byName("DocumentUploaded")).contains(DomainEvent.DOCUMENT_UPLOADED);
  // an audit action belongs to exactly one event
  var seen=new java.util.HashSet<String>();
  for(var event:DomainEvent.values())for(var action:event.actions())assertThat(seen.add(action)).as(action).isTrue();
 }

 @Test void urlPolicyAllowsOnlyHttpsAndNoInternalTargets() throws Exception {
  var policy=new WebhookUrlPolicy(false);
  assertThat(policy.parse("https://hooks.example.com/lc").getHost()).isEqualTo("hooks.example.com");
  assertThatThrownBy(()->policy.parse("http://hooks.example.com")).hasMessageContaining("https://");
  assertThatThrownBy(()->policy.parse("ftp://hooks.example.com")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->policy.parse("https://user:pw@hooks.example.com")).hasMessageContaining("Zugangsdaten");
  assertThatThrownBy(()->policy.parse("not a url")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->policy.checkTarget(policy.parse("https://127.0.0.1/x"))).hasMessageContaining("intern");
  assertThatThrownBy(()->policy.checkTarget(policy.parse("https://10.1.2.3/x"))).hasMessageContaining("intern");
  assertThatThrownBy(()->policy.checkTarget(policy.parse("https://169.254.169.254/latest"))).hasMessageContaining("intern");
  assertThatThrownBy(()->policy.checkTarget(policy.parse("https://[::1]/x"))).hasMessageContaining("intern");
  assertThat(WebhookUrlPolicy.internal(InetAddress.getByAddress(new byte[]{100,64,1,1}))).isTrue();
  assertThat(WebhookUrlPolicy.internal(InetAddress.getByAddress(new byte[]{8,8,8,8}))).isFalse();
  var open=new WebhookUrlPolicy(true);
  open.checkTarget(open.parse("http://127.0.0.1:9/x"));
 }

 @Test void secretIsEncryptedAndBoundToTheSubscription() {
  byte[] sealed=cipher.encrypt("id-1","whsec_abc");
  assertThat(new String(sealed)).doesNotContain("whsec_abc");
  assertThat(cipher.decrypt("id-1",sealed)).isEqualTo("whsec_abc");
  assertThatThrownBy(()->cipher.decrypt("id-2",sealed)).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->new WebhookCipher("short").encrypt("x","y")).hasMessageContaining("WEBHOOK_ENCRYPTION_KEY");
 }

 @Test void auditEntriesBecomeOneDeliveryPerSubscribedWebhook() {
  var subs=mock(WebhookSubscriptionRepository.class);var deliveries=mock(WebhookDeliveryRepository.class);
  var all=new WebhookSubscription();all.set("alle","https://a.example.com",List.of("*"),true,"t");
  var some=new WebhookSubscription();some.set("nur Upload","https://b.example.com",List.of("DocumentUploaded"),true,"t");
  var other=new WebhookSubscription();other.set("nur Prüfung","https://c.example.com",List.of("ValidationCompleted"),true,"t");
  when(subs.active()).thenReturn(List.of(all,some,other));when(deliveries.save(any())).thenAnswer(i->i.getArgument(0));
  var audit=new AuditEvent();audit.setAction("DOCUMENT_UPLOADED");audit.setEntityType("LETTER_OF_CREDIT");audit.setEntityId("lc-1");audit.setUsername("anna");audit.setSuccessful(true);audit.setDetails("rechnung.pdf");
  try(var scope=TenantContext.open(UUID.randomUUID())){new WebhookEvents(subs,deliveries,new ObjectMapper()).onAudit(audit);}
  var saved=org.mockito.ArgumentCaptor.forClass(WebhookDelivery.class);verify(deliveries,times(2)).save(saved.capture());
  assertThat(saved.getAllValues()).extracting(WebhookDelivery::getSubscriptionId).containsExactlyInAnyOrder(all.getId(),some.getId());
  var first=saved.getAllValues().get(0);assertThat(first.getEventType()).isEqualTo("DocumentUploaded");
  assertThat(first.getPayload()).contains("\"type\":\"DocumentUploaded\"").contains("\"action\":\"DOCUMENT_UPLOADED\"").contains("\"id\":\"lc-1\"").contains("rechnung.pdf");
  assertThat(saved.getAllValues().get(0).getEventId()).isEqualTo(saved.getAllValues().get(1).getEventId());
  // an action without a domain event, or a failed one, creates nothing
  reset(deliveries);audit.setAction("SLA_RULE_CREATED");new WebhookEvents(subs,deliveries,new ObjectMapper()).onAudit(audit);
  audit.setAction("DOCUMENT_UPLOADED");audit.setSuccessful(false);new WebhookEvents(subs,deliveries,new ObjectMapper()).onAudit(audit);
  verifyNoInteractions(deliveries);
 }

 private WebhookDispatcher dispatcher(WebhookSubscriptionRepository subs,WebhookDeliveryRepository deliveries,int maxAttempts){return new WebhookDispatcher(subs,deliveries,cipher,new WebhookUrlPolicy(true),maxAttempts);}
 private WebhookSubscription subscription(String url,boolean active,String secret){
  var sub=new WebhookSubscription();sub.set("Empfänger",url,List.of("*"),active,"t");sub.setSecret(cipher.encrypt(sub.getId().toString(),secret));return sub;
 }

 @Test void deliveryIsSignedAndMarkedDelivered() throws Exception {
  var seen=new ArrayList<Map<String,String>>();var bodies=new ArrayList<String>();
  HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
  server.createContext("/hook",exchange->{
   var headers=new java.util.HashMap<String,String>();exchange.getRequestHeaders().forEach((k,v)->headers.put(k.toLowerCase(),v.get(0)));seen.add(headers);
   bodies.add(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));exchange.sendResponseHeaders(204,-1);exchange.close();
  });
  server.start();
  try{
   var subs=mock(WebhookSubscriptionRepository.class);var deliveries=mock(WebhookDeliveryRepository.class);
   var sub=subscription("http://127.0.0.1:"+server.getAddress().getPort()+"/hook",true,"whsec_geheim");when(subs.findById(sub.getId())).thenReturn(java.util.Optional.of(sub));
   var delivery=new WebhookDelivery(sub.getId(),UUID.randomUUID(),"DocumentUploaded","{\"type\":\"DocumentUploaded\"}");
   dispatcher(subs,deliveries,3).deliver(delivery);
   assertThat(delivery.getStatus()).isEqualTo("DELIVERED");assertThat(delivery.getHttpStatus()).isEqualTo(204);assertThat(delivery.getAttempts()).isEqualTo(1);assertThat(sub.getLastStatus()).isEqualTo("DELIVERED");
   var headers=seen.get(0);
   assertThat(headers).containsEntry("x-lcm-event","DocumentUploaded").containsEntry("x-lcm-delivery",delivery.getId().toString()).containsEntry("content-type","application/json");
   assertThat(headers.get("x-lcm-signature")).isEqualTo("sha256="+WebhookDispatcher.sign("whsec_geheim",headers.get("x-lcm-timestamp"),bodies.get(0)));
   assertThat(bodies.get(0)).isEqualTo("{\"type\":\"DocumentUploaded\"}");
  }finally{server.stop(0);}
 }

 @Test void failuresRetryWithBackoffAndEndDead() throws Exception {
  var calls=new AtomicInteger();
  HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
  server.createContext("/hook",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(500,-1);exchange.close();});
  server.start();
  try{
   var subs=mock(WebhookSubscriptionRepository.class);var deliveries=mock(WebhookDeliveryRepository.class);
   var sub=subscription("http://127.0.0.1:"+server.getAddress().getPort()+"/hook",true,"s");when(subs.findById(sub.getId())).thenReturn(java.util.Optional.of(sub));
   var delivery=new WebhookDelivery(sub.getId(),UUID.randomUUID(),"Ping","{}");var dispatcher=dispatcher(subs,deliveries,2);
   dispatcher.deliver(delivery);
   assertThat(delivery.getStatus()).isEqualTo("PENDING");assertThat(delivery.getAttempts()).isEqualTo(1);assertThat(delivery.getHttpStatus()).isEqualTo(500);assertThat(delivery.getLastError()).isEqualTo("HTTP 500");
   assertThat(delivery.getNextAttemptAt()).isAfter(java.time.LocalDateTime.now().plusSeconds(10));assertThat(sub.getLastStatus()).isEqualTo("RETRYING");
   dispatcher.deliver(delivery);
   assertThat(delivery.getStatus()).isEqualTo("DEAD");assertThat(sub.getLastStatus()).isEqualTo("DEAD");assertThat(calls.get()).isEqualTo(2);
   delivery.retry();assertThat(delivery.getStatus()).isEqualTo("PENDING");assertThat(delivery.getAttempts()).isZero();
  }finally{server.stop(0);}
 }

 @Test void unreachableTargetInactiveOrDeletedWebhookAndInternalTargetsFail() {
  var subs=mock(WebhookSubscriptionRepository.class);var deliveries=mock(WebhookDeliveryRepository.class);
  var unreachable=subscription("http://127.0.0.1:1/hook",true,"s");when(subs.findById(unreachable.getId())).thenReturn(java.util.Optional.of(unreachable));
  var d1=new WebhookDelivery(unreachable.getId(),UUID.randomUUID(),"Ping","{}");dispatcher(subs,deliveries,5).deliver(d1);
  assertThat(d1.getStatus()).isEqualTo("PENDING");assertThat(d1.getLastError()).isNotBlank();
  var inactive=subscription("http://127.0.0.1:1/hook",false,"s");when(subs.findById(inactive.getId())).thenReturn(java.util.Optional.of(inactive));
  var d2=new WebhookDelivery(inactive.getId(),UUID.randomUUID(),"DocumentUploaded","{}");dispatcher(subs,deliveries,5).deliver(d2);
  assertThat(d2.getStatus()).isEqualTo("DEAD");assertThat(d2.getLastError()).contains("inaktiv");
  var d3=new WebhookDelivery(UUID.randomUUID(),UUID.randomUUID(),"Ping","{}");dispatcher(subs,deliveries,5).deliver(d3);
  assertThat(d3.getStatus()).isEqualTo("DEAD");
  // the production policy refuses an internal target at delivery time as well
  var strict=new WebhookDispatcher(subs,deliveries,cipher,new WebhookUrlPolicy(false),5);var internal=subscription("https://127.0.0.1/hook",true,"s");when(subs.findById(internal.getId())).thenReturn(java.util.Optional.of(internal));
  var d4=new WebhookDelivery(internal.getId(),UUID.randomUUID(),"Ping","{}");strict.deliver(d4);
  assertThat(d4.getLastError()).contains("intern");
 }
}
