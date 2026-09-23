package de.corporate.lc.training.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.service.DocumentExtractionService;
import de.corporate.lc.imports.api.*;
import de.corporate.lc.imports.service.SwiftImportService;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.swift.PrintedSwiftNormalizer;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import de.corporate.lc.training.service.TrainingDataService;
import de.corporate.lc.training.service.TrainingLearningService;
import de.corporate.lc.training.service.TrainingQualityService;
import de.corporate.lc.training.service.PdfFieldSnippetService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/training")
public class TrainingController {
    private final TrainingSessionRepository repo;
    private final DocumentExtractionService extraction;
    private final PrintedSwiftNormalizer normalizer;
    private final SwiftImportService imports;
    private final TrainingDataService data;
    private final PdfFieldSnippetService snippets;
    private final AuditService audit;
    private final LetterOfCreditRepository lcs;
    private final TrainingLearningService learning;
    private final TrainingQualityService quality;

    public TrainingController(TrainingSessionRepository r, DocumentExtractionService e,
                              PrintedSwiftNormalizer n, SwiftImportService i, TrainingDataService d,
                              PdfFieldSnippetService p,AuditService a,LetterOfCreditRepository lcs,TrainingLearningService learning,TrainingQualityService quality) {
        repo=r; extraction=e; normalizer=n; imports=i; data=d; snippets=p; audit=a;this.lcs=lcs;this.learning=learning;this.quality=quality;
    }

    @PostMapping("/preview")
    @Transactional
    public TrainingPreview preview(@RequestPart("file") MultipartFile file, Authentication auth) throws IOException {
        if(file.isEmpty()||file.getSize()>10*1024*1024) throw new IllegalArgumentException("PDF fehlt oder ist größer als 10 MB.");
        var x=extraction.extractFile(file.getBytes(),file.getOriginalFilename(),file.getContentType());
        String text=normalizer.normalize(x.text());
        if(text.isBlank()) throw new IllegalArgumentException("Keine SWIFT-Felder erkannt.");
        var request=new SwiftImportRequest(file.getOriginalFilename(),text);
        var preview=imports.preview(request);
        TrainingSession s=new TrainingSession();
        s.setFilename(file.getOriginalFilename()); s.setContentType(file.getContentType());
        s.setOriginalPdf(file.getBytes()); s.setExtractedText(text); s.setStatus("DRAFT");
        s.setUsername(auth.getName()); s.setExtractionStatus(x.status()); s.setMessageType(preview.messageType());
        repo.save(s);
        audit.record(auth,"TRAINING_STARTED","TRAINING_SESSION",s.getId(),s.getFilename()+" · "+preview.messageType()+" · "+preview.rawFields().size()+" Felder erkannt");
        return new TrainingPreview(s.getId(),request,preview,x.status());
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    public Object confirm(@PathVariable UUID id,@RequestBody TrainingConfirm request,Authentication auth) {
        TrainingSession s=find(id);
        if(!s.getUsername().equals(auth.getName())) throw new IllegalArgumentException("Trainingssitzung gehört einem anderen Benutzer.");
        var corrected=new SwiftImportRequest(s.getFilename(),request.correctedRawMessage());
        var qualityResult=quality.validate(s.getFilename(),request);
        if("RED".equals(qualityResult.status()))throw new IllegalArgumentException(String.join(" ",qualityResult.blockers()));
        var preview=imports.previewCorrected(corrected);
        boolean existingMt700=preview.messageType().equals("MT700")&&preview.duplicate()&&preview.reference()!=null
                &&preview.errors().stream().allMatch(error->error.contains("existiert bereits"));
        if(!preview.valid()&&!existingMt700) throw new IllegalArgumentException(String.join(" ",preview.errors()));
        Object result=existingMt700
                ?lcs.findByReference(preview.reference()).orElseThrow(()->new IllegalArgumentException("Vorhandenes Akkreditiv wurde nicht gefunden."))
                :preview.messageType().equals("MT760")
                ? Map.of("status","CONFIRMED","messageType","MT760","trainingSessionId",id)
                : imports.executeCorrected(corrected);
        s.setCorrectedText(request.correctedRawMessage()); s.setReviewsJson(request.reviewsJson());
        s.setStatus("CONFIRMED"); s.setMessageType(preview.messageType()); s.setConfirmedAt(LocalDateTime.now());
        if(result instanceof LetterOfCredit lc) s.setLcId(lc.getId());
        audit.record(auth,"TRAINING_CONFIRMED","TRAINING_SESSION",s.getId(),s.getFilename()+" · "+preview.messageType()+" · Training abgeschlossen"+(s.getLcId()==null?"":existingMt700?" · mit bestehendem LC verknüpft":" · LC angelegt"));
        Map<String,Object> response=new LinkedHashMap<>();response.put("status","CONFIRMED");response.put("messageType",preview.messageType());response.put("trainingSessionId",id);if(s.getLcId()!=null)response.put("lcId",s.getLcId());return response;
    }

    @PutMapping("/{id}/draft")
    @Transactional
    public Map<String,String> saveDraft(@PathVariable UUID id,@RequestBody TrainingConfirm request,Authentication auth) {
        TrainingSession s=find(id);
        if(!s.getUsername().equals(auth.getName())) throw new IllegalArgumentException("Trainingssitzung gehört einem anderen Benutzer.");
        if("CONFIRMED".equals(s.getStatus())) throw new IllegalArgumentException("Ein bestätigter Trainingsdatensatz kann nicht mehr verändert werden.");
        s.setCorrectedText(request.correctedRawMessage());
        s.setReviewsJson(request.reviewsJson());
        audit.record(auth,"TRAINING_PROGRESS_SAVED","TRAINING_SESSION",s.getId(),s.getFilename()+" · Bearbeitungsstand gespeichert");
        return Map.of("status","SAVED");
    }

    @PostMapping("/{id}/validate")
    public TrainingQualityService.Result validate(@PathVariable UUID id,@RequestBody TrainingConfirm request,Authentication auth){TrainingSession s=find(id);if(!s.getUsername().equals(auth.getName()))throw new IllegalArgumentException("Trainingssitzung gehört einem anderen Benutzer.");return quality.validate(s.getFilename(),request);}

    @GetMapping
    public List<TrainingView> history() {
        return repo.findTop100ByOrderByCreatedAtDesc().stream()
                .map(s->new TrainingView(s.getId(),s.getFilename(),s.getStatus(),s.getUsername(),s.getMessageType(),s.getCreatedAt())).toList();
    }

    @GetMapping("/quality")
    public List<TrainingDataService.ProfileQuality> quality(){return data.quality(repo.findAll());}

    @GetMapping("/learning-rules")
    public List<TrainingLearningService.LearningRule> learningRules(){return learning.rules();}

    @PutMapping("/learning-rules/{id}")
    public TrainingLearningService.LearningRule setLearningRule(@PathVariable String id,@RequestBody Map<String,Boolean> request,Authentication auth){
        boolean active=Boolean.TRUE.equals(request.get("active"));
        var rule=learning.setActive(id,active,auth.getName());
        audit.record(auth,active?"TRAINING_RULE_ACTIVATED":"TRAINING_RULE_DEACTIVATED","TRAINING_RULE",id,rule.messageType()+" · :"+rule.sourceCode()+": · "+rule.kind());
        return rule;
    }

    @GetMapping("/{id}")
    public TrainingDataService.Detail detail(@PathVariable UUID id){return data.detail(find(id));}

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable UUID id){
        TrainingSession s=find(id); String type=s.getContentType()==null?MediaType.APPLICATION_PDF_VALUE:s.getContentType();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+filename(s.getFilename(),"training.pdf")+"\"")
                .body(s.getOriginalPdf());
    }

    @GetMapping(value="/{id}/snippet/{index}",produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] snippet(@PathVariable UUID id,@PathVariable int index){return snippets.snippet(find(id),index);}

    @GetMapping("/{id}/export.json")
    public ResponseEntity<byte[]> json(@PathVariable UUID id){TrainingSession s=confirmed(id);return download(data.json(s),MediaType.APPLICATION_JSON,base(s)+".json");}

    @GetMapping("/{id}/export.xml")
    public ResponseEntity<byte[]> xml(@PathVariable UUID id){TrainingSession s=confirmed(id);return download(data.xml(s),MediaType.APPLICATION_XML,base(s)+".xml");}

    private TrainingSession find(UUID id){return repo.findById(id).orElseThrow(()->new IllegalArgumentException("Trainingsdatensatz wurde nicht gefunden."));}
    private TrainingSession confirmed(UUID id){TrainingSession s=find(id);if(!"CONFIRMED".equals(s.getStatus()))throw new IllegalArgumentException("Export ist erst nach der Bestätigung möglich.");return s;}
    private ResponseEntity<byte[]> download(byte[] body,MediaType type,String name){return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename(name,"training")+"\"").body(body);}
    private String base(TrainingSession s){String name=filename(s.getFilename(),"training");int dot=name.lastIndexOf('.');return (dot>0?name.substring(0,dot):name)+"-"+s.getMessageType();}
    private String filename(String value,String fallback){String clean=value==null?fallback:value.replaceAll("[^A-Za-z0-9._-]","_");return clean.isBlank()?fallback:clean;}
}
