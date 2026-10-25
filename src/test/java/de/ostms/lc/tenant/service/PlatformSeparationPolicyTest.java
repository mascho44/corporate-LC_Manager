package de.ostms.lc.tenant.service;
import de.ostms.lc.user.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class PlatformSeparationPolicyTest {
 private static AppRole role(UserRole base,UserPermission... permissions){var r=new AppRole();r.setBaseRole(base);r.setPermissions(Set.of(permissions));return r;}
 private static AppUser user(boolean platform){var u=new AppUser();u.setPlatformAdministrator(platform);return u;}

 @Test void aPlatformAdministratorCannotBecomeATenantAdministrator(){
  assertThatThrownBy(()->PlatformSeparationPolicy.require(user(true),role(UserRole.ADMIN))).isInstanceOf(AccessDeniedException.class).hasMessageContaining("Plattformadministratoren");
  assertThatThrownBy(()->PlatformSeparationPolicy.require(user(true),role(UserRole.EDITOR,UserPermission.USER_MANAGE))).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->PlatformSeparationPolicy.require(user(true),role(UserRole.VIEWER,UserPermission.SETTINGS_MANAGE))).isInstanceOf(AccessDeniedException.class);
 }
 @Test void ordinaryRolesAndOrdinaryUsersAreNotAffected(){
  assertThatCode(()->PlatformSeparationPolicy.require(user(true),role(UserRole.VIEWER))).doesNotThrowAnyException();
  assertThatCode(()->PlatformSeparationPolicy.require(user(false),role(UserRole.ADMIN,UserPermission.USER_MANAGE,UserPermission.SETTINGS_MANAGE))).doesNotThrowAnyException();
 }
 @Test void theSwitchOnlyExistsForTheSyntheticAcceptanceRun(){
  try{PlatformSeparationPolicy.enabled(false);assertThatCode(()->PlatformSeparationPolicy.require(user(true),role(UserRole.ADMIN))).doesNotThrowAnyException();}
  finally{PlatformSeparationPolicy.enabled(true);}
  assertThatThrownBy(()->PlatformSeparationPolicy.require(user(true),role(UserRole.ADMIN))).isInstanceOf(AccessDeniedException.class);
 }
}
