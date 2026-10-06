package de.corporate.lc.tenant.service;

import de.corporate.lc.tenant.domain.Tenant;
import de.corporate.lc.tenant.domain.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/** Explicit worker boundary. Provisioning/enumeration of additional tenants is not enabled. */
public final class TenantJobRunner {
 private TenantJobRunner(){}
 public static void run(UUID tenantId,Runnable job){
  if(!Tenant.DEFAULT_ID.equals(tenantId))throw new AccessDeniedException("Tenant jobs are not enabled for this tenant.");
  Objects.requireNonNull(job,"Job is missing.");
  try(var scope=TenantContext.open(tenantId)){job.run();}
 }
}
