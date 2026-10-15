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
 @org.springframework.beans.factory.annotation.Autowired(required=false) private de.ostms.lc.monitoring.service.OperationalAlertService alerts;
 private final TenantWorkers tenants;private final AutoTaskService service;private final ProcurementReminderService procurement;private final ProcurementDigestService digest;
 private final AtomicBoolean busy=new AtomicBoolean(false);
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"auto-tasks");t.setDaemon(true);return t;});
 public AutoTaskJob(TenantWorkers t,AutoTaskService s,ProcurementReminderService p,ProcurementDigestService d){tenants=t;service=s;procurement=p;digest=d;}

 @Scheduled(fixedDelayString="${app.automation.deadline-poll-ms:3600000}",initialDelayString="${app.automation.deadline-initial-ms:120000}")
 public void dispatch(){
  if(!busy.compareAndSet(false,true))return;
  try{executor.execute(()->{try{tenants.forEach(()->{service.scanDeadlines(LocalDate.now());procurement.scan(LocalDate.now());if(java.time.LocalTime.now().getHour()>=7){digest.sendDaily(LocalDate.now());if(alerts!=null)alerts.silentStations(LocalDate.now());}});if(alerts!=null&&java.time.LocalTime.now().getHour()>=7)alerts.sanctionsLists(LocalDate.now());}catch(RuntimeException failure){log.warn("Automatic task scan failed: {}",failure.getClass().getSimpleName());}finally{busy.set(false);}});}
  catch(RejectedExecutionException stopped){busy.set(false);}
 }
}
