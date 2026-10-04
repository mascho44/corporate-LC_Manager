package de.corporate.lc.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.messaging.domain.OutboxMessage;
import de.corporate.lc.messaging.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OutboxServiceTest {
    @Test void movesMessageToDeadLetterAfterConfiguredAttempts() {
        var repository = mock(OutboxMessageRepository.class);
        var publisher = mock(MessagePublisher.class);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(any(), any(), any());
        var service = new OutboxService(repository, new ObjectMapper(), publisher, 2);
        var message = new OutboxMessage(); message.setTopic("test.topic"); message.setPayload("{}");

        service.publish(message);
        assertThat(message.getStatus()).isEqualTo("PENDING");
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getNextAttemptAt()).isAfter(LocalDateTime.now().minusSeconds(1));

        service.publish(message);
        assertThat(message.getStatus()).isEqualTo("DEAD_LETTER");
        assertThat(message.getAttempts()).isEqualTo(2);
        assertThat(message.getDeadLetteredAt()).isNotNull();
        assertThat(message.getLastError()).isEqualTo("broker unavailable");
        verify(repository, times(2)).save(message);
    }

    @Test void permitsManualRetryOnlyForDeadLetteredMessages() {
        UUID id = UUID.randomUUID(); var repository = mock(OutboxMessageRepository.class);
        var service = new OutboxService(repository, new ObjectMapper(), mock(MessagePublisher.class), 3);
        var message = new OutboxMessage(); message.setStatus("DEAD_LETTER"); message.setAttempts(3); message.setLastError("failure"); message.setDeadLetteredAt(LocalDateTime.now());
        when(repository.findById(id)).thenReturn(Optional.of(message)); when(repository.save(message)).thenReturn(message);
        var result = service.retry(id);
        assertThat(result.getStatus()).isEqualTo("PENDING"); assertThat(result.getAttempts()).isZero();
        assertThat(result.getLastError()).isNull(); assertThat(result.getDeadLetteredAt()).isNull();

        message.setStatus("PUBLISHED");
        assertThatThrownBy(() -> service.retry(id)).isInstanceOf(IllegalStateException.class);
    }
}
