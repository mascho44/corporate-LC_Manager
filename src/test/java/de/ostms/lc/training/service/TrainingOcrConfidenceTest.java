package de.ostms.lc.training.service;
import de.ostms.lc.training.domain.TrainingSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TrainingOcrConfidenceTest {
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 private final TrainingDataService service=new TrainingDataService(mapper);
 @Test void digitalTextHasNoInventedOcrScore(){
  var preview=new de.ostms.lc.imports.api.SwiftImportPreview("MT700","LC123",null,null,null,null,null,null,null,null,java.util.List.of(),false,true,java.util.List.of(),java.util.List.of(),java.util.List.of(new de.ostms.lc.imports.api.SwiftFieldView("20","Reference","LC123",null,null,"HIGH",null,false,null)));
  var session=new TrainingSession();var scores=service.initializeOcrConfidence(session,preview,new de.ostms.lc.document.service.DocumentExtractionService.TextExtraction("LC123","EXTRACTED"));
  assertThat(scores.get(0).score()).isNull();assertThat(scores.get(0).status()).isEqualTo("NOT_APPLICABLE");assertThat(session.getOcrConfidenceJson()).contains("NOT_APPLICABLE");
 }
 @Test void correctionCannotReplaceOriginalOcrScore()throws Exception{
  var session=new TrainingSession();session.setOcrConfidenceJson("[{\"score\":0.4,\"status\":\"REVIEW\",\"originalValue\":\"120\"}]");
  String preserved=service.preserveOcrConfidence(session,"[{\"value\":\"150\",\"ocr\":{\"score\":1}}]");
  var field=mapper.readTree(preserved).get(0);assertThat(field.path("value").asText()).isEqualTo("150");assertThat(field.path("ocr").path("score").asDouble()).isEqualTo(.4);assertThat(field.path("ocr").path("originalValue").asText()).isEqualTo("120");
 }
 @Test void mismatchedFieldsRejected(){var session=new TrainingSession();session.setOcrConfidenceJson("[{}]");assertThatThrownBy(()->service.preserveOcrConfidence(session,"[]")).isInstanceOf(IllegalArgumentException.class);}
 @Test void exportsOcrMetadataToXmlAndJson(){var session=new TrainingSession();session.setReviewsJson("[{\"code\":\"20\",\"ocr\":{\"score\":0.42,\"status\":\"REVIEW\",\"engineVersion\":\"tesseract 5\",\"words\":[{\"text\":\"LC1\",\"page\":2,\"confidence\":0.42}]}}]");assertThat(new String(service.xml(session),java.nio.charset.StandardCharsets.UTF_8)).contains("<score>0.42</score>","<page>2</page>","tesseract 5");assertThat(new String(service.json(session),java.nio.charset.StandardCharsets.UTF_8)).contains("tesseract 5");}
}
