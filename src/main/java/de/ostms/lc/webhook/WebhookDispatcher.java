package de.ostms.lc.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Sends due deliveries: POST of the JSON event, signed with HMAC-SHA256 over "timestamp.body" (headers X-LCM-Timestamp and X-LCM-Signature),
 * no redirects, short timeouts, retries with growing delay, then DEAD. Targets are checked again before every delivery.
 */
@Service
public class WebhookDispatcher {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(WebhookDispatcher.class);
 private final WebhookSubscriptionRepository subscriptions;private final WebhookDeliveryRepository deliveries;private final WebhookCipher cipher;private final WebhookUrlPolicy policy;private final int maxAttempts;
 private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
 @org.springframework.beans.factory.annotation.Autowired(required=false) private de.ostms.lc.tenant.service.TenantWorkers tenantWorkers;

 public WebhookDispatcher(WebhookSubscriptionRepository subscriptions,WebhookDeliveryRepository deliveries,WebhookCipher cipher,WebhookUrlPolicy policy,@Value("${app.webhooks.max-attempts:8}") int maxAttempts){
  this.subscriptions=subscriptions;this.deliveries=deliveries;this.cipher=cipher;this.policy=policy;this.maxAttempts=Math.max(1,maxAttempts);
 }

 @Scheduled(fixedDelayString="${app.webhooks.interval-ms:5000}")
 public void dispatch(){
  if(tenantWorkers!=null)tenantWorkers.forEach(this::dispatchCurrentTenant);
  else de.ostms.lc.tenant.service.TenantJobRunner.run(de.ostms.lc.tenant.domain.Tenant.homeId(),this::dispatchCurrentTenant);
 }
 @Scheduled(cron="0 30 3 * * *")
 public void purge(){
  Runnable job=()->deliveries.purge(LocalDateTime.now().minusDays(14),LocalDateTime.now().minusDays(60));
  if(tenantWorkers!=null)tenantWorkers.forEach(job);else de.ostms.lc.tenant.service.TenantJobRunner.run(de.ostms.lc.tenant.domain.Tenant.homeId(),job);
 }
 void dispatchCurrentTenant(){for(var delivery:deliveries.due(LocalDateTime.now(),PageRequest.of(0,50)))deliver(delivery);}

 /** One attempt; the result is stored on the delivery and on the subscription. */
 void deliver(WebhookDelivery delivery){
  var sub=subscriptions.findById(delivery.getSubscriptionId()).orElse(null);
  if(sub==null||(!sub.isActive()&&!"Ping".equals(delivery.getEventType()))){delivery.failure(null,sub==null?"Webhook gelöscht.":"Webhook ist inaktiv.",1);deliveries.save(delivery);return;}
  Integer status=null;String error=null;
  try{
   URI uri=policy.parse(sub.getUrl());policy.checkTarget(uri);
   String secret=cipher.decrypt(sub.getId().toString(),sub.getSecretEnc()),timestamp=String.valueOf(System.currentTimeMillis()/1000),body=delivery.getPayload();
   var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("User-Agent","CorporateLCManager-Webhook/1")
     .header("X-LCM-Event",delivery.getEventType()).header("X-LCM-Delivery",delivery.getId().toString()).header("X-LCM-Timestamp",timestamp).header("X-LCM-Signature","sha256="+sign(secret,timestamp,body))
     .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
   status=http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
   if(status<200||status>=300)error="HTTP "+status;
  }catch(InterruptedException e){Thread.currentThread().interrupt();error="Unterbrochen.";}
  catch(java.io.IOException|RuntimeException e){error=e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage());}
  if(error==null){delivery.success(status);sub.delivered("DELIVERED");}
  else{delivery.failure(status,error,maxAttempts);sub.delivered(delivery.getStatus().equals("DEAD")?"DEAD":"RETRYING");log.info("Webhook delivery failed: subscription={} status={} attempt={}",sub.getId(),status,delivery.getAttempts());}
  deliveries.save(delivery);subscriptions.save(sub);
 }
 static String sign(String secret,String timestamp,String body){
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
 }
}
