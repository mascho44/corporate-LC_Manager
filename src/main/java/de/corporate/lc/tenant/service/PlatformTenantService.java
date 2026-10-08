package de.corporate.lc.tenant.service;

import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.user.service.*;
import de.corporate.lc.audit.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Service public class PlatformTenantService {
 public record View(UUID id,String code,String name,String defaultLanguage,boolean active,boolean bankEnabled,boolean corporateEnabled,boolean archived,java.time.Instant archivedAt){}
 private final TenantRepository tenants;private final PlatformAdministrationService platform;private final TenantWorkspaceService workspaces;private final TenantAdministrationLock lock;private final AuditService audit;
 public PlatformTenantService(TenantRepository tenants,PlatformAdministrationService platform,TenantWorkspaceService workspaces,TenantAdministrationLock lock,AuditService audit){this.tenants=tenants;this.platform=platform;this.workspaces=workspaces;this.lock=lock;this.audit=audit;}
 private void require(Authentication auth){if(!platform.enabled(auth))throw new AccessDeniedException("Platform administration with two-factor authentication is required.");platform.verifyLiveAccess(auth);}
 private View view(Tenant t){return new View(t.getId(),t.getCode(),t.getName(),t.getDefaultLanguage(),t.isActive(),t.isBankEnabled(),t.isCorporateEnabled(),t.isArchived(),t.getArchivedAt());}
 @Transactional(readOnly=true) public List<View> list(Authentication auth){require(auth);return tenants.findAll().stream().sorted(Comparator.comparing(Tenant::getName)).map(this::view).toList();}
 @Transactional public TenantWorkspaceService.Workspace create(String code,String name,String language,boolean bank,boolean corporate,Authentication auth){require(auth);try(var home=TenantContext.open(Tenant.DEFAULT_ID)){return workspaces.createForPlatform(code,name,language,bank,corporate,auth);}}
 @Transactional public View update(UUID id,boolean active,boolean bank,boolean corporate,Authentication auth){
  UUID selected=TenantContext.currentId();require(auth);
  if(!bank&&!corporate)throw new IllegalArgumentException("Select at least one profile.");
  if(!active&&(Tenant.DEFAULT_ID.equals(id)||selected.equals(id)))throw new IllegalArgumentException("The default tenant and your current workspace cannot be suspended. Switch workspace first.");
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){
   lock.acquire();require(auth);var tenant=tenants.findForAdministration(id).orElseThrow(()->new NoSuchElementException("Tenant not found."));
   if(tenant.isArchived())throw new IllegalArgumentException("Restore the archived tenant before changing access or profile.");
   if(tenant.isActive()==active&&tenant.isBankEnabled()==bank&&tenant.isCorporateEnabled()==corporate)return view(tenant);
   String before=snapshot(tenant);tenant.setActive(active);tenant.updateProfile(bank,corporate);tenants.saveAndFlush(tenant);
   audit.recordChangeInTransaction(auth,"PLATFORM_TENANT_UPDATED","TENANT",id,"Tenant activation and module profile updated",before,snapshot(tenant));return view(tenant);
  }
 }
 @Transactional public View archive(UUID id,boolean archived,Authentication auth){
  require(auth);if(Tenant.DEFAULT_ID.equals(id)||TenantContext.currentId().equals(id))throw new IllegalArgumentException("Default/current tenant cannot be archived or restored from this workspace.");
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){lock.acquire();require(auth);var tenant=tenants.findForAdministration(id).orElseThrow(()->new NoSuchElementException("Tenant not found."));if(tenant.isArchived()==archived)return view(tenant);
   String before="{\"active\":"+tenant.isActive()+",\"archived\":"+tenant.isArchived()+"}";
   if(archived)tenant.archive();else tenant.restoreArchive();tenants.saveAndFlush(tenant);
   audit.recordChangeInTransaction(auth,archived?"PLATFORM_TENANT_ARCHIVED":"PLATFORM_TENANT_RESTORED","TENANT",id,"Tenant archive status changed; business data retained",before,"{\"active\":false,\"archived\":"+tenant.isArchived()+"}");return view(tenant);
  }
 }
 private String snapshot(Tenant t){return "{\"active\":"+t.isActive()+",\"bankEnabled\":"+t.isBankEnabled()+",\"corporateEnabled\":"+t.isCorporateEnabled()+"}";}
}
