package de.corporate.lc.tenant;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.tenant.service.*;
import de.corporate.lc.user.service.*;
import de.corporate.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PlatformTenantServiceTest {
 @Test void archiveRequiresSuspensionAndRestorationDoesNotActivate(){when(platform.enabled(auth)).thenReturn(true);var tenant=new Tenant("archive","Archive","en",true,true);when(tenants.findForAdministration(tenant.getId())).thenReturn(Optional.of(tenant));assertThatThrownBy(()->service.archive(tenant.getId(),true,auth)).isInstanceOf(IllegalArgumentException.class);tenant.setActive(false);assertThat(service.archive(tenant.getId(),true,auth).archived()).isTrue();assertThatThrownBy(()->service.update(tenant.getId(),true,true,true,auth)).isInstanceOf(IllegalArgumentException.class);var restored=service.archive(tenant.getId(),false,auth);assertThat(restored.archived()).isFalse();assertThat(restored.active()).isFalse();verify(audit).recordChangeInTransaction(eq(auth),eq("PLATFORM_TENANT_ARCHIVED"),eq("TENANT"),eq(tenant.getId()),anyString(),anyString(),anyString());}
 @Test void archiveProtectsDefaultCurrentAndRequiresGlobalAccess(){assertThatThrownBy(()->service.archive(UUID.randomUUID(),true,auth)).isInstanceOf(AccessDeniedException.class);when(platform.enabled(auth)).thenReturn(true);assertThatThrownBy(()->service.archive(Tenant.DEFAULT_ID,true,auth)).isInstanceOf(IllegalArgumentException.class);var current=UUID.randomUUID();try(var scope=TenantContext.open(current)){assertThatThrownBy(()->service.archive(current,true,auth)).isInstanceOf(IllegalArgumentException.class);}}
 final TenantRepository tenants=mock(TenantRepository.class);final PlatformAdministrationService platform=mock(PlatformAdministrationService.class);final TenantWorkspaceService workspaces=mock(TenantWorkspaceService.class);final TenantAdministrationLock lock=mock(TenantAdministrationLock.class);final AuditService audit=mock(AuditService.class);final Authentication auth=mock(Authentication.class);
 final PlatformTenantService service=new PlatformTenantService(tenants,platform,workspaces,lock,audit);
 @Test void localAdminDeniedBeforeMutation(){assertThatThrownBy(()->service.create("code","Name","en",true,false,auth)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(workspaces,lock);assertThatThrownBy(()->service.list(auth)).isInstanceOf(AccessDeniedException.class);}
 @Test void creationUsesHomeScopeAndRestoresCaller(){when(platform.enabled(auth)).thenReturn(true);var selected=UUID.randomUUID();try(var scope=TenantContext.open(selected)){service.create("code","Name","en",true,false,auth);assertThat(TenantContext.currentId()).isEqualTo(selected);}verify(workspaces).createForPlatform("code","Name","en",true,false,auth);}
 @Test void protectsCurrentAndDefaultTenant(){when(platform.enabled(auth)).thenReturn(true);assertThatThrownBy(()->service.update(Tenant.DEFAULT_ID,false,true,false,auth)).isInstanceOf(IllegalArgumentException.class);var selected=UUID.randomUUID();try(var scope=TenantContext.open(selected)){assertThatThrownBy(()->service.update(selected,false,true,false,auth)).isInstanceOf(IllegalArgumentException.class);}verifyNoInteractions(lock);}
 @Test void profileAndActivationAuditedWithScopeRestored(){when(platform.enabled(auth)).thenReturn(true);var tenant=new Tenant("target","Target","en",true,false);when(tenants.findForAdministration(tenant.getId())).thenReturn(Optional.of(tenant));var selected=UUID.randomUUID();try(var scope=TenantContext.open(selected)){var result=service.update(tenant.getId(),false,false,true,auth);assertThat(result.active()).isFalse();assertThat(result.corporateEnabled()).isTrue();assertThat(TenantContext.currentId()).isEqualTo(selected);}verify(audit).recordChangeInTransaction(eq(auth),eq("PLATFORM_TENANT_UPDATED"),eq("TENANT"),eq(tenant.getId()),anyString(),contains("\"active\":true"),contains("\"active\":false"));}
}
