package de.ostms.lc.intake;

import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Looks every half minute for intake channels that are on and due, tenant by tenant; runs never overlap. */
@Component
public class IntakePollingJob {
 private static final Logger log=LoggerFactory.getLogger(IntakePollingJob.class);
 private final TenantWorkers tenants;private final IntakeChannelService service;
 private final AtomicBoolean busy=new AtomicBoolean(false);
 private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"intake-polling");t.setDaemon(true);return t;});
 public IntakePollingJob(TenantWorkers tenants,IntakeChannelService service){this.tenants=tenants;this.service=service;}

 @Scheduled(fixedDelayString="${app.intake.poll-ms:30000}",initialDelayString="${app.intake.poll-ms:30000}")
 public void dispatch(){
  if(!busy.compareAndSet(false,true))return;
  try{executor.execute(()->{try{tenants.forEach(service::pollDue);}catch(RuntimeException failure){log.warn("Intake polling failed: {}",failure.getClass().getSimpleName());}finally{busy.set(false);}});}
  catch(RejectedExecutionException stopped){busy.set(false);}
 }
}
