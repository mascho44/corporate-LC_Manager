package de.ostms.lc.ebics;
import de.ostms.lc.imports.api.SwiftImportPreview;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/ebics/messages")
public class EbicsMessageController {
 private final EbicsMessageService service;
 public EbicsMessageController(EbicsMessageService s){service=s;}
 @GetMapping public List<EbicsMessageService.View> list(){return service.list();}
 @PostMapping("/fetch") public EbicsMessageService.FetchResult fetch(Authentication auth){return service.fetch(auth);}
 @GetMapping("/{id}/preview") public SwiftImportPreview preview(@PathVariable UUID id){return service.preview(id);}
 @PostMapping("/{id}/import") public Object importMessage(@PathVariable UUID id,Authentication auth){return service.importMessage(id,auth);}
 @PostMapping("/{id}/discard") public EbicsMessageService.View discard(@PathVariable UUID id,Authentication auth){return service.discard(id,auth);}
}
