package de.ostms.lc.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.training.api.TrainingConfirm;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

@Service
public class TrainingQualityService {
    private static final Pattern FIELD=Pattern.compile("(?m)^:(\\d{2}[A-Z]?):(.*?)(?=^:\\d{2}[A-Z]?:|\\z)",Pattern.DOTALL);
    private final SwiftImportService imports; private final TrainingLearningService learning; private final ObjectMapper mapper;
    public TrainingQualityService(SwiftImportService imports,TrainingLearningService learning,ObjectMapper mapper){this.imports=imports;this.learning=learning;this.mapper=mapper;}
    public record Result(String status,List<String> blockers,List<String> warnings,List<String> information){}

    public Result validate(String filename,TrainingConfirm request){
        String raw=request.correctedRawMessage()==null?"":request.correctedRawMessage();var preview=imports.previewCorrected(new SwiftImportRequest(filename,raw));
        List<String> blockers=new ArrayList<>(),warnings=new ArrayList<>(preview.warnings()),information=new ArrayList<>();
        preview.errors().forEach(error->{if(error.contains("existiert bereits")){information.add("Vorhandenes LC wird verknüpft; es wird kein Duplikat angelegt.");}else blockers.add(error);});
        Map<String,List<String>> fields=new LinkedHashMap<>();Matcher matcher=FIELD.matcher(raw.strip());while(matcher.find())fields.computeIfAbsent(matcher.group(1),x->new ArrayList<>()).add(matcher.group(2).trim());
        fields.forEach((code,values)->{if(values.size()>1)warnings.add("SWIFT-Feld :"+code+": kommt mehrfach vor.");});
        if(preview.amount()!=null&&preview.amount().signum()<=0)blockers.add("Betrag muss größer als null sein.");
        if(preview.currency()!=null&&!preview.currency().matches("[A-Z]{3}"))blockers.add("Währung muss aus einem dreistelligen ISO-Code bestehen.");
        if(preview.requiredDocuments().isEmpty())warnings.add("Keine Dokumentenanforderungen erkannt.");
        LocalDate expiry=date(fields.get("31D")),shipment=date(fields.get("44C"));if(expiry!=null&&shipment!=null&&shipment.isAfter(expiry))blockers.add("Feld :44C: liegt nach dem Ablaufdatum aus :31D:.");
        try{long changed=0;for(var field:mapper.readTree(request.reviewsJson()==null?"[]":request.reviewsJson())){String review=field.path("review").asText();if(review.equals("corrected")||review.equals("reassigned")||review.equals("invalid"))changed++;}if(changed>0)information.add(changed+" Feld(er) wurden im Training manuell geändert.");}catch(Exception ignored){}
        String learned=learning.apply(preview.messageType(),raw);if(!learned.equals(raw.strip()))warnings.add("Aktive Lernregeln würden diesen Stand verändern; deine manuell bestätigten Werte haben beim Abschluss Vorrang.");
        String status=!blockers.isEmpty()?"RED":!warnings.isEmpty()?"YELLOW":"GREEN";return new Result(status,List.copyOf(blockers),List.copyOf(new LinkedHashSet<>(warnings)),List.copyOf(information));
    }
    private LocalDate date(List<String> values){if(values==null||values.isEmpty())return null;String value=values.get(0).trim();try{Matcher printed=Pattern.compile("^(\\d{2})\\s*\\.\\s*(\\d{2})\\s*\\.\\s*(\\d{4}).*$",Pattern.DOTALL).matcher(value);if(printed.matches())return LocalDate.of(Integer.parseInt(printed.group(3)),Integer.parseInt(printed.group(2)),Integer.parseInt(printed.group(1)));return LocalDate.parse(value.substring(0,6),DateTimeFormatter.ofPattern("yyMMdd"));}catch(Exception ignored){return null;}}
}
