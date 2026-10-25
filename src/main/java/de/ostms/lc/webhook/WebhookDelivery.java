package de.ostms.lc.webhook;

import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** One event for one subscription, delivered with retries (PENDING, then DELIVERED or DEAD). */
@Entity @Table(name="webhook_delivery")
public class WebhookDelivery extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(name="subscription_id",nullable=false) private UUID subscriptionId;
 @Column(name="event_id",nullable=false) private UUID eventId;
 @Column(name="event_type",nullable=false,length=60) private String eventType;
 @Column(nullable=false,columnDefinition="text") private String payload;
 @Column(nullable=false,length=12) private String status="PENDING";
 @Column(nullable=false) private int attempts;
 @Column(name="next_attempt_at",nullable=false) private LocalDateTime nextAttemptAt=LocalDateTime.now();
 @Column(name="http_status") private Integer httpStatus;
 @Column(name="last_error",length=500) private String lastError;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="delivered_at") private LocalDateTime deliveredAt;
 protected WebhookDelivery(){}
 public WebhookDelivery(UUID subscriptionId,UUID eventId,String eventType,String payload){this.subscriptionId=subscriptionId;this.eventId=eventId;this.eventType=eventType;this.payload=payload;}
 public UUID getId(){return id;} public UUID getSubscriptionId(){return subscriptionId;} public UUID getEventId(){return eventId;} public String getEventType(){return eventType;}
 @com.fasterxml.jackson.annotation.JsonIgnore public String getPayload(){return payload;} public String getStatus(){return status;} public int getAttempts(){return attempts;}
 public LocalDateTime getNextAttemptAt(){return nextAttemptAt;} public Integer getHttpStatus(){return httpStatus;} public String getLastError(){return lastError;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getDeliveredAt(){return deliveredAt;}
 public void success(int http){attempts++;httpStatus=http;status="DELIVERED";deliveredAt=LocalDateTime.now();lastError=null;}
 public void failure(Integer http,String error,int maxAttempts){
  attempts++;httpStatus=http;lastError=error==null?null:error.substring(0,Math.min(500,error.length()));
  if(attempts>=maxAttempts)status="DEAD";else{status="PENDING";nextAttemptAt=LocalDateTime.now().plusSeconds(Math.min(3600,15L<<Math.min(attempts,8)));}
 }
 public void retry(){status="PENDING";attempts=0;nextAttemptAt=LocalDateTime.now();lastError=null;}
}
