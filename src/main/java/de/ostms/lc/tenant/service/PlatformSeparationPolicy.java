package de.ostms.lc.tenant.service;
import de.ostms.lc.user.domain.*;
import org.springframework.security.access.AccessDeniedException;

/** Platform administration and tenant administration belong to different people: an identity with the platform right never receives administrative rights inside a tenant. */
public final class PlatformSeparationPolicy {
 private PlatformSeparationPolicy(){}
 /** Administrator base role, user management or settings management count as administrative. */
 public static boolean administrative(AppRole role){
  return role.getBaseRole()==UserRole.ADMIN||role.getPermissions().contains(UserPermission.USER_MANAGE)||role.getPermissions().contains(UserPermission.SETTINGS_MANAGE);
 }
 public static void require(AppUser user,AppRole role){
  if(user.isPlatformAdministrator()&&administrative(role))
   throw new AccessDeniedException("Plattformadministratoren dürfen in Mandanten keine Administrator- oder Verwaltungsrechte halten. Bitte ein eigenes Mandantenkonto verwenden.");
 }
}
