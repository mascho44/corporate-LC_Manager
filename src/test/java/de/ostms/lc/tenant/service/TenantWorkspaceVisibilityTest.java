package de.ostms.lc.tenant.service;
import de.ostms.lc.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantWorkspaceVisibilityTest {
 private final TenantWorkspaceService.Workspace home=new TenantWorkspaceService.Workspace(Tenant.DEFAULT_ID,"default","Alt");
 private final TenantWorkspaceService.Workspace firma=new TenantWorkspaceService.Workspace(UUID.randomUUID(),"firma","Firma");

 @Test void hidesPlatformHomeWhenOtherWorkspacesExist(){assertEquals(List.of(firma),TenantWorkspaceService.withoutPlatformHome(List.of(home,firma),firma.id()));}
 @Test void keepsItWhenItIsTheOnlyChoice(){assertEquals(List.of(home),TenantWorkspaceService.withoutPlatformHome(List.of(home),firma.id()));}
 @Test void keepsItWhileSelected(){assertEquals(List.of(home,firma),TenantWorkspaceService.withoutPlatformHome(List.of(home,firma),Tenant.DEFAULT_ID));}
}
