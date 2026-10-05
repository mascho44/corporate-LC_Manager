package de.corporate.lc.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import de.corporate.lc.training.domain.TrainingSession;
import org.springframework.stereotype.Service;

import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class TrainingDataService {
    private final ObjectMapper mapper;

    public TrainingDataService(ObjectMapper mapper){this.mapper=mapper;}
    public List<de.corporate.lc.document.service.OcrEvidence.Assessment> initializeOcrConfidence(TrainingSession session,de.corporate.lc.imports.api.SwiftImportPreview preview,de.corporate.lc.document.service.DocumentExtractionService.TextExtraction extraction){
        var evidence=extraction.ocrEvidence();
        List<de.corporate.lc.document.service.OcrEvidence.Assessment> scores=preview.rawFields().stream().map(field->evidence==null
            ?new de.corporate.lc.document.service.OcrEvidence.Assessment(null,null,"OCR_EXTRACTED".equals(extraction.status())?"UNAVAILABLE":"NOT_APPLICABLE",null,null,0.8,field.value(),List.of())
            :evidence.assess(field.value(),evidence.threshold())).toList();
        try{
            session.setOcrConfidenceJson(mapper.writeValueAsString(scores));
            var fields=mapper.valueToTree(preview.rawFields());
            for(int i=0;i<fields.size();i++)((com.fasterxml.jackson.databind.node.ObjectNode)fields.get(i)).set("ocr",mapper.valueToTree(scores.get(i)));
            session.setReviewsJson(mapper.writeValueAsString(fields));
        }catch(JsonProcessingException e){throw new IllegalStateException("OCR-Bewertungen konnten nicht gespeichert werden",e);}
        return scores;
    }
    /** Client edits cannot change the OCR measurement of the original field value. */
    public String preserveOcrConfidence(TrainingSession session,String reviews){
        if(session.getOcrConfidenceJson()==null)return reviews;
        try{
            var scores=mapper.readTree(session.getOcrConfidenceJson());var fields=mapper.readTree(reviews);
            if(!fields.isArray()||fields.size()!=scores.size())throw new IllegalArgumentException("Anzahl der Trainingsfelder wurde verändert.");
            for(int i=0;i<fields.size();i++){
                if(!fields.get(i).isObject())throw new IllegalArgumentException("Ungültiges Trainingsfeld");
                ((com.fasterxml.jackson.databind.node.ObjectNode)fields.get(i)).set("ocr",scores.get(i));
            }
            return mapper.writeValueAsString(fields);
        }catch(JsonProcessingException e){throw new IllegalArgumentException("Ungültige Trainingsfelder",e);}
    }

    public void requireReviewedFields(String json) {
        try {
            JsonNode fields=mapper.readTree(json==null?"[]":json);
            if(fields==null||!fields.isArray()||fields.isEmpty()) throw new IllegalArgumentException("Keine Trainingsfelder vorhanden.");
            for(JsonNode field:fields) {
                if(!Set.of("correct","corrected","reassigned","invalid").contains(field.path("review").asText()))
                    throw new IllegalArgumentException("Bitte alle Trainingsfelder bestätigen oder als nicht verwendbar markieren.");
            }
        } catch(JsonProcessingException ex) { throw new IllegalArgumentException("Ungültige Trainingsfelder.",ex); }
    }

    public record Detail(UUID id,String filename,String messageType,String status,String username,
                         String extractionStatus,String rawMessage,JsonNode fields,
                         java.time.LocalDateTime createdAt,java.time.LocalDateTime confirmedAt,UUID lcId){}
    public record FieldQuality(String code,long confirmed,long correct,long corrected,double accuracyPercent){}
    public record ProfileQuality(String messageType,long sessions,long fields,long correct,long corrected,
                                 double accuracyPercent,List<FieldQuality> fieldQuality){}

    public Detail detail(TrainingSession session){
        return new Detail(session.getId(),session.getFilename(),session.getMessageType(),session.getStatus(),
                session.getUsername(),session.getExtractionStatus(),raw(session),fields(session),
                session.getCreatedAt(),session.getConfirmedAt(),session.getLcId());
    }

    public byte[] json(TrainingSession session){
        try{return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(detail(session));}
        catch(JsonProcessingException e){throw new IllegalArgumentException("JSON-Export konnte nicht erstellt werden.",e);}
    }

    public byte[] xml(TrainingSession session){
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            XMLStreamWriter xml=XMLOutputFactory.newFactory().createXMLStreamWriter(output,StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(),"1.0"); xml.writeStartElement("swiftTraining");
            element(xml,"id",String.valueOf(session.getId())); element(xml,"filename",session.getFilename());
            element(xml,"messageType",session.getMessageType()); element(xml,"status",session.getStatus());
            element(xml,"username",session.getUsername()); element(xml,"extractionStatus",session.getExtractionStatus());
            element(xml,"createdAt",String.valueOf(session.getCreatedAt())); element(xml,"confirmedAt",String.valueOf(session.getConfirmedAt()));
            element(xml,"rawMessage",raw(session)); xml.writeStartElement("fields");
            for(JsonNode field:fields(session)){
                xml.writeStartElement("field");
                for(String name:List.of("code","label","value","suggestedTarget","targetLabel","confidence","review")) element(xml,name,text(field,name));
                if(field.has("ocr")){
                    xml.writeStartElement("ocrConfidence");
                    for(String name:List.of("score","meanScore","status","method","engineVersion","threshold","originalValue"))element(xml,name,text(field.path("ocr"),name));
                    xml.writeStartElement("words");
                    for(JsonNode word:field.path("ocr").path("words")){
                        xml.writeStartElement("word");
                        for(String name:List.of("text","confidence","page","left","top","width","height"))element(xml,name,text(word,name));
                        xml.writeEndElement();
                    }
                    xml.writeEndElement();xml.writeEndElement();
                }
                xml.writeEndElement();
            }
            xml.writeEndElement(); xml.writeEndElement(); xml.writeEndDocument(); xml.close();
            return output.toByteArray();
        } catch(XMLStreamException e){throw new IllegalArgumentException("XML-Export konnte nicht erstellt werden.",e);}
    }

    public List<ProfileQuality> quality(List<TrainingSession> sessions){
        Map<String,List<TrainingSession>> profiles=new TreeMap<>();
        sessions.stream().filter(s->fields(s).size()>0).forEach(s->profiles.computeIfAbsent(value(s.getMessageType(),"Unbekannt"),x->new ArrayList<>()).add(s));
        List<ProfileQuality> result=new ArrayList<>();
        profiles.forEach((type,items)->{
            Map<String,long[]> byField=new TreeMap<>(); long correct=0,corrected=0;
            for(TrainingSession session:items) for(JsonNode field:fields(session)){
                String review=text(field,"review"),code=value(text(field,"code"),"?"); long[] counts=byField.computeIfAbsent(code,x->new long[2]);
                if("correct".equals(review)){counts[0]++;correct++;} else if(!review.isBlank()){counts[1]++;corrected++;}
            }
            List<FieldQuality> fieldQuality=byField.entrySet().stream().map(e->{long total=e.getValue()[0]+e.getValue()[1];return new FieldQuality(e.getKey(),total,e.getValue()[0],e.getValue()[1],percent(e.getValue()[0],total));}).toList();
            long total=correct+corrected; result.add(new ProfileQuality(type,items.size(),total,correct,corrected,percent(correct,total),fieldQuality));
        });
        return result;
    }

    private JsonNode fields(TrainingSession session){try{String json=session.getReviewsJson();return json==null||json.isBlank()?mapper.createArrayNode():mapper.readTree(json);}catch(JsonProcessingException e){return mapper.createArrayNode();}}
    private String raw(TrainingSession session){return session.getCorrectedText()==null?session.getExtractedText():session.getCorrectedText();}
    private String text(JsonNode node,String name){JsonNode value=node.get(name);return value==null||value.isNull()?"":value.asText();}
    private String value(String text,String fallback){return text==null||text.isBlank()?fallback:text;}
    private double percent(long correct,long total){return total==0?0:Math.round(correct*1000.0/total)/10.0;}
    private void element(XMLStreamWriter xml,String name,String value)throws XMLStreamException{xml.writeStartElement(name);xml.writeCharacters(value==null?"":value);xml.writeEndElement();}
}
