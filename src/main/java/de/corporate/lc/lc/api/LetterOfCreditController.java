package de.corporate.lc.lc.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.service.LetterOfCreditService;
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

    public LetterOfCreditController(LetterOfCreditService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
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

    @GetMapping("/{id}")
    public LetterOfCredit one(@PathVariable UUID id) {
        return service.one(id);
    }

    @PutMapping("/{id}")
    public LetterOfCredit update(@PathVariable UUID id, @Valid @RequestBody LetterOfCreditUpdateRequest request, Authentication authentication) {
        LetterOfCredit lc = service.update(id, request);
        audit.record(authentication, "LC_UPDATED", "LETTER_OF_CREDIT", id, lc.getReference());
        return lc;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        String reference = service.one(id).getReference();
        service.delete(id);
        audit.record(authentication, "LC_DELETED", "LETTER_OF_CREDIT", id, reference);
        return ResponseEntity.noContent().build();
    }
}
