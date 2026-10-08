package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class LanguagePreferencesService {
 public static final List<String> SUPPORTED=List.of("en","de");
 public record Preferences(String language,String preferredLanguage,String tenantDefaultLanguage,UUID tenantId,String tenantName,List<String> availableLanguages,boolean multiTenantEnabled) {}
 private final AppUserRepository users;private final TenantRepository tenants;
 public LanguagePreferencesService(AppUserRepository users,TenantRepository tenants){this.users=users;this.tenants=tenants;}
 @Transactional(readOnly=true) public Preferences get(String username){
  var user=users.findByUsernameIgnoreCase(username).orElseThrow(()->new NoSuchElementException("User not found."));
  if(!Tenant.DEFAULT_ID.equals(user.getTenantId()))throw new org.springframework.security.access.AccessDeniedException("Global identity administration remains in the default tenant.");
  var tenant=tenants.findById(de.ostms.lc.tenant.domain.TenantContext.currentId()).orElseThrow(()->new IllegalStateException("Tenant configuration is missing."));
  String fallback=SUPPORTED.contains(tenant.getDefaultLanguage())?tenant.getDefaultLanguage():"en";
  return new Preferences(user.getPreferredLanguage()!=null&&SUPPORTED.contains(user.getPreferredLanguage())?user.getPreferredLanguage():fallback,user.getPreferredLanguage(),fallback,tenant.getId(),tenant.getName(),SUPPORTED,true);
 }
 @Transactional public Preferences update(String username,String language){
  get(username);
  if(language!=null&&!SUPPORTED.contains(language))throw new IllegalArgumentException("Unsupported language pack.");
  var user=users.findByUsernameIgnoreCase(username).orElseThrow();
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(user.getTenantId())){user.setPreferredLanguage(language);users.saveAndFlush(user);}
  return get(username);
 }
}
