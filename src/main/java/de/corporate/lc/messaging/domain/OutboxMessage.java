package de.corporate.lc.messaging.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "integration_outbox")
public class OutboxMessage {
    @Column(nullable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore
    private UUID tenantId=de.corporate.lc.tenant.domain.TenantContext.currentId();
    public UUID getTenantId(){return tenantId;}
    @PrePersist @PreUpdate @PreRemove private void validateTenant(){de.corporate.lc.tenant.domain.TenantContext.require(tenantId);}
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 150) private String topic;
    @Column(name = "message_key", length = 255) private String messageKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, length = 20) private String status = "PENDING";
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime publishedAt;
    private LocalDateTime deadLetteredAt;
    @Column(length = 1000) private String lastError;

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public void setTopic(String value) { topic = value; }
    public String getMessageKey() { return messageKey; }
    public void setMessageKey(String value) { messageKey = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime value) { nextAttemptAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public LocalDateTime getDeadLetteredAt() { return deadLetteredAt; }
    public void setDeadLetteredAt(LocalDateTime value) { deadLetteredAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
}
