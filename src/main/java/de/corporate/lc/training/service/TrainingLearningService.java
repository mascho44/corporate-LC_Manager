package de.corporate.lc.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrainingLearningService {
    private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
    private final TrainingSessionRepository sessions;
    private final ObjectMapper mapper;

    public TrainingLearningService(TrainingSessionRepository sessions,ObjectMapper mapper){this.sessions=sessions;this.mapper=mapper;}

    public String apply(String messageType,String raw){
        Learned learned=learn(messageType);Matcher matcher=FIELD.matcher(raw.strip());StringBuffer output=new StringBuffer();
        while(matcher.find()){
            String originalCode=matcher.group(1),originalValue=matcher.group(2).trim();
            Decision exact=learned.exact.get(key(originalCode,originalValue));
            if(exact!=null&&exact.invalid){matcher.appendReplacement(output,"");continue;}
            String code=exact!=null?exact.code:learned.mappings.getOrDefault(originalCode,originalCode);
            String value=exact!=null&&exact.value!=null?exact.value:originalValue;
            matcher.appendReplacement(output,Matcher.quoteReplacement(":"+code+":"+value+(value.endsWith("\n")?"":"\n")));
        }
        matcher.appendTail(output);return output.toString().trim();
    }

    public LearningSummary summary(String messageType){Learned learned=learn(messageType);return new LearningSummary(learned.exact.size(),learned.mappings.size());}
    public record LearningSummary(int exactCorrections,int stableFieldMappings){}

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
        return new Learned(exact,mappings);
    }

    private String key(String code,String value){return code+"\u0000"+value.replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);}
    private String text(JsonNode node,String name){JsonNode value=node.get(name);return value==null||value.isNull()?"":value.asText();}
    private String value(String text,String fallback){return text==null||text.isBlank()?fallback:text;}
    private record Decision(String code,String value,boolean invalid){}
    private record Learned(Map<String,Decision> exact,Map<String,String> mappings){}
}
