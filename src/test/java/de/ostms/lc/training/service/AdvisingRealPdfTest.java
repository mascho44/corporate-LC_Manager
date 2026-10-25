package de.ostms.lc.training.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.document.service.*;
import de.ostms.lc.training.domain.TrainingSession;
import de.ostms.lc.training.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Opt-in real-document regression: private PDFs are never committed. */
class AdvisingRealPdfTest {
 @Test @EnabledIfSystemProperty(named="advice.files",matches=".+") void realTableAdviceAndSnippets()throws Exception{
  var mapper=new ObjectMapper();var learning=new TrainingLearningService(mock(TrainingSessionRepository.class),mock(TrainingLearningControlRepository.class),mapper);var training=new AdvisingTrainingService(mock(TrainingSessionRepository.class),mapper,learning);
  int fileIndex=0;for(String filename:System.getProperty("advice.files").split(",")){
   var bytes=Files.readAllBytes(Path.of(filename));var extraction=new DocumentExtractionService().extractFile(bytes,"advice.pdf","application/pdf");assertThat(extraction.status()).isEqualTo("EXTRACTED");
   var fields=training.fields("BANK",extraction.text());assertThat(fields).extracting(AdvisingTrainingService.Field::sourceLabel).contains("Akkreditivnummer","Unsere Referenz","Akkreditiv über","Gültig bis","Eröffnende Bank","Auftraggeber","Empfänger (Briefkopf – prüfen)");
   var preview=training.preview("BANK",extraction.text());assertThat(preview.fields()).containsKeys("reference","amount","currency","expiryDate","expiryPlace","issuingBank","applicant","beneficiary");assertThat(preview.fields()).doesNotContainKey("ownBankReference");
   var session=new TrainingSession();ReflectionTestUtils.setField(session,"id",UUID.randomUUID());session.setMessageType("ADVISING_LETTER");session.setOriginalPdf(bytes);session.setExtractedText(extraction.text());session.setReviewsJson(mapper.writeValueAsString(fields));
   var snippets=new PdfFieldSnippetService();int index=0;for(var field:fields){var snippet=snippets.snippet(session,index++);var image=ImageIO.read(new java.io.ByteArrayInputStream(snippet));assertThat(image.getWidth()).as("real crop: %s",field.sourceLabel()).isGreaterThan(1000);if(System.getProperty("advice.snippets")!=null)ImageIO.write(image,"png",Path.of(System.getProperty("advice.snippets"),"advice-"+fileIndex+"-field-"+index+".png").toFile());}
   System.out.println("Real advice "+(++fileIndex)+": "+fields.size()+" fields and original PDF crops verified");
  }
 }
}
