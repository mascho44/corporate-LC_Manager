package de.ostms.lc.check.api;

import de.ostms.lc.check.service.*;
import de.ostms.lc.document.repository.LcDocumentRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
public class FindingEvidenceController {
    private final DocumentCheckService checks;
    private final LcDocumentRepository documents;
    public FindingEvidenceController(DocumentCheckService checks,LcDocumentRepository documents){this.checks=checks;this.documents=documents;}
    public record View(CheckResult finding,UUID documentId,String contentType,EvidenceLocator.Location location,String mode){
        public View(CheckResult finding,UUID documentId,String contentType,EvidenceLocator.Location location){this(finding,documentId,contentType,location,"REVIEW");}
    }
    @GetMapping("/api/lcs/{lcId}/document-checks/evidence") @Transactional(readOnly=true)
    public View evidence(@PathVariable UUID lcId,@RequestParam String code,@RequestParam(required=false) String documentName,@RequestParam(required=false) String reviewFingerprint,@RequestParam(defaultValue="REVIEW") String mode){
        if(!Set.of("REVIEW","PRECHECK").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown review mode");
        var findings=("PRECHECK".equals(mode)?checks.precheck(lcId):checks.check(lcId)).results().stream().filter(r->Objects.equals(r.code(),code)&&Objects.equals(r.documentName(),documentName)&&(reviewFingerprint==null||reviewFingerprint.equals(r.reviewFingerprint()))).toList();
        if(findings.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Befund nicht eindeutig oder nicht mehr aktuell. Bitte Prüfung neu laden.");
        var finding=findings.get(0);
        var matches=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId).stream().filter(d->finding.documentName()!=null&&Objects.equals(d.getOriginalFilename(),finding.documentName())).toList();
        if(matches.size()!=1)return new View(finding,null,null,new EvidenceLocator.Location(matches.isEmpty()?"NO_DOCUMENT":"AMBIGUOUS_DOCUMENT","NONE",List.of()),mode);
        var doc=matches.get(0);
        return new View(finding,doc.getId(),doc.getContentType(),EvidenceLocator.locate(doc,finding.documentEvidence()),mode);
    }
    @org.springframework.beans.factory.annotation.Autowired private de.ostms.lc.document.service.FindingCropService crops;
    @GetMapping(value="/api/lcs/{lcId}/documents/{id}/finding-crop",produces="image/png") @Transactional(readOnly=true)
    public org.springframework.http.ResponseEntity<byte[]> crop(@PathVariable UUID lcId,@PathVariable UUID id,@RequestParam String kind)throws Exception{
        var doc=documents.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!doc.getLetterOfCredit().getId().equals(lcId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try{return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(crops.crop(doc,kind));}
        catch(NoSuchElementException none){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Kein Ausschnitt verfügbar.");}
    }
    public record PageMark(int page,int count,String severity){}
    /** Pages of a document that carry at least one open finding (warning or discrepancy), with the worst severity per page. */
    @GetMapping("/api/lcs/{lcId}/documents/{id}/finding-pages") @Transactional(readOnly=true)
    public List<PageMark> findingPages(@PathVariable UUID lcId,@PathVariable UUID id){
        var doc=documents.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!doc.getLetterOfCredit().getId().equals(lcId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return pageMarks(doc,checks.check(lcId).results());
    }
    static List<PageMark> pageMarks(de.ostms.lc.document.domain.LcDocument doc,List<CheckResult> results){
        var count=new TreeMap<Integer,Integer>();var worst=new HashMap<Integer,String>();int examined=0;
        for(var r:results){
            if(r.severity()==CheckResult.Severity.OK||!Objects.equals(r.documentName(),doc.getOriginalFilename())||r.documentEvidence()==null)continue;
            if(++examined>50)break;
            var location=EvidenceLocator.locate(doc,r.documentEvidence());
            if(!location.status().equals("MATCH")&&!location.status().equals("AMBIGUOUS"))continue;
            for(int page:location.pages()){
                count.merge(page,1,Integer::sum);
                if(!"DISCREPANCY".equals(worst.get(page)))worst.put(page,r.severity().name());
            }
        }
        return count.entrySet().stream().map(e->new PageMark(e.getKey(),e.getValue(),worst.get(e.getKey()))).toList();
    }
    public View evidence(UUID lcId,String code,String documentName){return evidence(lcId,code,documentName,null);}
    public View evidence(UUID lcId,String code,String documentName,String fingerprint){return evidence(lcId,code,documentName,fingerprint,"REVIEW");}
}
