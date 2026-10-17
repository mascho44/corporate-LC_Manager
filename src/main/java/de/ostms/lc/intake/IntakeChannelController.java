package de.ostms.lc.intake;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Administration of the intake channels (watched folder); needs the settings permission. */
@RestController @RequestMapping("/api/intake/channels")
public class IntakeChannelController {
 private final IntakeChannelService service;
 public IntakeChannelController(IntakeChannelService service){this.service=service;}
 @GetMapping public List<IntakeChannelService.View> list(){return service.list();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public IntakeChannelService.View create(@RequestBody IntakeChannelService.Request request,Authentication auth)throws IOException{return service.create(request,auth);}
 @PutMapping("/{id}") public IntakeChannelService.View update(@PathVariable UUID id,@RequestBody IntakeChannelService.Request request,Authentication auth){return service.update(id,request,auth);}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth);return ResponseEntity.noContent().build();}
 @PostMapping("/{id}/test") public java.util.Map<String,String> test(@PathVariable UUID id,Authentication auth){return java.util.Map.of("result",service.test(id,auth));}
 @PostMapping("/{id}/run") public IntakeChannelService.View run(@PathVariable UUID id,Authentication auth){return service.runNow(id,auth);}
}
