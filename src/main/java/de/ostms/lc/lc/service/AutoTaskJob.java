package de.ostms.lc.lc.service;
import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hourly scan for approaching deadlines in every tenant. */
@Component
public class AutoTaskJob {
 private static final Logger log=LoggerFactory.getLogger(AutoTaskJob.class);
 private final TenantWorkers tenants;private final AutoTaskService service;
 private final AtomicBoolean busy=new AtomicBoolean(false);
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"auto-tasks");t.setDaemon(true);return t;});
 public AutoTaskJob(TenantWorkers t,AutoTaskService s){tenants=t;service=s;}

 @Scheduled(fixedDelayString="${app.automation.deadline-poll-ms:3600000}",initialDelayString="${app.automation.deadline-initial-ms:120000}")
 public void dispatch(){
  if(!busy.compareAndSet(false,true))return;
  try{executor.execute(()->{try{tenants.forEach(()->service.scanDeadlines(LocalDate.now()));}catch(RuntimeException failure){log.warn("Automatic task scan failed: {}",failure.getClass().getSimpleName());}finally{busy.set(false);}});}
  catch(RejectedExecutionException stopped){busy.set(false);}
 }
}
