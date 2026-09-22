package de.corporate.lc.training.api;

import de.corporate.lc.document.service.DocumentExtractionService;
import de.corporate.lc.imports.api.*;
import de.corporate.lc.imports.service.SwiftImportService;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.swift.PrintedSwiftNormalizer;
import de.corporate.lc.training.domain.TrainingSession;
import de.corporate.lc.training.repository.TrainingSessionRepository;
import de.corporate.lc.training.service.TrainingDataService;
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

    public TrainingController(TrainingSessionRepository r, DocumentExtractionService e,
                              PrintedSwiftNormalizer n, SwiftImportService i, TrainingDataService d) {
        repo=r; extraction=e; normalizer=n; imports=i; data=d;
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
        return new TrainingPreview(s.getId(),request,preview,x.status());
    }

    @PostMapping("/{id}/confirm")
    @Transactional
    public Object confirm(@PathVariable UUID id,@RequestBody TrainingConfirm request,Authentication auth) {
        TrainingSession s=find(id);
        if(!s.getUsername().equals(auth.getName())) throw new IllegalArgumentException("Trainingssitzung gehört einem anderen Benutzer.");
        var corrected=new SwiftImportRequest(s.getFilename(),request.correctedRawMessage());
        var preview=imports.preview(corrected);
        if(!preview.valid()) throw new IllegalArgumentException(String.join(" ",preview.errors()));
        Object result=preview.messageType().equals("MT760")
                ? Map.of("status","CONFIRMED","messageType","MT760","trainingSessionId",id)
                : imports.execute(corrected);
        s.setCorrectedText(request.correctedRawMessage()); s.setReviewsJson(request.reviewsJson());
        s.setStatus("CONFIRMED"); s.setMessageType(preview.messageType()); s.setConfirmedAt(LocalDateTime.now());
        if(result instanceof LetterOfCredit lc) s.setLcId(lc.getId());
        return result;
    }

    @GetMapping
    public List<TrainingView> history() {
        return repo.findTop100ByOrderByCreatedAtDesc().stream()
                .map(s->new TrainingView(s.getId(),s.getFilename(),s.getStatus(),s.getUsername(),s.getMessageType(),s.getCreatedAt())).toList();
    }

    @GetMapping("/quality")
    public List<TrainingDataService.ProfileQuality> quality(){return data.quality(repo.findAll());}

    @GetMapping("/{id}")
    public TrainingDataService.Detail detail(@PathVariable UUID id){return data.detail(find(id));}

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable UUID id){
        TrainingSession s=find(id); String type=s.getContentType()==null?MediaType.APPLICATION_PDF_VALUE:s.getContentType();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+filename(s.getFilename(),"training.pdf")+"\"")
                .body(s.getOriginalPdf());
    }

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
