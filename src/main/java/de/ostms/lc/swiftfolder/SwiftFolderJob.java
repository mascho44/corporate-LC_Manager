package de.ostms.lc.swiftfolder;

import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

/** Looks every half minute into the SWIFT inbox folder of each bank tenant (when the folder inbox is switched on); runs never overlap. */
@Component
public class SwiftFolderJob {
 private static final Logger log=LoggerFactory.getLogger(SwiftFolderJob.class);
 private final TenantWorkers tenants;private final SwiftFolderInbox inbox;private final AtomicBoolean busy=new AtomicBoolean(false);
 public SwiftFolderJob(TenantWorkers tenants,SwiftFolderInbox inbox){this.tenants=tenants;this.inbox=inbox;}
 @Scheduled(fixedDelayString="${app.swift-folder.poll-ms:30000}",initialDelayString="${app.swift-folder.poll-ms:30000}")
 public void dispatch(){
  if(!inbox.enabled()||!busy.compareAndSet(false,true))return;
  try{tenants.forEach(inbox::pollCurrentTenant);}catch(RuntimeException failure){log.warn("SWIFT folder inbox failed: {}",failure.getClass().getSimpleName());}finally{busy.set(false);}
 }
}
