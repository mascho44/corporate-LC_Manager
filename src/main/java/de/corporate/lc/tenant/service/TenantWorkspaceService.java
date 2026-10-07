package de.corporate.lc.tenant.service;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.*;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TenantWorkspaceService {
 public record Workspace(UUID id,String code,String name){}
 public record Overview(UUID selectedTenantId,List<Workspace> workspaces,boolean creationEnabled){}
 private final TenantRepository tenants;private final TenantMembershipRepository memberships;
 private final AppUserRepository users;private final AppRoleRepository roles;
 private final TenantMembershipService access;private final TenantMembershipProvisioningStore store;
 private final TenantAdministrationLock lock;private final AuditService audit;
 public TenantWorkspaceService(TenantRepository tenants,TenantMembershipRepository memberships,AppUserRepository users,AppRoleRepository roles,TenantMembershipService access,TenantMembershipProvisioningStore store,TenantAdministrationLock lock,AuditService audit){this.tenants=tenants;this.memberships=memberships;this.users=users;this.roles=roles;this.access=access;this.store=store;this.lock=lock;this.audit=audit;}
 private AppUser identity(Authentication auth){
  if(auth==null||!auth.isAuthenticated()||auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)throw new AccessDeniedException("Authentication is required.");
  var user=users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(()->new AccessDeniedException("Active identity is required."));
  if(!user.isActive())throw new AccessDeniedException("Active identity is required.");return user;
 }
 @Transactional(readOnly=true) public Overview overview(Authentication auth){
  var user=identity(auth);var current=access.requireActiveAccess(user.getId());
  var choices=memberships.findWorkspaceMemberships(user.getId()).stream().map(m->tenants.findById(m.getTenantId()).map(t->new Workspace(t.getId(),t.getCode(),t.getName())).orElseThrow()).sorted(Comparator.comparing(Workspace::name)).toList();
  return new Overview(TenantContext.currentId(),choices,Tenant.DEFAULT_ID.equals(current.tenantId())&&current.baseRole()==UserRole.ADMIN&&current.permissions().contains(UserPermission.USER_MANAGE)&&user.isTotpEnabled());
 }
 @Transactional public Workspace create(String code,String name,String language,boolean bank,boolean corporate,Authentication auth){
  if(!Tenant.DEFAULT_ID.equals(TenantContext.currentId()))throw new AccessDeniedException("Tenant creation is restricted to the identity administration tenant.");
  lock.acquire();var user=identity(auth);var current=access.requireActiveAccess(user.getId());
  if(current.baseRole()!=UserRole.ADMIN||!current.permissions().contains(UserPermission.USER_MANAGE)||!user.isTotpEnabled())throw new AccessDeniedException("An administrator with two-factor authentication is required.");
  code=code==null?"":code.trim().toLowerCase(Locale.ROOT);name=name==null?"":name.trim();
  if(!code.matches("[a-z][a-z0-9-]{1,49}")||name.isEmpty()||name.length()>255||name.chars().anyMatch(Character::isISOControl)||language==null||!List.of("en","de").contains(language))throw new IllegalArgumentException("Provide a tenant code, name and supported language.");
  if(tenants.existsByCodeIgnoreCase(code))throw new IllegalArgumentException("Tenant code already exists.");
  var tenant=tenants.saveAndFlush(new Tenant(code,name,language,bank,corporate));
  try(var scope=TenantContext.open(tenant.getId())){
   var role=new AppRole();role.setName("Administrator");role.setBaseRole(UserRole.ADMIN);role.setSystemRole(true);role.setPermissions(EnumSet.allOf(UserPermission.class));role=roles.saveAndFlush(role);
   store.create(tenant.getId(),user.getId(),role.getId());
   audit.recordInTransaction(auth,"TENANT_CREATED","TENANT",tenant.getId(),"Tenant created with initial administrator membership");
  }
  return new Workspace(tenant.getId(),tenant.getCode(),tenant.getName());
 }
 @Transactional public TenantMembershipService.Access select(UUID tenantId,Authentication auth){
  var user=identity(auth);
  try(var scope=TenantContext.open(tenantId)){
   var selected=access.requireActiveAccess(user.getId());
   if(selected.baseRole()==UserRole.ADMIN&&!user.isTotpEnabled())throw new AccessDeniedException("Two-factor authentication is required for administrator access.");
   audit.recordInTransaction(auth,"TENANT_SELECTED","TENANT",tenantId,"Workspace selected by existing membership");return selected;
  }
 }
}
