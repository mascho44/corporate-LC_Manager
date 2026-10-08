package de.corporate.lc.tenant;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.tenant.service.*;
import de.corporate.lc.user.service.PlatformAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TenantInventoryServiceTest {
 final PlatformAdministrationService platform=mock(PlatformAdministrationService.class);
 final TenantRepository tenants=mock(TenantRepository.class);final TenantInventoryStore store=mock(TenantInventoryStore.class);final Authentication auth=mock(Authentication.class);
 final TenantInventoryService service=new TenantInventoryService(platform,tenants,store);
 @Test void localAdminDeniedBeforeInventory(){assertThatThrownBy(()->service.preview(UUID.randomUUID(),auth)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,tenants);}
 @Test void revokedGlobalAccessDeniedBeforeInventory(){when(platform.enabled(auth)).thenReturn(true);doThrow(new AccessDeniedException("Revoked")).when(platform).verifyLiveAccess(auth);assertThatThrownBy(()->service.preview(UUID.randomUUID(),auth)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,tenants);}
 @Test void missingTenantHasNoInventory(){when(platform.enabled(auth)).thenReturn(true);assertThatThrownBy(()->service.preview(UUID.randomUUID(),auth)).isInstanceOf(NoSuchElementException.class);verifyNoInteractions(store);}
 @Test void explicitTargetCountsWithoutChangingCallerAndNeverAuthorizesDeletion(){
  var target=new Tenant("synthetic","Synthetic","en",true,true);when(platform.enabled(auth)).thenReturn(true);when(tenants.findById(target.getId())).thenReturn(Optional.of(target));
  when(store.categories(target.getId())).thenReturn(List.of(new TenantInventoryStore.Category("lc_document",2,25L),new TenantInventoryStore.Category("training_session",1,12L),new TenantInventoryStore.Category("letter_of_credit",1,null)));when(store.jobs(target.getId())).thenReturn(new TenantInventoryStore.Jobs(1,2,3));when(store.sharedMemberships(target.getId())).thenReturn(2L);
  var selected=UUID.randomUUID();try(var scope=TenantContext.open(selected)){var result=service.preview(target.getId(),auth);assertThat(TenantContext.currentId()).isEqualTo(selected);assertThat(result.knownBinaryBytes()).isEqualTo(37);assertThat(result.sharedMemberships()).isEqualTo(2);assertThat(result.inventoryOnly()).isTrue();assertThat(result.globalAccountsExcluded()).isTrue();assertThat(result.deletionAllowed()).isFalse();assertThat(result.protectedTenant()).isFalse();assertThat(result.blockers()).contains("PENDING_JOBS","HOLDS_NOT_EVALUATED","BACKUPS_NOT_INVENTORIED");}
 }
 @Test void currentTenantIsProtectedEvenWhenEmpty(){var target=new Tenant("synthetic","Synthetic","en",true,true);when(platform.enabled(auth)).thenReturn(true);when(tenants.findById(target.getId())).thenReturn(Optional.of(target));when(store.categories(target.getId())).thenReturn(List.of());when(store.jobs(target.getId())).thenReturn(new TenantInventoryStore.Jobs(0,0,0));try(var scope=TenantContext.open(target.getId())){var result=service.preview(target.getId(),auth);assertThat(result.protectedTenant()).isTrue();assertThat(result.blockers()).contains("PROTECTED_TENANT").doesNotContain("PENDING_JOBS");assertThat(result.deletionAllowed()).isFalse();}}
}
