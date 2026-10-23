package de.ostms.lc.guarantee;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Guarantees of the company (customer side), only for tenants with the corporate profile (see TenantModulePolicy). Reading is open to signed-in users, writing needs the right to edit files. */
@RestController @RequestMapping("/api/guarantees")
public class GuaranteeController {
 private final GuaranteeService service;
 public GuaranteeController(GuaranteeService service){this.service=service;}
 @GetMapping public List<GuaranteeService.View> list(){return service.list(LocalDate.now());}
 @GetMapping("/options") public Map<String,Object> options(){return Map.of("types",GuaranteeService.TYPES,"statuses",GuaranteeService.STATUSES);}
 @GetMapping("/{lcId}") public GuaranteeService.View one(@PathVariable UUID lcId){return service.one(lcId,LocalDate.now());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public GuaranteeService.View create(@RequestBody GuaranteeService.Request request,Authentication auth){return service.create(request,auth);}
 @PutMapping("/{lcId}") public GuaranteeService.View update(@PathVariable UUID lcId,@RequestBody GuaranteeService.Request request,Authentication auth){return service.update(lcId,request,auth);}
 @PostMapping("/{lcId}/status") public GuaranteeService.View status(@PathVariable UUID lcId,@RequestBody GuaranteeService.StatusRequest request,Authentication auth){return service.setStatus(lcId,request,auth);}
}
