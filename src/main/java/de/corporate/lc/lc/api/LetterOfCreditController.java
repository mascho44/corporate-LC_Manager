package de.corporate.lc.lc.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.service.LetterOfCreditService;
import de.corporate.lc.check.service.DocumentCheckService;
import de.corporate.lc.document.repository.LcDocumentRepository;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lcs")
public class LetterOfCreditController {
    private final LetterOfCreditService service;
    private final AuditService audit;
    private final DocumentCheckService checks;
    private final LcDocumentRepository documents;

    public LetterOfCreditController(LetterOfCreditService service, AuditService audit,DocumentCheckService checks,LcDocumentRepository documents) {
        this.service = service;
        this.audit = audit;
        this.checks = checks;
        this.documents=documents;
    }

    @PostMapping(value = "/import/mt700", consumes = MediaType.TEXT_PLAIN_VALUE)
    public LetterOfCredit importMt700(@RequestBody String raw, Authentication authentication) {
        LetterOfCredit lc = service.importMt700(raw);
        audit.record(authentication, "LC_IMPORTED", "LETTER_OF_CREDIT", lc.getId(), lc.getReference());
        return lc;
    }

    @GetMapping
    public List<LetterOfCredit> all() {
        return service.all();
    }

    @GetMapping("/dossier-status")
    public List<LcDossierStatus> dossierStatus(){return service.all().stream().map(lc->{var review=checks.check(lc.getId());return new LcDossierStatus(lc.getId(),review.status(),documents.countByLetterOfCreditId(lc.getId()),review.discrepancies(),review.warnings(),review.passed());}).toList();}

    @GetMapping("/{id}")
    public LetterOfCredit one(@PathVariable UUID id) {
        return service.one(id);
    }

    @PutMapping("/{id}")
    public LetterOfCredit update(@PathVariable UUID id, @Valid @RequestBody LetterOfCreditUpdateRequest request, Authentication authentication) {
        String previous=auditState(service.one(id));
        LetterOfCredit lc = service.update(id, request);
        long reset=checks.invalidateDecisions(id);
        String assignment = lc.getAssignedTo() == null ? "nicht zugewiesen" : lc.getAssignedTo();
        String followUp = lc.getFollowUpDate() == null ? "keine Wiedervorlage" : "Wiedervorlage " + lc.getFollowUpDate();
        audit.recordChange(authentication, "LC_UPDATED", "LETTER_OF_CREDIT", id, lc.getReference() + " · " + assignment + " · " + followUp,previous,auditState(lc));
        if(reset>0)audit.record(authentication,"DOCUMENT_CHECK_DECISIONS_RESET","LETTER_OF_CREDIT",id,reset+" Entscheidungen wegen LC-Änderung zurückgesetzt");
        return lc;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        String reference = service.one(id).getReference();
        service.delete(id);
        audit.record(authentication, "LC_DELETED", "LETTER_OF_CREDIT", id, reference);
        return ResponseEntity.noContent().build();
    }

    private String auditState(LetterOfCredit lc){return "Referenz="+value(lc.getReference())+" | Status="+value(lc.getStatus())+" | Applicant="+value(lc.getApplicant())+" | Beneficiary="+value(lc.getBeneficiary())+" | Betrag="+value(lc.getCurrency())+" "+value(lc.getAmount())+" | Ablauf="+value(lc.getExpiryDate())+" | Versand="+value(lc.getLatestShipmentDate())+" | Bearbeiter="+value(lc.getAssignedTo())+" | Wiedervorlage="+value(lc.getFollowUpDate())+" | Dokumentenanforderungen="+lc.getRequiredDocuments().size();}
    private String value(Object value){return value==null?"-":String.valueOf(value);}
}
