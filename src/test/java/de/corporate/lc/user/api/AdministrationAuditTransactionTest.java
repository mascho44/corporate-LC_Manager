package de.corporate.lc.user.api;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:administrationaudit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({RoleController.class,UserController.class,RoleService.class,UserService.class,AdministrationAuditTransactionTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdministrationAuditTransactionTest {
 @TestConfiguration static class Beans{
  @Bean AuditService audit(){return mock(AuditService.class);}
  @Bean PasswordEncoder passwords(){return mock(PasswordEncoder.class);}
 }
 @Autowired RoleController roleController;@Autowired UserController userController;
 @Autowired AppRoleRepository roles;@Autowired AppUserRepository users;@Autowired AuditService audit;
 @Test void auditFailureRollsBackRoleAndUserMutations(){
  var role=new AppRole();role.setName("Synthetic audit role");role.setBaseRole(UserRole.EDITOR);role.setPermissions(Set.of(UserPermission.LC_EDIT));role=roles.saveAndFlush(role);
  var user=new AppUser();user.setUsername("synthetic-audit-user");user.setDisplayName("Synthetic user");user.setEmail("user@example.invalid");user.setPasswordHash("synthetic-secret-hash");user.setAssignedRole(role);user=users.saveAndFlush(user);
  var auth=UsernamePasswordAuthenticationToken.authenticated("synthetic-admin",null,List.of());var roleId=role.getId();var userId=user.getId();
  AuditService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
  doThrow(new IllegalStateException("Synthetic audit failure")).when(target).recordChangeInTransaction(any(),anyString(),anyString(),any(),anyString(),nullable(String.class),nullable(String.class));
  assertThatThrownBy(()->roleController.update(roleId,new RoleRequest("Changed name",UserRole.VIEWER,Set.of()),auth)).isInstanceOf(IllegalStateException.class);
  assertThat(roles.findById(roleId).orElseThrow().getName()).isEqualTo("Synthetic audit role");
  var request=new UserRequest(user.getUsername(),user.getDisplayName(),user.getEmail(),null,roleId,false);
  assertThatThrownBy(()->userController.update(userId,request,auth)).isInstanceOf(IllegalStateException.class);
  assertThat(users.findById(userId).orElseThrow().isActive()).isTrue();
  assertThatThrownBy(()->userController.delete(userId,auth)).isInstanceOf(IllegalStateException.class);assertThat(users.existsById(userId)).isTrue();
  reset(target);userController.update(userId,request,auth);
  verify(target).recordChangeInTransaction(eq(auth),eq("USER_UPDATED"),eq("USER"),eq(userId),anyString(),contains("\"active\":true"),contains("\"active\":false"));
  assertThat(users.findById(userId).orElseThrow().isActive()).isFalse();
 }
 @Test void snapshotsAreAllowlistedAndEscapeNames(){
  var user=new UserView(UUID.randomUUID(),"<script>\"synthetic</script>","Private display",UUID.randomUUID(),"Private role",UserRole.EDITOR,Set.of(),true,null,"private@example.invalid");
  var value=AdministrationAuditSnapshot.user(user);assertThat(value).contains("\\\"synthetic").doesNotContain("private@example.invalid","Private display","password","totp");
  var role=new RoleView(UUID.randomUUID(),"Synthetic role",UserRole.EDITOR,false,Set.of(UserPermission.LC_EDIT,UserPermission.AUDIT_VIEW));
  assertThat(AdministrationAuditSnapshot.role(role)).contains("\"permissions\":[\"AUDIT_VIEW\",\"LC_EDIT\"]");
 }
}
