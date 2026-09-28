package de.corporate.lc.audit.service;

import de.corporate.lc.audit.domain.AuditEvent;
import de.corporate.lc.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    @Test void exportsRecentEventsAsExcelCompatibleCsv(){
        AuditEvent event=new AuditEvent();event.setUsername("administrator");event.setAction("DOCUMENT_UPDATED");event.setEntityType("LETTER_OF_CREDIT");event.setEntityId("LC-1");event.setDetails("Rechnung; geändert");event.setSuccessful(true);
        AuditEventRepository repository=mock(AuditEventRepository.class);when(repository.findTop200ByOrderByOccurredAtDesc()).thenReturn(List.of(event));
        String csv=new String(new AuditService(repository).csv(),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFZeitpunkt;Ergebnis").contains("\"Erfolgreich\"","\"administrator\"","\"Rechnung; geändert\"");
    }
}
