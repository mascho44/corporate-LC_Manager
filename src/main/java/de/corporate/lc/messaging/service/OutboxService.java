package de.corporate.lc.messaging.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.messaging.domain.OutboxMessage;
import de.corporate.lc.messaging.repository.OutboxMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxMessageRepository repo;
    private final ObjectMapper json;
    private final MessagePublisher publisher;
    private final int maxAttempts;

    public OutboxService(OutboxMessageRepository repo, ObjectMapper json, MessagePublisher publisher,
                         @Value("${app.messaging.max-attempts:8}") int maxAttempts) {
        this.repo = repo;
        this.json = json;
        this.publisher = publisher;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public void enqueue(String topic, Object key, Object payload) {
        OutboxMessage message = new OutboxMessage();
        message.setTopic(topic);
        message.setMessageKey(key == null ? null : String.valueOf(key));
        try {
            message.setPayload(json.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Integrationsereignis konnte nicht serialisiert werden.", ex);
        }
        repo.save(message);
    }

    @Scheduled(fixedDelayString = "${app.messaging.outbox-interval-ms:5000}")
    public void dispatch() {
        de.corporate.lc.tenant.service.TenantJobRunner.run(de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID, () -> {
        for (OutboxMessage message : repo.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt("PENDING", LocalDateTime.now())) publish(message);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(OutboxMessage message) {
        de.corporate.lc.tenant.domain.TenantContext.require(message.getTenantId());
        try {
            publisher.publish(message.getTopic(), message.getMessageKey(), message.getPayload());
            message.setStatus("PUBLISHED");
            message.setPublishedAt(LocalDateTime.now());
            message.setLastError(null);
            message.setDeadLetteredAt(null);
        } catch (RuntimeException ex) {
            int attempts = message.getAttempts() + 1;
            message.setAttempts(attempts);
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            message.setLastError(error.substring(0, Math.min(1000, error.length())));
            if (attempts >= maxAttempts) {
                message.setStatus("DEAD_LETTER");
                message.setDeadLetteredAt(LocalDateTime.now());
            } else {
                message.setStatus("PENDING");
                message.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
            }
        }
        repo.save(message);
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> deadLetters() { return repo.findTop100ByStatusOrderByCreatedAtDesc("DEAD_LETTER"); }

    @Transactional
    public OutboxMessage retry(UUID id) {
        OutboxMessage message = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Outbox-Nachricht nicht gefunden."));
        if (!"DEAD_LETTER".equals(message.getStatus())) throw new IllegalStateException("Nur Nachrichten aus der Dead-Letter-Queue können erneut gestartet werden.");
        message.setStatus("PENDING");
        message.setAttempts(0);
        message.setNextAttemptAt(LocalDateTime.now());
        message.setLastError(null);
        message.setDeadLetteredAt(null);
        return repo.save(message);
    }

    public Map<String, Object> status() {
        return Map.of("provider", publisher.provider(), "pending", repo.countByStatus("PENDING"), "deadLetter", repo.countByStatus("DEAD_LETTER"), "maxAttempts", maxAttempts);
    }
}
