package de.ostms.lc.audit.service;

import de.ostms.lc.audit.domain.AuditEvent;
import de.ostms.lc.audit.repository.AuditEventRepository;
import de.ostms.lc.messaging.service.OutboxService;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    @Test void recordsPreviousAndNewValuesForChanges(){
        AuditEventRepository repository=mock(AuditEventRepository.class);OutboxService outbox=mock(OutboxService.class);AuditService service=new AuditService(repository,outbox);
        service.recordChange(null,"LC_UPDATED","LETTER_OF_CREDIT","LC-1","Fachliche Änderung","Betrag=1000","Betrag=1200");
        var captor=org.mockito.ArgumentCaptor.forClass(AuditEvent.class);verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPreviousValue()).isEqualTo("Betrag=1000");assertThat(captor.getValue().getNewValue()).isEqualTo("Betrag=1200");
        verify(outbox).enqueue(eq("corporate-lc.lc.updated"),eq("LC-1"),any());
    }
    @Test void exportsRecentEventsAsExcelCompatibleCsv(){
        AuditEvent event=new AuditEvent();event.setUsername("administrator");event.setAction("DOCUMENT_UPDATED");event.setEntityType("LETTER_OF_CREDIT");event.setEntityId("LC-1");event.setDetails("Rechnung; geändert");event.setSuccessful(true);
        AuditEventRepository repository=mock(AuditEventRepository.class);when(repository.findTop200ByOrderByOccurredAtDesc()).thenReturn(List.of(event));
        String csv=new String(new AuditService(repository,mock(OutboxService.class)).csv(),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFZeitpunkt;Ergebnis").contains("Zeitpunkt (UTC);Rolle;Sitzung;Anfrage-ID;User-Agent;Fehlergrund").contains("\"Erfolgreich\"","\"administrator\"","\"Rechnung; geändert\"");
    }
}
