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
    @Test void retryRejectsForeignMessageBeforeChangingItsFailureState(){
        var repository=mock(OutboxMessageRepository.class);var publisher=mock(MessagePublisher.class);var service=new OutboxService(repository,new ObjectMapper(),publisher,3);OutboxMessage foreign;
        try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(UUID.randomUUID())){foreign=new OutboxMessage();foreign.setStatus("DEAD_LETTER");foreign.setAttempts(3);foreign.setLastError("Synthetic failure");}
        var id=UUID.randomUUID();when(repository.findById(id)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(()->service.retry(id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(foreign.getStatus()).isEqualTo("DEAD_LETTER");assertThat(foreign.getAttempts()).isEqualTo(3);assertThat(foreign.getLastError()).isEqualTo("Synthetic failure");verify(repository,never()).save(any());verifyNoInteractions(publisher);
    }
    @Test void deadLetterOverviewRejectsForeignRepositoryResults(){
        var repository=mock(OutboxMessageRepository.class);var publisher=mock(MessagePublisher.class);var service=new OutboxService(repository,new ObjectMapper(),publisher,3);OutboxMessage foreign;
        try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(UUID.randomUUID())){foreign=new OutboxMessage();}
        when(repository.findTop100ByStatusOrderByCreatedAtDesc("DEAD_LETTER")).thenReturn(java.util.List.of(new OutboxMessage(),foreign));
        assertThatThrownBy(service::deadLetters).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(publisher);verify(repository,never()).save(any());
    }
    @Test void selectedTenantCanViewAndRetryOwnDeadLetterWithoutPublishingImmediately(){
        var repository=mock(OutboxMessageRepository.class);var publisher=mock(MessagePublisher.class);var service=new OutboxService(repository,new ObjectMapper(),publisher,3);var tenant=UUID.randomUUID();
        try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(tenant)){
            var own=new OutboxMessage();own.setStatus("DEAD_LETTER");own.setAttempts(3);var id=UUID.randomUUID();
            when(repository.findTop100ByStatusOrderByCreatedAtDesc("DEAD_LETTER")).thenReturn(java.util.List.of(own));when(repository.findById(id)).thenReturn(Optional.of(own));when(repository.save(own)).thenReturn(own);
            assertThat(service.deadLetters()).containsExactly(own);assertThat(service.retry(id).getStatus()).isEqualTo("PENDING");assertThat(own.getAttempts()).isZero();assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(tenant);verifyNoInteractions(publisher);
        }
        assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID);
    }
    @Test void scheduledDispatchUsesExplicitDefaultTenantAndRestoresCallerScope() {
        var repository=mock(OutboxMessageRepository.class);var publisher=mock(MessagePublisher.class);
        var service=new OutboxService(repository,new ObjectMapper(),publisher,3);var message=new OutboxMessage();message.setTopic("synthetic.topic");message.setPayload("{}");
        when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(eq("PENDING"),any())).thenAnswer(call->{assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID);return java.util.List.of(message);});
        UUID caller=UUID.randomUUID();try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(caller)){service.dispatch();assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(caller);}
        verify(publisher).publish(eq("synthetic.topic"),isNull(),eq("{}"));assertThat(message.getStatus()).isEqualTo("PUBLISHED");
    }
    @Test void scheduledDispatchRejectsForeignMessageAndRestoresScopeOnFailure() {
        var repository=mock(OutboxMessageRepository.class);var publisher=mock(MessagePublisher.class);var service=new OutboxService(repository,new ObjectMapper(),publisher,3);
        UUID caller=UUID.randomUUID();OutboxMessage foreign;try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(caller)){foreign=new OutboxMessage();}
        when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(eq("PENDING"),any())).thenReturn(java.util.List.of(foreign));
        try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(caller)){assertThatThrownBy(service::dispatch).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(de.corporate.lc.tenant.domain.TenantContext.currentId()).isEqualTo(caller);}
        verifyNoInteractions(publisher);verify(repository,never()).save(any());
    }
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
