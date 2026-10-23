package de.ostms.lc.creditline;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lines of the company (corporate profile only, see TenantModulePolicy). Reading and the check are open to signed-in users, changing needs the right to manage settings. */
@RestController @RequestMapping("/api/credit-lines")
public class CreditLineController {
 private final CreditLineService service;
 public CreditLineController(CreditLineService service){this.service=service;}
 @GetMapping public CreditLineService.Overview overview(){return service.overview(LocalDate.now());}
 @GetMapping("/check") public CreditLineService.Check check(@RequestParam String bank,@RequestParam String currency,@RequestParam(required=false) String type,@RequestParam(required=false) BigDecimal amount,@RequestParam(required=false) UUID exclude){return service.check(bank,currency,type,amount,exclude,LocalDate.now());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public CreditLineService.View create(@RequestBody CreditLineService.Request request,Authentication auth){return service.create(request,auth);}
 @PutMapping("/{id}") public CreditLineService.View update(@PathVariable UUID id,@RequestBody CreditLineService.Request request,Authentication auth){return service.update(id,request,auth);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth);}
}
