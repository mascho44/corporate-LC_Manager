package de.corporate.lc.rulepack;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.io.IOException;
import java.util.*;

@RestController @RequestMapping("/api/settings/rule-packs")
public class InternalPackController {
 private final InternalPackService service;
 public InternalPackController(InternalPackService s){service=s;}
 @GetMapping public List<InternalPackService.View> list(){return service.list();}
 @PostMapping("/preview") public InternalPackService.Preview preview(HttpServletRequest request)throws IOException{return service.preview(body(request));}
 @PostMapping public StoredPackVersion importPack(HttpServletRequest request,Authentication auth)throws IOException{return service.importPack(body(request),auth);}
 @PostMapping("/{id}/test") public InternalPackService.Preview test(@PathVariable UUID id){return service.test(id);}
 public record Activation(boolean rightsConfirmed){}
 @PostMapping("/{id}/activate") public void activate(@PathVariable UUID id,@RequestBody Activation request,Authentication auth){service.activate(id,request.rightsConfirmed(),auth);}
 @PostMapping("/{packId}/deactivate") public void deactivate(@PathVariable String packId,Authentication auth){service.deactivate(packId,auth);}
 private byte[] body(HttpServletRequest request)throws IOException{
  var bytes=request.getInputStream().readNBytes(PackCodec.MAX_BYTES+1);
  if(bytes.length>PackCodec.MAX_BYTES)throw new IllegalArgumentException("Rule Pack überschreitet 512 KB.");
  return bytes;
 }
}
