package de.ostms.lc.webhook;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Turns audit entries into domain events and queues one delivery per subscribed webhook. Runs inside the transaction of the audit entry. */
@Component
public class WebhookEvents {
 private final WebhookSubscriptionRepository subscriptions;private final WebhookDeliveryRepository deliveries;private final ObjectMapper json;
 public WebhookEvents(WebhookSubscriptionRepository subscriptions,WebhookDeliveryRepository deliveries,ObjectMapper json){this.subscriptions=subscriptions;this.deliveries=deliveries;this.json=json;}

 public void onAudit(AuditEvent audit){
  var event=DomainEvent.forAction(audit.getAction(),audit.isSuccessful());
  if(event.isEmpty())return;
  var targets=subscriptions.active().stream().filter(s->s.wants(event.get().eventName())).toList();
  if(targets.isEmpty())return;
  UUID eventId=UUID.randomUUID();String body=envelope(eventId,event.get().eventName(),audit.getAction(),audit.getEntityType(),audit.getEntityId(),audit.getUsername(),audit.getDetails());
  for(var target:targets)deliveries.save(new WebhookDelivery(target.getId(),eventId,event.get().eventName(),body));
 }
 /** Queues a test event for one subscription only. */
 public WebhookDelivery ping(WebhookSubscription target,String username){
  UUID eventId=UUID.randomUUID();
  return deliveries.save(new WebhookDelivery(target.getId(),eventId,"Ping",envelope(eventId,"Ping","WEBHOOK_PING","WEBHOOK",target.getId().toString(),username,"Testereignis")));
 }
 String envelope(UUID eventId,String type,String action,String entityType,String entityId,String actor,String details){
  var entity=new LinkedHashMap<String,Object>();entity.put("type",entityType);entity.put("id",entityId);
  var body=new LinkedHashMap<String,Object>();
  body.put("id",eventId.toString());body.put("type",type);body.put("occurredAt",Instant.now().toString());body.put("tenantId",String.valueOf(TenantContext.currentId()));
  body.put("action",action);body.put("entity",entity);body.put("actor",actor);body.put("details",details==null?null:(details.length()>500?details.substring(0,500):details));
  try{return json.writeValueAsString(body);}catch(JacksonException e){throw new IllegalStateException("Ereignis konnte nicht serialisiert werden.",e);}
 }
}
