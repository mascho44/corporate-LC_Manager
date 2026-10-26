package de.ostms.lc.calendarfeed;

import de.ostms.lc.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** The public calendar link (no login: the secret in the path is the credential) and its management by the person themselves. */
@RestController
public class CalendarFeedController {
 private final CalendarFeedService service;private final AuditService audit;private final String baseUrl;
 public CalendarFeedController(CalendarFeedService service,AuditService audit,@Value("${app.security.public-base-url:}") String baseUrl){this.service=service;this.audit=audit;this.baseUrl=baseUrl==null?"":baseUrl.strip().replaceAll("/+$","");}

 public record Subscription(boolean active,String prefix,java.time.LocalDateTime createdAt,java.time.LocalDateTime lastUsedAt,String url){}
 private static Subscription of(CalendarFeedService.Status s,String url){return new Subscription(s.active(),s.prefix(),s.createdAt(),s.lastUsedAt(),url);}

 @GetMapping(value="/calendar/feed/{secret}",produces="text/calendar")
 public ResponseEntity<byte[]> feed(@PathVariable String secret){
  var content=service.export(secret.endsWith(".ics")?secret.substring(0,secret.length()-4):secret);
  if(content.isEmpty())return ResponseEntity.notFound().build();
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8")).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(content.get());
 }
 @GetMapping("/api/profile/calendar") public Subscription status(Authentication auth){return of(service.status(auth.getName()),null);}
 @PostMapping("/api/profile/calendar") public Subscription create(Authentication auth,HttpServletRequest request){
  var created=service.create(auth.getName());
  audit.record(auth,"CALENDAR_FEED_CREATED","USER",auth.getName(),"Kalender-Abo erstellt");
  String origin=baseUrl.isEmpty()?request.getScheme()+"://"+request.getServerName()+((request.getServerPort()==80||request.getServerPort()==443)?"":":"+request.getServerPort()):baseUrl;
  return of(created.status(),origin+request.getContextPath()+"/calendar/feed/"+created.secret()+".ics");
 }
 @DeleteMapping("/api/profile/calendar") public Subscription revoke(Authentication auth){
  service.revoke(auth.getName());audit.record(auth,"CALENDAR_FEED_REVOKED","USER",auth.getName(),"Kalender-Abo beendet");return of(service.status(auth.getName()),null);
 }
}
