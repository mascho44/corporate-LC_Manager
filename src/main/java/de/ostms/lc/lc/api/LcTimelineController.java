package de.ostms.lc.lc.api;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.service.LcTimelineService;
import de.ostms.lc.lc.service.LetterOfCreditService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/lcs/{lcId}")
public class LcTimelineController {
    private final LcTimelineService timeline;
    private final LetterOfCreditService lettersOfCredit;
    private final AuditService audit;

    public LcTimelineController(LcTimelineService timeline, LetterOfCreditService lettersOfCredit, AuditService audit) {
        this.timeline = timeline; this.lettersOfCredit = lettersOfCredit; this.audit = audit;
    }

    @GetMapping("/timeline")
    public List<LcTimelineEntry> timeline(@PathVariable UUID lcId) { return timeline.timeline(lcId); }

    @PostMapping("/notes")
    public LcTimelineEntry addNote(@PathVariable UUID lcId, @Valid @RequestBody LcNoteRequest request, Authentication authentication) {
        var note = timeline.addNote(lcId, authentication.getName(), request.content());
        audit.record(authentication, "LC_NOTE_ADDED", "LETTER_OF_CREDIT", lcId, note.getContent());
        return new LcTimelineEntry(note.getId(), "NOTE", "LC_NOTE_ADDED", note.getUsername(), note.getContent(), note.getCreatedAt());
    }

    @PostMapping("/follow-up/complete")
    public LetterOfCredit completeFollowUp(@PathVariable UUID lcId, Authentication authentication) {
        LetterOfCredit lc = lettersOfCredit.completeFollowUp(lcId);
        audit.record(authentication, "LC_FOLLOW_UP_COMPLETED", "LETTER_OF_CREDIT", lcId, lc.getReference());
        return lc;
    }

    @PostMapping("/assign-to-me")
    public LetterOfCredit assignToMe(@PathVariable UUID lcId, Authentication authentication) {
        String before = lettersOfCredit.one(lcId).getAssignedTo();
        LetterOfCredit lc = lettersOfCredit.assignTo(lcId, authentication.getName());
        audit.recordChange(authentication, "LC_ASSIGNED", "LETTER_OF_CREDIT", lcId, lc.getReference() + " · " + authentication.getName(),
                de.ostms.lc.audit.service.AuditSnapshots.assignment(before), de.ostms.lc.audit.service.AuditSnapshots.assignment(lc.getAssignedTo()));
        return lc;
    }
}
