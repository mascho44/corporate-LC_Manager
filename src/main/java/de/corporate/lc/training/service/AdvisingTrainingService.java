package de.corporate.lc.training.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.*;

@Service
public class AdvisingTrainingService {
 public static final Map<String,String> LABELS=Map.of("reference","LC number","ownBankReference","Referenz eigene Bank","foreignBankReference","Fremdbankreferenz","applicant","Applicant","beneficiary","Beneficiary","amount","Credit amount","expiryDate","Expiry date");
 public record Field(String sourceLabel,String originalCode,String originalValue,String code,String value,String review){}
 private final TrainingSessionRepository sessions;private final ObjectMapper mapper;private final TrainingLearningService learning;
 public AdvisingTrainingService(TrainingSessionRepository sessions,ObjectMapper mapper,TrainingLearningService learning){this.sessions=sessions;this.mapper=mapper;this.learning=learning;}
 public List<Field> fields(String bank,String text){
  var mappings=learning.adviceMappings(bank);List<Field> fields=new ArrayList<>();var matcher=Pattern.compile("(?m)^\\s*([^:\\r\\n]{1,100}):[ \\t]*([^\\r\\n]+)$").matcher(text==null?"":text);
  while(matcher.find()&&fields.size()<100){String label=matcher.group(1).trim(),value=matcher.group(2).trim();String target=mappings.getOrDefault(label.toUpperCase(Locale.ROOT),"");fields.add(new Field(label,TrainingLearningService.adviceSource(bank,label),value,target,value,null));}
  return fields;
 }
 @Transactional public TrainingSession save(UUID id,List<Field> submitted,String username,boolean finish){
  var session=sessions.findById(id).orElseThrow();
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
 public de.corporate.lc.document.service.AdvisingLetterExtractor.Proposal preview(String bank,String text){
  String enriched=text==null?"":text;
  if(bank!=null&&!bank.isBlank())for(var field:fields(bank,text)){if(!field.code().isBlank())enriched+="\n"+LABELS.get(field.code())+": "+field.value();}
  return de.corporate.lc.document.service.AdvisingLetterExtractor.extract(enriched);
 }
}
