package de.ostms.lc.webhook;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** Webhook subscriptions of a tenant: manage, rotate the secret, send a test event, look at deliveries. */
@Service
public class WebhookService {
 public record View(UUID id,String name,String url,List<String> eventTypes,boolean active,LocalDateTime lastDeliveryAt,String lastStatus){}
 public record Request(String name,String url,List<String> eventTypes,Boolean active){}
 /** The secret is shown exactly once, here. */
 public record Created(View webhook,String secret){}
 public record EventInfo(String name,String german,String english){}
 private final WebhookSubscriptionRepository subscriptions;private final WebhookDeliveryRepository deliveries;private final WebhookCipher cipher;private final WebhookUrlPolicy policy;private final WebhookEvents events;private final AuditService audit;
 private final SecureRandom random=new SecureRandom();
 public WebhookService(WebhookSubscriptionRepository subscriptions,WebhookDeliveryRepository deliveries,WebhookCipher cipher,WebhookUrlPolicy policy,WebhookEvents events,AuditService audit){
  this.subscriptions=subscriptions;this.deliveries=deliveries;this.cipher=cipher;this.policy=policy;this.events=events;this.audit=audit;
 }

 public List<EventInfo> catalog(){return Arrays.stream(DomainEvent.values()).map(e->new EventInfo(e.eventName(),e.german(),e.english())).toList();}
 @Transactional(readOnly=true) public List<View> list(){return subscriptions.findAll().stream().map(WebhookService::view).toList();}

 @Transactional public Created create(Request r,Authentication auth){
  var clean=validate(r,null);
  var sub=new WebhookSubscription();String secret=newSecret();
  sub.set(clean.name(),clean.url(),clean.eventTypes(),clean.active()==null||clean.active(),auth==null?null:auth.getName());sub.setSecret(cipher.encrypt(sub.getId().toString(),secret));
  subscriptions.save(sub);audit.recordInTransaction(auth,"WEBHOOK_CREATED","WEBHOOK",sub.getId(),sub.getName()+" · "+host(sub.getUrl()));
  return new Created(view(sub),secret);
 }
 @Transactional public View update(UUID id,Request r,Authentication auth){
  var sub=subscriptions.findById(id).orElseThrow(()->new NoSuchElementException("Webhook nicht gefunden."));
  var clean=validate(r,id);sub.set(clean.name(),clean.url(),clean.eventTypes(),clean.active()==null||clean.active(),auth==null?null:auth.getName());
  subscriptions.save(sub);audit.recordInTransaction(auth,"WEBHOOK_UPDATED","WEBHOOK",id,sub.getName()+" · "+host(sub.getUrl())+(sub.isActive()?"":" · inaktiv"));return view(sub);
 }
 @Transactional public Created rotateSecret(UUID id,Authentication auth){
  var sub=subscriptions.findById(id).orElseThrow(()->new NoSuchElementException("Webhook nicht gefunden."));
  String secret=newSecret();sub.setSecret(cipher.encrypt(id.toString(),secret));subscriptions.save(sub);
  audit.recordInTransaction(auth,"WEBHOOK_SECRET_ROTATED","WEBHOOK",id,sub.getName());return new Created(view(sub),secret);
 }
 @Transactional public void delete(UUID id,Authentication auth){
  var sub=subscriptions.findById(id).orElseThrow(()->new NoSuchElementException("Webhook nicht gefunden."));
  deliveries.deleteOf(id);subscriptions.delete(sub);audit.recordInTransaction(auth,"WEBHOOK_DELETED","WEBHOOK",id,sub.getName());
 }
 /** Queues a Ping event; the dispatcher delivers it like any other event. */
 @Transactional public WebhookDelivery test(UUID id,Authentication auth){
  var sub=subscriptions.findById(id).orElseThrow(()->new NoSuchElementException("Webhook nicht gefunden."));
  var delivery=events.ping(sub,auth==null?"unbekannt":auth.getName());
  audit.recordInTransaction(auth,"WEBHOOK_TESTED","WEBHOOK",id,sub.getName());return delivery;
 }
 @Transactional(readOnly=true) public List<WebhookDelivery> deliveries(UUID id){
  subscriptions.findById(id).orElseThrow(()->new NoSuchElementException("Webhook nicht gefunden."));
  return deliveries.recent(id,PageRequest.of(0,30));
 }
 @Transactional public WebhookDelivery retry(UUID deliveryId,Authentication auth){
  var delivery=deliveries.findById(deliveryId).orElseThrow(()->new NoSuchElementException("Zustellung nicht gefunden."));
  if(!"DEAD".equals(delivery.getStatus()))throw new IllegalStateException("Nur endgültig fehlgeschlagene Zustellungen können erneut gestartet werden.");
  delivery.retry();audit.recordInTransaction(auth,"WEBHOOK_DELIVERY_RETRIED","WEBHOOK",delivery.getSubscriptionId(),delivery.getEventType());return deliveries.save(delivery);
 }

 private Request validate(Request r,UUID selfId){
  if(r==null||r.name()==null||r.name().isBlank()||r.name().trim().length()>120)throw new IllegalArgumentException("Bitte einen Namen (höchstens 120 Zeichen) angeben.");
  var uri=policy.parse(r.url());policy.checkTarget(uri);
  var types=new LinkedHashSet<String>();
  for(String t:r.eventTypes()==null?List.<String>of():r.eventTypes()){String v=t==null?"":t.trim();if(v.isEmpty())continue;if(!v.equals("*")&&DomainEvent.byName(v).isEmpty())throw new IllegalArgumentException("Unbekannter Ereignistyp.");types.add(v);}
  if(types.isEmpty())throw new IllegalArgumentException("Bitte mindestens einen Ereignistyp wählen.");
  if(types.contains("*"))types=new LinkedHashSet<>(List.of("*"));
  String name=r.name().trim();
  subscriptions.findAll().stream().filter(s->s.getName().equalsIgnoreCase(name)&&!s.getId().equals(selfId)).findAny().ifPresent(s->{throw new IllegalArgumentException("Es gibt schon einen Webhook mit diesem Namen.");});
  return new Request(name,uri.toString(),List.copyOf(types),r.active());
 }
 private String newSecret(){byte[] b=new byte[32];random.nextBytes(b);return "whsec_"+Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static String host(String url){try{return java.net.URI.create(url).getHost();}catch(RuntimeException e){return "?";}}
 static View view(WebhookSubscription s){return new View(s.getId(),s.getName(),s.getUrl(),s.eventTypes(),s.isActive(),s.getLastDeliveryAt(),s.getLastStatus());}
}
