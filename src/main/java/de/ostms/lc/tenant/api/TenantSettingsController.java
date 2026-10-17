package de.ostms.lc.tenant.api;
import de.ostms.lc.tenant.service.TenantSettingsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/tenants/current/settings")
public class TenantSettingsController {
 public record Update(String name,String defaultLanguage){}
 public record OcrUpdate(Double threshold){}
 private final TenantSettingsService service;
 public TenantSettingsController(TenantSettingsService service){this.service=service;}
 @GetMapping public TenantSettingsService.Settings get(Authentication auth){return service.get(auth);}
 @PutMapping public TenantSettingsService.Settings update(@RequestBody Update request,Authentication auth){return service.update(request.name(),request.defaultLanguage(),auth);}
 @GetMapping("/ocr") public TenantSettingsService.OcrSettings ocr(Authentication auth){return service.ocr(auth);}
 @PutMapping("/ocr") public TenantSettingsService.OcrSettings updateOcr(@RequestBody OcrUpdate request,Authentication auth){return service.updateOcr(request.threshold(),auth);}
}
