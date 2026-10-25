package de.ostms.lc.lc.service;

import de.ostms.lc.audit.repository.AuditEventRepository;
import de.ostms.lc.lc.api.LcTimelineEntry;
import de.ostms.lc.lc.domain.LcNote;
import de.ostms.lc.lc.repository.LcNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class LcTimelineService {
    private final LcNoteRepository notes;
    private final AuditEventRepository audit;
    private final LetterOfCreditService lettersOfCredit;

    public LcTimelineService(LcNoteRepository notes, AuditEventRepository audit, LetterOfCreditService lettersOfCredit) {
        this.notes = notes; this.audit = audit; this.lettersOfCredit = lettersOfCredit;
    }

    public List<LcTimelineEntry> timeline(UUID lcId) {
        lettersOfCredit.one(lcId);
        List<LcTimelineEntry> entries = new ArrayList<>();
        notes.findTop100ByLetterOfCreditIdOrderByCreatedAtDesc(lcId).forEach(note -> entries.add(
                new LcTimelineEntry(note.getId(), "NOTE", "LC_NOTE_ADDED", note.getUsername(), note.getContent(), note.getCreatedAt())));
        audit.findTop100ByEntityIdOrderByOccurredAtDesc(lcId.toString()).stream()
                .filter(event -> !"LC_NOTE_ADDED".equals(event.getAction()))
                .forEach(event -> entries.add(new LcTimelineEntry(event.getId(), "EVENT", event.getAction(), event.getUsername(), timelineDetails(event), event.getOccurredAt())));
        return entries.stream().sorted(Comparator.comparing(LcTimelineEntry::occurredAt).reversed()).limit(100).toList();
    }

    private String timelineDetails(de.ostms.lc.audit.domain.AuditEvent event){String details=event.getDetails()==null?"":event.getDetails();if(event.getPreviousValue()!=null||event.getNewValue()!=null)details+=(details.isBlank()?"":"\n")+"Vorher: "+Objects.toString(event.getPreviousValue(),"-")+"\nNachher: "+Objects.toString(event.getNewValue(),"-");return details;}

    @Transactional
    public LcNote addNote(UUID lcId, String username, String content) {
        lettersOfCredit.one(lcId);
        LcNote note = new LcNote();
        note.setLetterOfCreditId(lcId);
        note.setUsername(username);
        note.setContent(content.trim());
        return notes.save(note);
    }
}
