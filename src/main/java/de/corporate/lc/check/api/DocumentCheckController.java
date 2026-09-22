package de.corporate.lc.check.api;

import de.corporate.lc.check.service.DocumentCheckService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/lcs/{lcId}/document-checks")
public class DocumentCheckController {
    private final DocumentCheckService service;
    public DocumentCheckController(DocumentCheckService service) { this.service = service; }
    @GetMapping public ReviewSummary check(@PathVariable UUID lcId) { return service.check(lcId); }
}
