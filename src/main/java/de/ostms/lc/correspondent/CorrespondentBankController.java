package de.ostms.lc.correspondent;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Correspondent banks of the tenant. Writing needs the settings right (see SecurityConfig). */
@RestController @RequestMapping("/api/correspondent-banks")
public class CorrespondentBankController {
 private final CorrespondentBankService service;
 public CorrespondentBankController(CorrespondentBankService service){this.service=service;}
 @GetMapping public List<CorrespondentBankService.View> list(){return service.list();}
 @GetMapping("/channels") public Map<String,Object> channels(){return Map.of("channels",CorrespondentBankService.CHANNELS);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public CorrespondentBankService.View create(@RequestBody CorrespondentBankService.Request request,Authentication auth){return service.create(request,auth);}
 @PutMapping("/{id}") public CorrespondentBankService.View update(@PathVariable UUID id,@RequestBody CorrespondentBankService.Request request,Authentication auth){return service.update(id,request,auth);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth);}
}
