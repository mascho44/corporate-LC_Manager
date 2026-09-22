package de.corporate.lc.audit.api;
import de.corporate.lc.audit.domain.AuditEvent; import de.corporate.lc.audit.service.AuditService; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/audit") public class AuditController {private final AuditService service;public AuditController(AuditService s){service=s;}@GetMapping public List<AuditEvent> recent(){return service.recent();}}
