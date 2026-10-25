package de.ostms.lc.tenant.service;
import de.ostms.lc.user.domain.*;
import org.springframework.security.access.AccessDeniedException;

/** Platform administration and tenant administration belong to different people: an identity with the platform right never receives administrative rights inside a tenant. */
public final class PlatformSeparationPolicy {
 private static volatile boolean enabled=true;
 private PlatformSeparationPolicy(){}
 /** Only for the synthetic single-identity acceptance run (LC_SEPARATE_PLATFORM_ADMIN=false); production keeps the default true. */
 public static void enabled(boolean value){enabled=value;}
 /** True unless the synthetic single-identity acceptance run disabled the separation. */
 public static boolean separated(){return enabled;}
 /** Administrator base role, user management or settings management count as administrative. */
 public static boolean administrative(AppRole role){
  return role.getBaseRole()==UserRole.ADMIN||role.getPermissions().contains(UserPermission.USER_MANAGE)||role.getPermissions().contains(UserPermission.SETTINGS_MANAGE);
 }
 public static void require(AppUser user,AppRole role){
  if(enabled&&user.isPlatformAdministrator()&&administrative(role))
   throw new AccessDeniedException("Plattformadministratoren dürfen in Mandanten keine Administrator- oder Verwaltungsrechte halten. Bitte ein eigenes Mandantenkonto verwenden.");
 }
}
