package de.corporate.lc.tenant.service;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.AppUserRepository;
import de.corporate.lc.user.service.TenantAdministrationLock;
import de.corporate.lc.audit.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Service public class TenantSettingsService {
 public record Settings(UUID tenantId,String code,String name,String defaultLanguage,boolean editingEnabled){}
 private final TenantRepository tenants;private final AppUserRepository users;private final TenantMembershipService access;private final TenantAdministrationLock lock;private final AuditService audit;
 public TenantSettingsService(TenantRepository tenants,AppUserRepository users,TenantMembershipService access,TenantAdministrationLock lock,AuditService audit){this.tenants=tenants;this.users=users;this.access=access;this.lock=lock;this.audit=audit;}
 private boolean canEdit(Authentication auth){
  if(auth==null||!auth.isAuthenticated()||auth instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Authentication is required.");
  var user=users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(()->new AccessDeniedException("Active identity is required."));
  if(!user.isActive())throw new AccessDeniedException("Active identity is required.");
  var membership=access.requireActiveAccess(user.getId());
  return membership.baseRole()==UserRole.ADMIN&&membership.permissions().contains(UserPermission.USER_MANAGE)&&user.isTotpEnabled();
 }
 private Tenant tenant(){return tenants.findById(TenantContext.currentId()).orElseThrow(()->new AccessDeniedException("Tenant not available."));}
 private Settings view(Tenant t,boolean editable){return new Settings(t.getId(),t.getCode(),t.getName(),t.getDefaultLanguage(),editable);}
 @Transactional(readOnly=true) public Settings get(Authentication auth){boolean editable=canEdit(auth);return view(tenant(),editable);}
 @Transactional public Settings update(String name,String language,Authentication auth){
  lock.acquire();if(!canEdit(auth))throw new AccessDeniedException("An administrator with user-management permission and two-factor authentication is required.");
  name=name==null?"":name.trim();
  if(name.isEmpty()||name.length()>255||name.chars().anyMatch(Character::isISOControl)||!List.of("en","de").contains(language==null?"":language))throw new IllegalArgumentException("Provide a tenant name and supported language.");
  var t=tenant();String before=snapshot(t);t.updatePresentation(name,language);tenants.saveAndFlush(t);
  audit.recordChangeInTransaction(auth,"TENANT_SETTINGS_UPDATED","TENANT",t.getId(),"Tenant name and default language updated",before,snapshot(t));return view(t,true);
 }
 private String snapshot(Tenant t){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("name",t.getName(),"defaultLanguage",t.getDefaultLanguage()));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Tenant audit snapshot failed",e);}}
}
