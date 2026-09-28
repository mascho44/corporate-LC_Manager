package de.corporate.lc.lc.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.lc.service.LcCalendarService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class LcCalendarController {
    private final LcCalendarService calendar;
    private final AuditService audit;
    public LcCalendarController(LcCalendarService calendar, AuditService audit){this.calendar=calendar;this.audit=audit;}
    @GetMapping(value="/api/calendar.ics",produces="text/calendar")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue="all") String scope, Authentication authentication){
        if(!scope.equals("all")&&!scope.equals("mine")&&!scope.equals("unassigned"))throw new IllegalArgumentException("Unbekannter Kalenderumfang.");
        byte[] content=calendar.export(scope,authentication.getName());
        audit.record(authentication,"CALENDAR_EXPORTED","CALENDAR",null,"Umfang: "+scope);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=LC-Fristen.ics").contentLength(content.length).body(content);
    }
}
