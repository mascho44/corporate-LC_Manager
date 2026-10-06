package de.corporate.lc.tenant;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.security.access.AccessDeniedException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:tenantisolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class TenantRepositoryIsolationTest {
 @Autowired LetterOfCreditRepository lcs;
 @Autowired AppRoleRepository roles;
 @Autowired AppUserRepository users;
 final UUID foreignTenant=UUID.fromString("00000000-0000-0000-0000-000000000099");
 LetterOfCredit createLc(String reference){var lc=new LetterOfCredit();lc.setReference(reference);return lcs.saveAndFlush(lc);}
 AppRole createRole(String name){var role=new AppRole();role.setName(name);role.setBaseRole(UserRole.ADMIN);role.setSystemRole(true);return roles.saveAndFlush(role);}
 @Test void guessedLcIdReferenceProjectionAndCountDoNotCrossTenantBoundary(){
  var own=createLc("OWN-REFERENCE");LetterOfCredit foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createLc("FOREIGN-REFERENCE");}
  assertThat(lcs.findAll()).extracting(LetterOfCredit::getId).containsExactly(own.getId());
  assertThat(lcs.findById(foreign.getId())).isEmpty();assertThat(lcs.existsById(foreign.getId())).isFalse();
  assertThat(lcs.findByReference("FOREIGN-REFERENCE")).isEmpty();assertThat(lcs.findForAmendment("FOREIGN-REFERENCE")).isEmpty();
  assertThat(lcs.existsByReference("FOREIGN-REFERENCE")).isFalse();assertThat(lcs.existsByReferenceAndIdNot("FOREIGN-REFERENCE",own.getId())).isFalse();
  assertThat(lcs.findAssignmentTargets()).extracting(LetterOfCreditRepository.AssignmentTarget::getId).containsExactly(own.getId());assertThat(lcs.count()).isEqualTo(1);
  try(var scope=TenantContext.open(foreignTenant)){assertThat(lcs.findById(own.getId())).isEmpty();assertThat(lcs.findById(foreign.getId())).isPresent();}
 }
 @Test void userListsRoleLookupsAndAdminCountsAreScoped(){
  var ownRole=createRole("Own role");AppRole foreignRole;AppUser foreignUser;
  try(var scope=TenantContext.open(foreignTenant)){
   foreignRole=createRole("Foreign role");var user=new AppUser();user.setUsername("synthetic-foreign");user.setDisplayName("Synthetic foreign");user.setPasswordHash("not-a-password");user.setAssignedRole(foreignRole);foreignUser=users.saveAndFlush(user);
  }
  assertThat(roles.findAllByOrderByNameAsc()).extracting(AppRole::getId).containsExactly(ownRole.getId());
  assertThat(roles.findById(foreignRole.getId())).isEmpty();assertThat(roles.existsByNameIgnoreCase("Foreign role")).isFalse();
  assertThat(roles.findByBaseRoleAndSystemRoleTrue(UserRole.ADMIN)).contains(ownRole);
  assertThat(users.findById(foreignUser.getId())).isEmpty();assertThat(users.findAllByOrderByUsernameAsc()).isEmpty();assertThat(users.findAllByActiveTrueOrderByDisplayNameAsc()).isEmpty();
  assertThat(users.countByRoleAndActiveTrue(UserRole.ADMIN)).isZero();assertThat(users.countByAssignedRoleId(foreignRole.getId())).isZero();
 }
 @Test void updatingAForeignEntityIsDeniedEvenIfAlreadyLoaded(){
  LetterOfCredit foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createLc("FOREIGN-WRITE");}
  foreign.setBeneficiary("Forbidden change");
  assertThatThrownBy(()->lcs.saveAndFlush(foreign)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 @Test void userCannotReceiveRoleFromAnotherTenant(){
  AppRole foreignRole;try(var scope=TenantContext.open(foreignTenant)){foreignRole=createRole("Foreign assignment");}
  var user=new AppUser();user.setAssignedRole(foreignRole);assertThatThrownBy(user::validateRoleTenant).isInstanceOf(AccessDeniedException.class);
 }
}
