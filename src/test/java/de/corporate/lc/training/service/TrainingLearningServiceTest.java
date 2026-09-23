package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrainingLearningServiceTest {
    @Test void appliesExactCorrectionFromEditing(){
        TrainingSession session=session("[{\"originalCode\":\"31C\",\"code\":\"31C\",\"originalValue\":\"25O522\",\"value\":\"250522\",\"review\":\"corrected\"}]");
        TrainingSessionRepository repo=mock(TrainingSessionRepository.class);when(repo.findAll()).thenReturn(List.of(session));
        TrainingLearningService service=new TrainingLearningService(repo,new ObjectMapper());
        assertThat(service.apply("MT700",":20:LC-1\n:31C:25O522")).contains(":31C:250522").doesNotContain("25O522");
        assertThat(service.summary("MT700").exactCorrections()).isEqualTo(1);
    }

    @Test void requiresThreeConsistentExamplesForGeneralFieldMapping(){
        String decision="[{\"originalCode\":\"72\",\"code\":\"72Z\",\"originalValue\":\"NOTE\",\"value\":\"NOTE\",\"review\":\"reassigned\"}]";
        TrainingSessionRepository repo=mock(TrainingSessionRepository.class);when(repo.findAll()).thenReturn(List.of(session(decision),session(decision),session(decision)));
        TrainingLearningService service=new TrainingLearningService(repo,new ObjectMapper());
        assertThat(service.apply("MT700",":20:LC-1\n:72:OTHER NOTE")).contains(":72Z:OTHER NOTE");
        assertThat(service.summary("MT700").stableFieldMappings()).isEqualTo(1);
    }

    private TrainingSession session(String reviews){TrainingSession session=new TrainingSession();session.setMessageType("MT700");session.setReviewsJson(reviews);return session;}
}
