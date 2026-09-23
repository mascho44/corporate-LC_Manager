package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.training.domain.TrainingLearningControl;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingLearningControlRepository;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrainingLearningService {
    private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
    private final TrainingSessionRepository sessions;
    private final TrainingLearningControlRepository controls;
    private final ObjectMapper mapper;

    public TrainingLearningService(TrainingSessionRepository sessions,TrainingLearningControlRepository controls,ObjectMapper mapper){this.sessions=sessions;this.controls=controls;this.mapper=mapper;}

    public String apply(String messageType,String raw){
        Learned learned=learn(messageType);Matcher matcher=FIELD.matcher(raw.strip());StringBuffer output=new StringBuffer();
        while(matcher.find()){
            String originalCode=matcher.group(1),originalValue=matcher.group(2).trim();
            Decision exact=learned.exact.get(key(originalCode,originalValue));
            if(exact!=null&&learned.disabled.contains(ruleId("EXACT",messageType,originalCode,originalValue)))exact=null;
            if(exact!=null&&exact.invalid){matcher.appendReplacement(output,"");continue;}
            String mapping=learned.disabled.contains(ruleId("MAPPING",messageType,originalCode,""))?null:learned.mappings.get(originalCode);
            String code=exact!=null?exact.code:mapping==null?originalCode:mapping;
            String value=exact!=null&&exact.value!=null?exact.value:originalValue;
            matcher.appendReplacement(output,Matcher.quoteReplacement(":"+code+":"+value+(value.endsWith("\n")?"":"\n")));
        }
        matcher.appendTail(output);return output.toString().trim();
    }

    public LearningSummary summary(String messageType){Learned learned=learn(messageType);return new LearningSummary(learned.exact.size(),learned.mappings.size());}
    public record LearningSummary(int exactCorrections,int stableFieldMappings){}

    public record LearningRule(String id,String messageType,String kind,String sourceCode,String sourceValue,
                               String targetCode,String targetValue,boolean rejected,int examples,boolean active,List<String> documents){}

    public List<LearningRule> rules(){
        Map<String,RuleAccumulator> found=new LinkedHashMap<>();
        for(TrainingSession session:sessions.findAll()){
            try{for(JsonNode field:mapper.readTree(session.getReviewsJson()==null?"[]":session.getReviewsJson())){
                String review=text(field,"review"),code=text(field,"code"),originalCode=value(text(field,"originalCode"),code),originalValue=text(field,"originalValue"),correctedValue=text(field,"value");
                if(!originalValue.isBlank()&&(review.equals("corrected")||review.equals("reassigned")||review.equals("invalid"))){
                    String id=ruleId("EXACT",session.getMessageType(),originalCode,originalValue);
                    found.computeIfAbsent(id,x->new RuleAccumulator(id,session.getMessageType(),"Exakte Korrektur",originalCode,originalValue,code,correctedValue,review.equals("invalid"))).add(session.getFilename());
                }
            }}catch(Exception ignored){}
        }
        for(String type:sessions.findAll().stream().map(TrainingSession::getMessageType).filter(Objects::nonNull).distinct().toList()){
            Learned learned=learn(type);
            learned.mappings.forEach((source,target)->{
                String id=ruleId("MAPPING",type,source,"");RuleAccumulator rule=new RuleAccumulator(id,type,"Feldzuordnung",source,"",target,"",false);
                sessions.findAll().stream().filter(s->type.equals(s.getMessageType())).forEach(s->rule.add(s.getFilename()));found.putIfAbsent(id,rule);
            });
        }
        Set<String> inactive=new HashSet<>();controls.findAll().stream().filter(c->!c.isActive()).forEach(c->inactive.add(c.getRuleId()));
        return found.values().stream().map(r->r.view(!inactive.contains(r.id))).sorted(Comparator.comparing(LearningRule::messageType).thenComparing(LearningRule::sourceCode)).toList();
    }

    @Transactional public LearningRule setActive(String id,boolean active,String username){
        LearningRule rule=rules().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Lernregel wurde nicht gefunden."));
        TrainingLearningControl control=controls.findById(id).orElseGet(TrainingLearningControl::new);control.setRuleId(id);control.setActive(active);control.setUpdatedBy(username);control.setUpdatedAt(LocalDateTime.now());controls.save(control);
        return new LearningRule(rule.id(),rule.messageType(),rule.kind(),rule.sourceCode(),rule.sourceValue(),rule.targetCode(),rule.targetValue(),rule.rejected(),rule.examples(),active,rule.documents());
    }

    private Learned learn(String messageType){
        Map<String,Decision> exact=new HashMap<>();Map<String,Map<String,Integer>> votes=new HashMap<>();
        sessions.findAll().stream().filter(s->messageType.equals(s.getMessageType())).forEach(session->{
            try{JsonNode fields=mapper.readTree(session.getReviewsJson()==null?"[]":session.getReviewsJson());for(JsonNode field:fields){
                String review=text(field,"review"),code=text(field,"code"),originalCode=value(text(field,"originalCode"),code),originalValue=text(field,"originalValue"),correctedValue=text(field,"value");
                if(review.isBlank()||originalCode.isBlank())continue;
                if(!originalValue.isBlank()&&(review.equals("corrected")||review.equals("reassigned")||review.equals("invalid")))exact.put(key(originalCode,originalValue),new Decision(code,correctedValue,review.equals("invalid")));
                if(!review.equals("invalid")&&!code.isBlank())votes.computeIfAbsent(originalCode,x->new HashMap<>()).merge(code,1,Integer::sum);
            }}catch(Exception ignored){}
        });
        Map<String,String> mappings=new HashMap<>();votes.forEach((source,counts)->{int total=counts.values().stream().mapToInt(Integer::intValue).sum();counts.entrySet().stream().max(Map.Entry.comparingByValue()).filter(e->e.getValue()>=3&&e.getValue()*4>=total*3&&!e.getKey().equals(source)).ifPresent(e->mappings.put(source,e.getKey()));});
        Set<String> disabled=new HashSet<>();controls.findAll().stream().filter(c->!c.isActive()).forEach(c->disabled.add(c.getRuleId()));
        return new Learned(exact,mappings,disabled);
    }

    private String key(String code,String value){return code+"\u0000"+value.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);}
    private String text(JsonNode node,String name){JsonNode value=node.get(name);return value==null||value.isNull()?"":value.asText();}
    private String value(String text,String fallback){return text==null||text.isBlank()?fallback:text;}
    private record Decision(String code,String value,boolean invalid){}
    private String ruleId(String kind,String type,String code,String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest((kind+"\u0000"+type+"\u0000"+code+"\u0000"+value).getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(bytes);}catch(Exception e){throw new IllegalStateException(e);}}
    private record Learned(Map<String,Decision> exact,Map<String,String> mappings,Set<String> disabled){}
    private static class RuleAccumulator {final String id,type,kind,sourceCode,sourceValue,targetCode,targetValue;final boolean rejected;final Set<String> documents=new LinkedHashSet<>();int examples;
        RuleAccumulator(String id,String type,String kind,String sourceCode,String sourceValue,String targetCode,String targetValue,boolean rejected){this.id=id;this.type=type;this.kind=kind;this.sourceCode=sourceCode;this.sourceValue=sourceValue;this.targetCode=targetCode;this.targetValue=targetValue;this.rejected=rejected;}
        void add(String document){examples++;if(document!=null)documents.add(document);}
        LearningRule view(boolean active){return new LearningRule(id,type,kind,sourceCode,sourceValue,targetCode,targetValue,rejected,examples,active,List.copyOf(documents));}}
}
