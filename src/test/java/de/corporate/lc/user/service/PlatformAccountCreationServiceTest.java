package de.corporate.lc.user.service;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantMembershipSuspensionRepository;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAccountCreationServiceTest {
 final PlatformAdministrationService platform=mock(PlatformAdministrationService.class);
 final AppUserRepository users=mock(AppUserRepository.class);final AppRoleRepository roles=mock(AppRoleRepository.class);
 final TenantMembershipSuspensionRepository suspensions=mock(TenantMembershipSuspensionRepository.class);
 final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);final PasswordEncoder encoder=mock(PasswordEncoder.class);final AuditService audit=mock(AuditService.class);
 final PlatformAccountCreationService service=new PlatformAccountCreationService(platform,users,roles,suspensions,lock,encoder,audit);
 final org.springframework.security.core.Authentication auth=UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of());
 void allowed(){when(platform.enabled(auth)).thenReturn(true);var role=new AppRole();role.setBaseRole(UserRole.VIEWER);role.setSystemRole(true);when(roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER)).thenReturn(Optional.of(role));when(encoder.encode(any())).thenReturn("synthetic-hash");when(users.saveAndFlush(any())).thenAnswer(call->{AppUser user=call.getArgument(0);ReflectionTestUtils.setField(user,"id",UUID.randomUUID());return user;});}
 PlatformAdministrationService.Account create(){return service.create(" Synthetic.New "," Synthetic New ","new@example.invalid","Synthetic123!",auth);}
 @Test void deniedCallerCannotLockOrEncode(){assertThatThrownBy(this::create).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(lock,users,roles,encoder,suspensions,audit);}
 @Test void rechecksGrantAfterLock(){when(platform.enabled(auth)).thenReturn(true,false);assertThatThrownBy(this::create).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(lock).acquire();verifyNoInteractions(users,roles,encoder,suspensions,audit);}
 @Test void createsNormalizedIdentityWithSuspendedHomeAndNoGlobalRights(){allowed();UUID workspace=UUID.randomUUID();try(var scope=TenantContext.open(workspace)){var result=create();assertThat(result.username()).isEqualTo("synthetic.new");assertThat(result.platformAdministrator()).isFalse();assertThat(result.totpEnabled()).isFalse();assertThat(result.active()).isTrue();assertThat(TenantContext.currentId()).isEqualTo(workspace);}
  var user=org.mockito.ArgumentCaptor.forClass(AppUser.class);verify(users).saveAndFlush(user.capture());assertThat(user.getValue().getTenantId()).isEqualTo(Tenant.DEFAULT_ID);assertThat(user.getValue().effectivePermissions()).isEmpty();assertThat(user.getValue().getPasswordHash()).isEqualTo("synthetic-hash");
  var suspension=org.mockito.ArgumentCaptor.forClass(TenantMembershipSuspension.class);verify(suspensions).saveAndFlush(suspension.capture());assertThat(suspension.getValue().isSuspended()).isTrue();assertThat(suspension.getValue().getUserId()).isEqualTo(user.getValue().getId());verify(audit).recordChangeInTransaction(eq(auth),eq("PLATFORM_ACCOUNT_CREATED"),eq("USER"),eq(user.getValue().getId()),anyString(),isNull(),eq("{\"active\":true}"));
 }
 @Test void duplicateIdentityIsRejectedBeforePasswordEncoding(){allowed();when(users.existsByUsernameIgnoreCase("synthetic.new")).thenReturn(true);assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);verify(encoder,never()).encode(any());verify(users,never()).saveAndFlush(any());}
 @Test void invalidInputIsRejected(){allowed();for(String email:List.of("","not-an-email","a\nb@example.invalid"))assertThatThrownBy(()->service.create("new","New",email,"Synthetic123!",auth)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->service.create("new","New","a@example.invalid","weak",auth)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->service.create("bad name","New","a@example.invalid","Synthetic123!",auth)).isInstanceOf(IllegalArgumentException.class);verify(users,never()).saveAndFlush(any());}
 @Test void customizedViewerRoleFailsClosed(){allowed();var role=roles.findByBaseRoleAndSystemRoleTrue(UserRole.VIEWER).orElseThrow();role.setPermissions(Set.of(UserPermission.USER_MANAGE));assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class);verify(users,never()).saveAndFlush(any());}
}
