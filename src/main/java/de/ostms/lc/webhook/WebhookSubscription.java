package de.ostms.lc.webhook;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** A target URL that receives the chosen domain events, signed with the secret of the subscription. */
@Entity @Table(name="webhook_subscription")
public class WebhookSubscription extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=120) private String name;
 @Column(nullable=false,length=500) private String url;
 @Column(name="secret_enc",nullable=false) private byte[] secretEnc;
 @Column(name="event_types",nullable=false,length=400) private String eventTypes;
 @Column(nullable=false) private boolean active=true;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 @Column(name="last_delivery_at") private LocalDateTime lastDeliveryAt;
 @Column(name="last_status",length=20) private String lastStatus;
 public UUID getId(){return id;} public String getName(){return name;} public String getUrl(){return url;} public byte[] getSecretEnc(){return secretEnc;} public boolean isActive(){return active;}
 public java.util.List<String> eventTypes(){return eventTypes.isBlank()?java.util.List.of():java.util.Arrays.asList(eventTypes.split(","));}
 public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;} public LocalDateTime getLastDeliveryAt(){return lastDeliveryAt;} public String getLastStatus(){return lastStatus;}
 public boolean wants(String eventName){var types=eventTypes();return types.contains("*")||types.contains(eventName);}
 public void set(String name,String url,java.util.List<String> eventTypes,boolean active,String user){this.name=name;this.url=url;this.eventTypes=String.join(",",eventTypes);this.active=active;this.changedBy=user;this.changedAt=LocalDateTime.now();}
 public void setSecret(byte[] sealed){this.secretEnc=sealed;}
 public void delivered(String status){this.lastDeliveryAt=LocalDateTime.now();this.lastStatus=status;}
}
