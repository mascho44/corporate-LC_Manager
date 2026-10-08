package de.ostms.lc.check.api;

import de.ostms.lc.check.service.RuleCatalog;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/check-rules")
public class RuleCatalogController {
    public record Catalog(String version,List<RuleDefinition> rules){}
    @GetMapping public Catalog catalog(){return new Catalog(RuleCatalog.VERSION,RuleCatalog.definitions());}
}
