package de.ostms.lc.tenant.service;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.TenantAdministrationLock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TenantWorkspaceService {
 /** home marks the tenant that only holds the accounts; the sign-in form leaves it out once real workspaces exist. */
 public record Workspace(UUID id,String code,String name,boolean home){
  public Workspace(UUID id,String code,String name){this(id,code,name,false);}
 }
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
  var user=identity(auth);
  try{access.requireActiveAccess(user.getId());}
  catch(AccessDeniedException denied){
   // A platform administrator may belong to no tenant: nothing to choose, not an error.
   if(user.isPlatformAdministrator()&&user.isTotpEnabled()&&!user.isInvitationPending())return new Overview(TenantContext.currentId(),List.of(),false);
   throw denied;
  }
  var choices=memberships.findWorkspaceMemberships(user.getId()).stream().map(m->tenants.findById(m.getTenantId()).orElseThrow()).filter(Tenant::isActive).map(t->new Workspace(t.getId(),t.getCode(),t.getName(),t.getId().equals(Tenant.homeId()))).sorted(Comparator.comparing(Workspace::name)).toList();
  return new Overview(TenantContext.currentId(),withoutPlatformHome(choices,TenantContext.currentId()),false);
 }
 /** The bootstrap tenant is the identity home of all accounts, not a workspace to work in: hide it once real workspaces exist (unless it is the selected one). */
 static List<Workspace> withoutPlatformHome(List<Workspace> choices,UUID selected){
  if(Tenant.homeId().equals(selected)||choices.size()<2)return choices;
  return choices.stream().filter(w->!Tenant.homeId().equals(w.id())).toList();
 }
 @Transactional public Workspace create(String code,String name,String language,boolean bank,boolean corporate,Authentication auth){
  throw new AccessDeniedException("Tenant creation is available only in platform administration.");
 }
 /** The person who administers a new tenant; invited by email, never the platform administrator who creates the tenant. */
 public record AdminInvite(String username,String displayName,String email){}
 @org.springframework.beans.factory.annotation.Autowired(required=false) @org.springframework.context.annotation.Lazy private de.ostms.lc.user.service.PlatformInvitationService invitations;
 @Transactional public Workspace createForPlatform(String code,String name,String language,boolean bank,boolean corporate,Authentication auth){return createForPlatform(code,name,language,bank,corporate,null,auth);}
 @Transactional public Workspace createForPlatform(String code,String name,String language,boolean bank,boolean corporate,AdminInvite admin,Authentication auth){
  if(!Tenant.homeId().equals(TenantContext.currentId()))throw new AccessDeniedException("Tenant creation is restricted to the identity administration tenant.");
  var checked=identity(auth);if(!checked.isPlatformAdministrator()||!checked.isTotpEnabled()||checked.isInvitationPending())throw new AccessDeniedException("Platform access required.");
  lock.acquire();var user=identity(auth);
  if(!users.hasLivePlatformAccess(user.getId()))throw new AccessDeniedException("Platform access unavailable.");
  if(!user.isPlatformAdministrator()||!user.isTotpEnabled()||user.isInvitationPending())throw new AccessDeniedException("A platform administrator with two-factor authentication is required.");
  boolean separated=PlatformSeparationPolicy.separated();
  boolean adminGiven=admin!=null&&admin.username()!=null&&!admin.username().isBlank()&&admin.displayName()!=null&&!admin.displayName().isBlank()&&admin.email()!=null&&!admin.email().isBlank();
  var existingAdmin=admin!=null&&admin.username()!=null&&!admin.username().isBlank()?users.findByUsernameIgnoreCase(admin.username().trim()).orElse(null):null;
  if(existingAdmin!=null&&(!existingAdmin.isActive()||existingAdmin.isInvitationPending()))throw new IllegalArgumentException("Das bestehende Konto ist nicht aktiv oder die Einladung ist noch offen.");
  if(separated&&!adminGiven&&existingAdmin==null)throw new IllegalArgumentException("Bitte den ersten Mandantenadmin angeben: den Benutzernamen eines bestehenden Kontos, oder Benutzername, Name und E-Mail für eine Einladung. Der Plattformadministrator verwaltet keinen Mandanten selbst.");
  if(adminGiven&&existingAdmin==null&&invitations==null)throw new IllegalStateException("Einladungen sind nicht verfügbar.");
  if(!bank&&!corporate)throw new IllegalArgumentException("Select at least one profile.");
  code=code==null?"":code.trim().toLowerCase(Locale.ROOT);name=name==null?"":name.trim();
  if(!code.matches("[a-z][a-z0-9-]{1,49}")||name.isEmpty()||name.length()>255||name.chars().anyMatch(Character::isISOControl)||language==null||!List.of("en","de").contains(language))throw new IllegalArgumentException("Provide a tenant code, name and supported language.");
  if(tenants.existsByCodeIgnoreCase(code))throw new IllegalArgumentException("Tenant code already exists.");
  var tenant=tenants.saveAndFlush(new Tenant(code,name,language,bank,corporate));
  java.util.UUID adminRoleId=null;
  try(var scope=TenantContext.open(tenant.getId())){
   var role=createStandardRole("Administrator",UserRole.ADMIN);
   createStandardRole("Editor",UserRole.EDITOR);
   createStandardRole("Viewer",UserRole.VIEWER);
   adminRoleId=role.getId();
   if(!separated)store.create(tenant.getId(),user.getId(),role.getId()); // legacy single-identity mode (synthetic acceptance run only)
   if(existingAdmin!=null&&!(existingAdmin.getId().equals(user.getId())&&!separated))store.create(tenant.getId(),existingAdmin.getId(),role.getId());
   audit.recordInTransaction(auth,"TENANT_CREATED","TENANT",tenant.getId(),existingAdmin!=null?"Tenant created; existing account "+existingAdmin.getUsername()+" is the first tenant administrator":separated?"Tenant created; first tenant administrator invited, no platform membership":"Tenant created with initial administrator membership");
  }
  if(adminGiven&&existingAdmin==null)invitations.invite(admin.username(),admin.displayName(),admin.email(),tenant.getId(),adminRoleId,auth);
  return new Workspace(tenant.getId(),tenant.getCode(),tenant.getName());
 }
 private AppRole createStandardRole(String name,UserRole base){
  var role=new AppRole();role.setName(name);role.setBaseRole(base);role.setSystemRole(true);role.setPermissions(UserPermission.defaults(base));return roles.saveAndFlush(role);
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
