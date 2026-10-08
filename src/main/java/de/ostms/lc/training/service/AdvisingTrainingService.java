package de.ostms.lc.training.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.training.domain.TrainingSession;
import de.ostms.lc.training.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.*;

@Service
public class AdvisingTrainingService {
 public static final Map<String,String> LABELS=Map.of("reference","LC number","ownBankReference","Referenz eigene Bank","foreignBankReference","Fremdbankreferenz","applicant","Applicant","beneficiary","Beneficiary","amount","Credit amount","expiryDate","Expiry date","issuingBank","Issuing bank");
 public record Field(String sourceLabel,String originalCode,String originalValue,String code,String value,String review){}
 private final TrainingSessionRepository sessions;private final ObjectMapper mapper;private final TrainingLearningService learning;
 public AdvisingTrainingService(TrainingSessionRepository sessions,ObjectMapper mapper,TrainingLearningService learning){this.sessions=sessions;this.mapper=mapper;this.learning=learning;}
 public List<Field> fields(String bank,String text){
  var mappings=learning.adviceMappings(bank);List<Field> fields=new ArrayList<>();
  for(var row:de.ostms.lc.document.service.AdvisingRows.read(text)){String target=mappings.getOrDefault(row.label().toUpperCase(Locale.ROOT),row.target());fields.add(new Field(row.label(),TrainingLearningService.adviceSource(bank,row.label()),row.value(),target,row.value(),null));}
  return fields;
 }
 @Transactional public TrainingSession save(UUID id,List<Field> submitted,String username,boolean finish){
  var session=sessions.findForUpdate(id).orElseThrow();
  if(!"ADVISING_LETTER".equals(session.getMessageType())||!username.equals(session.getUsername())||!"DRAFT".equals(session.getStatus()))throw new IllegalArgumentException("Trainingsentwurf nicht bearbeitbar.");
  try{
   List<Field> original=Arrays.asList(mapper.readValue(session.getReviewsJson(),Field[].class));
   if(original.size()!=submitted.size())throw new IllegalArgumentException("Trainingsfelder wurden verändert.");
   List<Field> merged=new ArrayList<>();
   for(int i=0;i<original.size();i++){
    var old=original.get(i);var field=submitted.get(i);
    if(field.value()==null||field.value().length()>4000||field.code()==null||(!field.code().isBlank()&&!LABELS.containsKey(field.code()))||field.review()!=null&&!Set.of("correct","corrected","reassigned","invalid").contains(field.review()))throw new IllegalArgumentException("Ungültiges Trainingsfeld.");
    if(field.review()!=null&&!field.review().equals("invalid")&&(field.code().isBlank()||field.value().isBlank()))throw new IllegalArgumentException("Bitte ein Zielfeld und einen Wert auswählen.");
    if(finish&&field.review()==null)throw new IllegalArgumentException("Bitte alle Felder bestätigen oder verwerfen.");
    merged.add(new Field(old.sourceLabel(),old.originalCode(),old.originalValue(),field.code(),field.value(),field.review()));
   }
   session.setReviewsJson(mapper.writeValueAsString(merged));
   if(finish){session.setStatus("CONFIRMED");session.setConfirmedAt(java.time.LocalDateTime.now());}
   return sessions.save(session);
  }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Ungültige Trainingsdaten.",e);}
 }
 public de.ostms.lc.document.service.AdvisingLetterExtractor.Proposal preview(String bank,String text){
  String enriched=text==null?"":text;
  if(bank!=null&&!bank.isBlank())for(var field:fields(bank,text)){if(!field.code().isBlank())enriched+="\n"+LABELS.get(field.code())+": "+field.value().replace('\n',' ');}
  return de.ostms.lc.document.service.AdvisingLetterExtractor.extract(enriched);
 }
}
