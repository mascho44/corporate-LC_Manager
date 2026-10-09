package de.ostms.lc.tenant;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.tenant.repository.*;
import de.ostms.lc.tenant.service.*;
import de.ostms.lc.user.domain.*;
import de.ostms.lc.user.repository.*;
import de.ostms.lc.user.service.PlatformAdministrationService;
import de.ostms.lc.user.service.TenantAdministrationLock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Restoring access changes an existing suspension row; its tenant check runs at flush time, still inside the tenant scope. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:platformaccess;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class PlatformMembershipAccessTransactionTest {
 @Autowired TenantRepository tenants;@Autowired AppUserRepository users;@Autowired AppRoleRepository roles;@Autowired TenantMembershipRepository memberships;@Autowired TenantMembershipSuspensionRepository suspensions;@Autowired EntityManager entityManager;
 @Test void suspendAndRestoreBothSucceedAndPersist(){
  tenants.saveAndFlush(new Tenant());
  var foreign=new Tenant("foreign","Foreign","en",true,true);tenants.saveAndFlush(foreign);var foreignId=foreign.getId();
  var role=new AppRole();role.setName("Synthetic home role");role.setBaseRole(UserRole.ADMIN);role.setSystemRole(true);roles.saveAndFlush(role);
  var user=new AppUser();user.setUsername("synthetic-shared");user.setDisplayName("Synthetic");user.setPasswordHash("x");user.setAssignedRole(role);users.saveAndFlush(user);
  AppRole foreignRole;
  try(var scope=TenantContext.open(foreignId)){
   var r=new AppRole();r.setName("Synthetic foreign role");r.setBaseRole(UserRole.USER);r.setSystemRole(false);foreignRole=roles.saveAndFlush(r);
   entityManager.createNativeQuery("insert into tenant_membership(id,tenant_id,user_id,role_id,active) values(:id,:tenant,:user,:role,true)").setParameter("id",UUID.randomUUID()).setParameter("tenant",foreignId).setParameter("user",user.getId()).setParameter("role",foreignRole.getId()).executeUpdate();
  }
  var access=new TenantMembershipService(memberships,suspensions,tenants);
  var platform=mock(PlatformAdministrationService.class);var audit=mock(AuditService.class);
  var service=new PlatformMembershipService(platform,users,tenants,roles,memberships,suspensions,access,mock(TenantMembershipProvisioningStore.class),new TenantAdministrationLock(tenants),audit);
  var auth=new TestingAuthenticationToken("platform","x");
  var suspended=service.changeAccess(foreignId,user.getId(),true,auth);
  // the transaction commit flushes in the platform (default) scope
  entityManager.flush();
  assertThat(suspended.suspended()).isTrue();
  var restored=service.changeAccess(foreignId,user.getId(),false,auth);
  entityManager.flush();
  assertThat(restored.suspended()).isFalse();
  entityManager.clear();
  try(var scope=TenantContext.open(foreignId)){assertThat(suspensions.findByUserId(user.getId())).get().extracting(TenantMembershipSuspension::isSuspended).isEqualTo(false);}
 }
}
