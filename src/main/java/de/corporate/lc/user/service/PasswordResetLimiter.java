package de.corporate.lc.user.service;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Bounded single-instance abuse protection. Store only hashes of client identifiers. */
@Component
public class PasswordResetLimiter {
    private record Window(Instant start,int count) { }
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;
    public PasswordResetLimiter(){this(Clock.systemUTC());}
    PasswordResetLimiter(Clock clock){this.clock=clock;}
    public synchronized boolean allow(String action,String ip,String username){
        Instant now=clock.instant();
        windows.entrySet().removeIf(e->!e.getValue().start().plusSeconds(900).isAfter(now));
        String client=CredentialStamp.of(action+":ip:"+ip), account=CredentialStamp.of(action+":user:"+username.toLowerCase(Locale.ROOT));
        if(windows.size()>10000||count(client)>=10||(!username.isEmpty()&&count(account)>=3))return false;
        increment(client,now);if(!username.isEmpty())increment(account,now);return true;
    }
    private int count(String key){return windows.containsKey(key)?windows.get(key).count():0;}
    private void increment(String key,Instant now){Window old=windows.get(key);windows.put(key,new Window(old==null?now:old.start(),old==null?1:old.count()+1));}
}
