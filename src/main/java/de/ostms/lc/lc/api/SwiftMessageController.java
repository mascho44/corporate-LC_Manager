package de.ostms.lc.lc.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.SwiftMessage;
import de.ostms.lc.lc.service.SwiftMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class SwiftMessageController {
 private final SwiftMessageService service;private final AuditService audit;
 public SwiftMessageController(SwiftMessageService s,AuditService a){service=s;audit=a;}
 @GetMapping("/api/lcs/{lcId}/swift-messages") public List<SwiftMessage> forLc(@PathVariable UUID lcId){return service.forLc(lcId);}
 @GetMapping("/api/swift-messages/unassigned") public List<SwiftMessage> unassigned(){return service.unassigned();}
 @PostMapping("/api/swift-messages/{id}/assign") public SwiftMessage assign(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication auth){
  UUID lc;try{lc=UUID.fromString(body.get("lcId"));}catch(RuntimeException bad){throw new IllegalArgumentException("Bitte eine Akte auswählen.");}
  var m=service.assign(id,lc);audit.record(auth,"SWIFT_MESSAGE_ASSIGNED","LETTER_OF_CREDIT",lc,m.getMessageType()+" "+m.getReference());return m;
 }
}
