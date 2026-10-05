package de.corporate.lc.check.api;

import de.corporate.lc.check.service.ValidationSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/lcs/{lcId}/document-checks/simulation")
public class ValidationSimulationController {
    private final ValidationSimulationService service;
    public ValidationSimulationController(ValidationSimulationService service){this.service=service;}
    @PostMapping public ReviewSummary simulate(@PathVariable UUID lcId,@Valid @RequestBody SimulationRequest request){return service.simulate(lcId,request);}
}
