package de.corporate.lc.check.api;
import de.corporate.lc.check.service.RulePacks;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class RulePackController {
    @GetMapping("/api/rule-packs")
    public List<RulePacks.Pack> packs() { return RulePacks.all(); }
}
