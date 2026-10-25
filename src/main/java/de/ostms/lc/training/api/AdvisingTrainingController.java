package de.ostms.lc.training.api;
import de.ostms.lc.training.service.*;
import de.ostms.lc.training.domain.TrainingSession;
import de.ostms.lc.training.repository.TrainingSessionRepository;
import de.ostms.lc.document.service.DocumentExtractionService;
import de.ostms.lc.audit.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/api/training/advising")
public class AdvisingTrainingController {
 private final AdvisingTrainingService training;private final TrainingSessionRepository sessions;private final DocumentExtractionService extraction;private final ObjectMapper mapper;private final AuditService audit;
 public AdvisingTrainingController(AdvisingTrainingService training,TrainingSessionRepository sessions,DocumentExtractionService extraction,ObjectMapper mapper,AuditService audit){this.training=training;this.sessions=sessions;this.extraction=extraction;this.mapper=mapper;this.audit=audit;}
 public record View(UUID id,List<AdvisingTrainingService.Field> fields){}
 @PostMapping @Transactional public View start(@RequestPart("file") MultipartFile file,@RequestParam String bank,Authentication auth)throws java.io.IOException{
  if(bank.isBlank()||bank.length()>100||bank.contains("|")||file.isEmpty()||file.getSize()>10*1024*1024||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf"))throw new IllegalArgumentException("Bankprofil und PDF (maximal 10 MB) erforderlich.");
  var extracted=extraction.extractFile(file.getBytes(),file.getOriginalFilename(),"application/pdf");var fields=training.fields(bank,extracted.text());if(fields.isEmpty())throw new IllegalArgumentException("Keine beschrifteten Angaben erkannt. Bitte ein Avisierungsschreiben mit Feldbezeichnungen verwenden.");
  var session=new TrainingSession();session.setFilename(file.getOriginalFilename());session.setContentType("application/pdf");session.setOriginalPdf(file.getBytes());session.setExtractedText(extracted.text());session.setReviewsJson(mapper.writeValueAsString(fields));session.setStatus("DRAFT");session.setUsername(auth.getName());session.setExtractionStatus(extracted.status());session.setMessageType("ADVISING_LETTER");sessions.save(session);
  audit.record(auth,"TRAINING_STARTED","TRAINING_SESSION",session.getId(),"Avisierungsschreiben · Bankprofil "+bank);return new View(session.getId(),fields);
 }
 @PutMapping("/{id}") @Transactional public View save(@PathVariable UUID id,@RequestBody List<AdvisingTrainingService.Field> fields,@RequestParam(defaultValue="false") boolean finish,Authentication auth){var session=training.save(id,fields,auth.getName(),finish);if(finish)audit.record(auth,"TRAINING_CONFIRMED","TRAINING_SESSION",id,"Avisierungsschreiben · keine Akte angelegt");try{return new View(id,Arrays.asList(mapper.readValue(session.getReviewsJson(),AdvisingTrainingService.Field[].class)));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
