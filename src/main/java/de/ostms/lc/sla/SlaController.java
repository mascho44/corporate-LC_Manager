package de.ostms.lc.sla;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SLA rules (settings) and the SLA status of a file. Only tenants with the bank profile (see TenantModulePolicy). */
@RestController @RequestMapping("/api")
public class SlaController {
 private final SlaService service;
 public SlaController(SlaService service){this.service=service;}
 @GetMapping("/sla/rules") public List<SlaService.RuleView> rules(){return service.list();}
 @GetMapping("/sla/catalog") public Map<String,Object> catalog(){return Map.of("activities",SlaCatalog.ACTIVITIES,"actions",service.knownActions());}
 @PostMapping("/sla/rules") @ResponseStatus(HttpStatus.CREATED) public SlaService.RuleView create(@RequestBody SlaService.RuleRequest request,Authentication auth){return service.create(request,auth);}
 @PutMapping("/sla/rules/{id}") public SlaService.RuleView update(@PathVariable UUID id,@RequestBody SlaService.RuleRequest request,Authentication auth){return service.update(id,request,auth);}
 @DeleteMapping("/sla/rules/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth);}
 @GetMapping("/lcs/sla-status") public List<SlaService.ListEntry> listStates(){return service.listStates(LocalDate.now());}
 @GetMapping("/lcs/{lcId}/sla") public SlaService.FileStatus status(@PathVariable UUID lcId){return service.status(lcId,LocalDate.now());}
}
