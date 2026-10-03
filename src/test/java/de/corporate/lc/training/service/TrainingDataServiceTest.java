package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.training.domain.TrainingSession;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingDataServiceTest {
    private final TrainingDataService service=new TrainingDataService(new ObjectMapper().findAndRegisterModules());

    @Test void trainingCompletionRequiresReviewsButNotAValidBusinessRecord(){
        service.requireReviewedFields("[{\"code\":\"44C\",\"value\":\"unreadable\",\"review\":\"invalid\"}]");
        for(String invalid:List.of("[]","{}","null","not json","[{\"code\":\"20\"}]"))
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.requireReviewedFields(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void exportsConfirmedFieldsAsJsonAndXml(){
        TrainingSession session=session("MT760","corrected",":20:A&B", "[{\"code\":\"20\",\"value\":\"A&B\",\"review\":\"corrected\"}]");
        String json=new String(service.json(session),StandardCharsets.UTF_8);
        String xml=new String(service.xml(session),StandardCharsets.UTF_8);
        assertThat(json).contains("MT760","corrected","A&B");
        assertThat(xml).contains("<messageType>MT760</messageType>","<review>corrected</review>","A&amp;B");
    }

    @Test void aggregatesQualityByProfileAndField(){
        TrainingSession first=session("MT700","correct",":20:ONE","[{\"code\":\"20\",\"review\":\"correct\"},{\"code\":\"31D\",\"review\":\"corrected\"}]");
        TrainingSession second=session("MT700","correct",":20:TWO","[{\"code\":\"20\",\"review\":\"correct\"}]");
        var quality=service.quality(List.of(first,second));
        assertThat(quality).singleElement().satisfies(profile->{
            assertThat(profile.messageType()).isEqualTo("MT700");
            assertThat(profile.sessions()).isEqualTo(2);
            assertThat(profile.accuracyPercent()).isEqualTo(66.7);
            assertThat(profile.fieldQuality()).anySatisfy(field->{assertThat(field.code()).isEqualTo("20");assertThat(field.accuracyPercent()).isEqualTo(100.0);});
        });
    }

    private TrainingSession session(String type,String review,String raw,String fields){
        TrainingSession session=new TrainingSession(); session.setFilename("sample.pdf");session.setMessageType(type);
        session.setStatus("CONFIRMED");session.setUsername("tester");session.setExtractionStatus("OCR_EXTRACTED");
        session.setOriginalPdf(new byte[]{1});session.setExtractedText(raw);session.setCorrectedText(raw);
        session.setReviewsJson(fields);session.setConfirmedAt(LocalDateTime.now());return session;
    }
}
