package de.ostms.lc.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Webhooks of the tenant. Everything needs the settings right (see SecurityConfig): deliveries carry business events. */
@RestController @RequestMapping("/api/webhooks")
public class WebhookController {
 private final WebhookService service;
 public WebhookController(WebhookService service){this.service=service;}
 @GetMapping public List<WebhookService.View> list(){return service.list();}
 @GetMapping("/events") public List<WebhookService.EventInfo> events(){return service.catalog();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public WebhookService.Created create(@RequestBody WebhookService.Request request,Authentication auth){return service.create(request,auth);}
 @PutMapping("/{id}") public WebhookService.View update(@PathVariable UUID id,@RequestBody WebhookService.Request request,Authentication auth){return service.update(id,request,auth);}
 @PostMapping("/{id}/secret") public WebhookService.Created rotate(@PathVariable UUID id,Authentication auth){return service.rotateSecret(id,auth);}
 @PostMapping("/{id}/test") @ResponseStatus(HttpStatus.ACCEPTED) public WebhookDelivery test(@PathVariable UUID id,Authentication auth){return service.test(id,auth);}
 @GetMapping("/{id}/deliveries") public List<WebhookDelivery> deliveries(@PathVariable UUID id){return service.deliveries(id);}
 @PostMapping("/deliveries/{id}/retry") public WebhookDelivery retry(@PathVariable UUID id,Authentication auth){return service.retry(id,auth);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){service.delete(id,auth);}
}
