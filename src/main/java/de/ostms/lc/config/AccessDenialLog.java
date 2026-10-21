package de.ostms.lc.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers refused requests (401/403) so that the cause of a "forbidden" can be found without guessing: method, path (no query, no body), user, tenant, address and a reason hint.
 * The same kind of refusal by the same user is logged at most once a minute (the count of suppressed repeats is added), and the latest entries stay in memory for the platform administration.
 */
@Component
public class AccessDenialLog {
 public record Entry(Instant at,int status,String method,String path,String user,String tenant,String address,String reason,int repeats){}
 private static final Logger LOG=LoggerFactory.getLogger("access-denied");
 private static final int KEEP=200,MAX_KEYS=5000;private static final long WINDOW_MS=60_000;
 private final Deque<Entry> recent=new ArrayDeque<>();
 private final Map<String,long[]> seen=new ConcurrentHashMap<>(); // key -> {lastLoggedAt, suppressed}

 /** Path with ids replaced, so that /api/lcs/{uuid} and /api/lcs/{another} count as the same kind of refusal. */
 static String shape(String path){return path.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}","{id}").replaceAll("/\\d+","/{n}");}
 /** Control characters and anything unusual out of values that come from the request. */
 static String clean(String value,int max){if(value==null)return "-";String v=value.replaceAll("[\\p{Cntrl}\\s]+"," ").trim();return v.length()>max?v.substring(0,max)+"…":(v.isEmpty()?"-":v);}

 public void record(int status,String method,String path,String user,String tenant,String address,String reason){
  // An anonymous visit of the start page (reachability checks, uptime monitors, browsers without session) is expected, not a refusal worth a warning.
  boolean anonymous=user==null||user.isBlank()||"-".equals(user);
  if(status==401&&anonymous&&"GET".equalsIgnoreCase(method)&&("/".equals(path)||"/index.html".equals(path))){LOG.debug("anonymous start page visit address={}",clean(address,60));return;}
  long now=System.currentTimeMillis();String shown=clean(path,200);
  String key=clean(user,100)+"|"+method+"|"+shape(shown)+"|"+status+"|"+clean(reason,80);
  if(seen.size()>MAX_KEYS)seen.entrySet().removeIf(e->now-e.getValue()[0]>WINDOW_MS);
  long[] decision={0,0}; // {log it?, repeats suppressed since the last line}
  seen.compute(key,(k,old)->{
   if(old==null){decision[0]=1;return new long[]{now,0};}
   if(now-old[0]>=WINDOW_MS){decision[0]=1;decision[1]=old[1];return new long[]{now,0};}  // window over: log again, with the number of suppressed repeats
   old[1]++;return old;                                                                  // inside the window: only count
  });
  if(decision[0]==1){
   int repeats=(int)decision[1];
   var entry=new Entry(Instant.now(),status,clean(method,10),shown,clean(user,100),tenant==null?"-":clean(tenant,40),clean(address,60),clean(reason,120),repeats);
   synchronized(recent){recent.addFirst(entry);while(recent.size()>KEEP)recent.removeLast();}
   LOG.warn("ACCESS_DENIED status={} method={} path={} user={} tenant={} address={} reason={}{}",entry.status(),entry.method(),entry.path(),entry.user(),entry.tenant(),entry.address(),entry.reason(),repeats>0?" repeats="+repeats:"");
  }
 }
 public List<Entry> recent(){synchronized(recent){return List.copyOf(recent);}}
}
