package de.corporate.lc.tenant.service;

import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.user.service.PlatformAdministrationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service
public class TenantInventoryService {
 public record Preview(UUID tenantId,String tenantCode,String tenantName,Instant observedAt,
  boolean inventoryOnly,boolean deletionAllowed,boolean protectedTenant,boolean globalAccountsExcluded,
  long sharedMemberships,long knownBinaryBytes,List<TenantInventoryStore.Category> categories,
  TenantInventoryStore.Jobs jobs,List<String> blockers){}
 private final PlatformAdministrationService platform;private final TenantRepository tenants;private final TenantInventoryStore store;
 public TenantInventoryService(PlatformAdministrationService platform,TenantRepository tenants,TenantInventoryStore store){this.platform=platform;this.tenants=tenants;this.store=store;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Preview preview(UUID id,Authentication auth){
  if(!platform.enabled(auth))throw new AccessDeniedException("Platform administration with two-factor authentication is required.");
  platform.verifyLiveAccess(auth);
  var tenant=tenants.findById(id).orElseThrow(()->new NoSuchElementException("Tenant not found."));
  var categories=store.categories(id);var jobs=store.jobs(id);
  var blockers=new ArrayList<>(List.of("DELETION_NOT_IMPLEMENTED","RETENTION_NOT_CONFIGURED","HOLDS_NOT_EVALUATED","BACKUPS_NOT_INVENTORIED","TEMPORARY_STORAGE_NOT_INVENTORIED","IN_FLIGHT_WORK_NOT_DRAINED"));
  boolean protectedTenant=Tenant.DEFAULT_ID.equals(id)||TenantContext.currentId().equals(id);
  if(protectedTenant)blockers.add("PROTECTED_TENANT");
  if(jobs.inboxExtraction()+jobs.outbox()+jobs.invitationMail()>0)blockers.add("PENDING_JOBS");
  return new Preview(id,tenant.getCode(),tenant.getName(),Instant.now(),true,false,protectedTenant,true,
   store.sharedMemberships(id),categories.stream().map(TenantInventoryStore.Category::binaryBytes).filter(Objects::nonNull).mapToLong(Long::longValue).sum(),categories,jobs,List.copyOf(blockers));
 }
}
