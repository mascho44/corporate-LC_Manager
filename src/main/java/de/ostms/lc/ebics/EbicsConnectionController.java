package de.ostms.lc.ebics;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ebics/connection")
public class EbicsConnectionController {
 private final EbicsConnectionService service;
 public EbicsConnectionController(EbicsConnectionService s){service=s;}
 @GetMapping public EbicsConnectionService.View get(){return service.view();}
 @PutMapping public EbicsConnectionService.View save(@RequestBody EbicsConnectionService.Request request,Authentication auth){return service.save(request,auth);}
 @PostMapping("/keys") public EbicsConnectionService.Fingerprints keys(Authentication auth){return service.initialise(auth);}
 @PostMapping("/bank-keys") public EbicsConnectionService.Fingerprints bankKeys(Authentication auth){return service.fetchBankKeys(auth);}
 @GetMapping("/fingerprints") public EbicsConnectionService.Fingerprints fingerprints(){return service.fingerprints();}
 @PutMapping("/auto-fetch") public EbicsConnectionService.View autoFetch(@RequestBody EbicsConnectionService.AutoFetchRequest request,Authentication auth){return service.setAutoFetch(request,auth);}
 @PostMapping("/reset") public EbicsConnectionService.View reset(Authentication auth){return service.reset(auth);}
}
