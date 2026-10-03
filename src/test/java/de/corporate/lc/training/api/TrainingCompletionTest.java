package de.corporate.lc.training.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.imports.service.SwiftImportService;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import de.corporate.lc.training.service.TrainingDataService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrainingCompletionTest {
    @Test void finishingTrainingDoesNotImportOrLinkLc() {
        var repo=mock(TrainingSessionRepository.class);
        var imports=mock(SwiftImportService.class);
        var lcs=mock(LetterOfCreditRepository.class);
        var auth=mock(Authentication.class);
        when(auth.getName()).thenReturn("tester");
        UUID id=UUID.randomUUID();
        var session=new TrainingSession();
        session.setUsername("tester");session.setStatus("DRAFT");session.setFilename("sample.pdf");
        when(repo.findById(id)).thenReturn(Optional.of(session));
        var controller=new TrainingController(repo,null,null,imports,new TrainingDataService(new ObjectMapper()),null,mock(AuditService.class),lcs,null,null);
        controller.finish(id,new TrainingConfirm(":44C:unreadable","[{\"code\":\"44C\",\"review\":\"invalid\"}]"),auth);
        assertThat(session.getStatus()).isEqualTo("CONFIRMED");
        assertThat(session.getConfirmedAt()).isNotNull();
        assertThat(session.getLcId()).isNull();
        verifyNoInteractions(imports,lcs);
    }
}
