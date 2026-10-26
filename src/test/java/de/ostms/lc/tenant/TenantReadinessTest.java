package de.ostms.lc.tenant;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.company.repository.CompanyProfileRepository;
import de.ostms.lc.document.repository.DocumentTemplateRepository;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class TenantReadinessTest {
 final CompanyProfileRepository companies=mock(CompanyProfileRepository.class);final DocumentTemplateRepository templates=mock(DocumentTemplateRepository.class);final TenantMembershipRepository members=mock(TenantMembershipRepository.class);final AppUserRepository users=mock(AppUserRepository.class);final TenantMembershipService access=mock(TenantMembershipService.class);final de.ostms.lc.sla.SlaRuleRepository slaRules=mock(de.ostms.lc.sla.SlaRuleRepository.class);final de.ostms.lc.correspondent.CorrespondentBankRepository correspondents=mock(de.ostms.lc.correspondent.CorrespondentBankRepository.class);
 final TenantReadinessService service=new TenantReadinessService(companies,templates,members,users,access,slaRules,correspondents);final AppUser user=new AppUser();final UUID target=UUID.randomUUID(),id=UUID.randomUUID();final UsernamePasswordAuthenticationToken auth=UsernamePasswordAuthenticationToken.authenticated("synthetic-admin",null,List.of());
 @BeforeEach void setup(){user.setUsername(auth.getName());user.setTotpEnabled(true);ReflectionTestUtils.setField(user,"id",id);when(users.findByUsernameIgnoreCase(auth.getName())).thenReturn(Optional.of(user));grant(UserRole.ADMIN,Set.of(UserPermission.USER_MANAGE,UserPermission.SETTINGS_MANAGE));}
 void grant(UserRole role,Set<UserPermission> permissions){when(access.requireActiveAccess(id)).thenReturn(new TenantMembershipService.Access(target,id,UUID.randomUUID(),UUID.randomUUID(),role,permissions));}
 @Test void readsCountsOnlyWithinSelectedContext(){when(companies.count()).thenAnswer(i->{assertThat(TenantContext.currentId()).isEqualTo(target);return 2L;});when(templates.count()).thenReturn(3L);when(members.countAccessibleMembers()).thenReturn(1L);when(slaRules.count()).thenReturn(4L);when(correspondents.count()).thenReturn(5L);try(var scope=TenantContext.open(target)){assertThat(service.get(auth)).isEqualTo(new TenantReadinessService.Readiness(target,2,3,1,4,5));}assertThat(TenantContext.currentId()).isEqualTo(Tenant.DEFAULT_ID);}
 @Test void emptyTenantReportsZeroWithoutClaimingBusinessReadiness(){try(var scope=TenantContext.open(target)){var result=service.get(auth);assertThat(result.companies()).isZero();assertThat(result.templates()).isZero();assertThat(result.activeMembers()).isZero();assertThat(result.slaRules()).isZero();assertThat(result.correspondentBanks()).isZero();}}
 @Test void missingPermissionOrNonAdministratorCannotReadCounts(){for(var role:List.of(UserRole.ADMIN,UserRole.VIEWER)){grant(role,Set.of(UserPermission.USER_MANAGE));assertThatThrownBy(()->service.get(auth)).isInstanceOf(AccessDeniedException.class);}verifyNoInteractions(companies,templates,members,slaRules,correspondents);}
 @Test void inactiveIdentityAndMissingTotpCannotReadCounts(){user.setActive(false);assertThatThrownBy(()->service.get(auth)).isInstanceOf(AccessDeniedException.class);user.setActive(true);user.setTotpEnabled(false);assertThatThrownBy(()->service.get(auth)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(companies,templates,members,slaRules,correspondents);}
 @Test void anonymousAndRevokedMembershipCannotReadCounts(){assertThatThrownBy(()->service.get(null)).isInstanceOf(AccessDeniedException.class);doThrow(new AccessDeniedException("Suspended")).when(access).requireActiveAccess(id);assertThatThrownBy(()->service.get(auth)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(companies,templates,members,slaRules,correspondents);}
}
