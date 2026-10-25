package de.ostms.lc.ebics;
import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches trade-finance messages for every tenant whose connection is active, switched on and due. Fetching only stores messages; importing stays a user decision. */
@Component
public class EbicsAutoFetchJob {
 private static final Logger log=LoggerFactory.getLogger(EbicsAutoFetchJob.class);
 static final String ACTOR="system:ebics-auto-fetch";
 private final TenantWorkers tenants;private final EbicsConnectionRepository connections;private final EbicsMessageService messages;
 private final AtomicBoolean busy=new AtomicBoolean(false);
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"ebics-auto-fetch");t.setDaemon(true);return t;});
 public EbicsAutoFetchJob(TenantWorkers tenants,EbicsConnectionRepository connections,EbicsMessageService messages){this.tenants=tenants;this.connections=connections;this.messages=messages;}

 @Scheduled(fixedDelayString="${app.ebics.auto-fetch-poll-ms:60000}",initialDelayString="${app.ebics.auto-fetch-poll-ms:60000}")
 public void dispatch(){
  if(!busy.compareAndSet(false,true))return;
  try{executor.execute(()->{try{tenants.forEach(this::runForCurrentTenant);}catch(RuntimeException failure){log.warn("EBICS auto fetch failed: {}",failure.getClass().getSimpleName());}finally{busy.set(false);}});}
  catch(RejectedExecutionException stopped){busy.set(false);}
 }

 /** Runs inside the tenant scope opened by {@link TenantWorkers}. */
 void runForCurrentTenant(){
  var connection=connections.findCurrent().orElse(null);
  if(connection==null||connection.getStatus()!=EbicsStatus.ACTIVE||!connection.fetchDue(LocalDateTime.now()))return;
  try{messages.fetchAs(ACTOR);}
  catch(RuntimeException failure){
   // fetch() records its own outcome; a failure before that (keys unreadable) still has to move the due time on.
   connection=connections.findCurrent().orElse(null);
   if(connection!=null){connection.recordFetch("Fehler: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage()));connections.save(connection);}
  }
 }
}
