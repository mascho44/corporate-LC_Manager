package de.corporate.lc.check.api;

import de.corporate.lc.check.service.*;
import de.corporate.lc.document.repository.LcDocumentRepository;
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
    public record View(CheckResult finding,UUID documentId,String contentType,EvidenceLocator.Location location){}
    @GetMapping("/api/lcs/{lcId}/document-checks/evidence") @Transactional(readOnly=true)
    public View evidence(@PathVariable UUID lcId,@RequestParam String code,@RequestParam(required=false) String documentName,@RequestParam(required=false) String reviewFingerprint){
        var findings=checks.check(lcId).results().stream().filter(r->Objects.equals(r.code(),code)&&Objects.equals(r.documentName(),documentName)&&(reviewFingerprint==null||reviewFingerprint.equals(r.reviewFingerprint()))).toList();
        if(findings.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Befund nicht eindeutig oder nicht mehr aktuell. Bitte Prüfung neu laden.");
        var finding=findings.get(0);
        var matches=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId).stream().filter(d->finding.documentName()!=null&&Objects.equals(d.getOriginalFilename(),finding.documentName())).toList();
        if(matches.size()!=1)return new View(finding,null,null,new EvidenceLocator.Location(matches.isEmpty()?"NO_DOCUMENT":"AMBIGUOUS_DOCUMENT","NONE",List.of()));
        var doc=matches.get(0);
        return new View(finding,doc.getId(),doc.getContentType(),EvidenceLocator.locate(doc,finding.documentEvidence()));
    }
    public View evidence(UUID lcId,String code,String documentName){return evidence(lcId,code,documentName,null);}
}
