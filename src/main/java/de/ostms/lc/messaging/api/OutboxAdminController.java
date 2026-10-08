package de.ostms.lc.messaging.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.messaging.domain.OutboxMessage;
import de.ostms.lc.messaging.service.OutboxService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/outbox")
public class OutboxAdminController {
    private final OutboxService outbox;
    private final AuditService audit;

    public OutboxAdminController(OutboxService outbox, AuditService audit) {
        this.outbox = outbox;
        this.audit = audit;
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return outbox.status(); }

    @GetMapping("/dead-letter")
    public List<DeadLetterView> deadLetters() { return outbox.deadLetters().stream().map(DeadLetterView::from).toList(); }

    @PostMapping("/{id}/retry")
    public DeadLetterView retry(@PathVariable UUID id, Authentication authentication) {
        OutboxMessage message = outbox.retry(id);
        audit.record(authentication, "OUTBOX_RETRIED", "INTEGRATION_OUTBOX", id, message.getTopic());
        return DeadLetterView.from(message);
    }

    public record DeadLetterView(UUID id, String topic, String messageKey, int attempts,
                                 LocalDateTime createdAt, LocalDateTime deadLetteredAt, String lastError) {
        static DeadLetterView from(OutboxMessage message) {
            return new DeadLetterView(message.getId(), message.getTopic(), message.getMessageKey(), message.getAttempts(),
                    message.getCreatedAt(), message.getDeadLetteredAt(), message.getLastError());
        }
    }
}
