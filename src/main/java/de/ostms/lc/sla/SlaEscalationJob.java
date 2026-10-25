package de.ostms.lc.sla;

import de.ostms.lc.tenant.service.TenantWorkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/** Hourly: breached SLA clocks become group inbox tasks for the escalation team of their rule (tenants without SLA rules cost one empty query). */
@Component
public class SlaEscalationJob {
 private static final Logger log=LoggerFactory.getLogger(SlaEscalationJob.class);
 private final TenantWorkers tenants;private final SlaEscalationService escalation;
 public SlaEscalationJob(TenantWorkers tenants,SlaEscalationService escalation){this.tenants=tenants;this.escalation=escalation;}
 @Scheduled(fixedDelayString="${app.sla.escalation-ms:3600000}",initialDelayString="${app.sla.escalation-initial-ms:240000}")
 public void run(){
  try{tenants.forEach(()->{int created=escalation.escalate(LocalDate.now());if(created>0)log.info("SLA escalation: {} tasks created",created);});}
  catch(RuntimeException failure){log.warn("SLA escalation failed",failure);}
 }
}
