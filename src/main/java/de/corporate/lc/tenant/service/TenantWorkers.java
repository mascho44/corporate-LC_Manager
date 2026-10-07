package de.corporate.lc.tenant.service;
import de.corporate.lc.tenant.domain.TenantContext;
import de.corporate.lc.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
/** Enumerate only persisted tenants; never inherit a request's workspace in a worker. */
@Service public class TenantWorkers {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(TenantWorkers.class);
 private final TenantRepository tenants;
 public TenantWorkers(TenantRepository tenants){this.tenants=tenants;}
 public void forEach(Runnable job){
  for(var tenant:tenants.findAll())if(tenant.isActive())try(var scope=TenantContext.open(tenant.getId())){job.run();}
  catch(RuntimeException failure){log.warn("Tenant worker failed: tenant={} type={}",tenant.getId(),failure.getClass().getSimpleName());}
 }
}
