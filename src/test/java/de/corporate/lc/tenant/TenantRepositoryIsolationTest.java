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
 @Autowired de.corporate.lc.document.repository.DocumentInboxRepository inbox;
 @Autowired de.corporate.lc.document.repository.LcDocumentRepository documents;
 final UUID foreignTenant=UUID.fromString("00000000-0000-0000-0000-000000000099");
 LetterOfCredit createLc(String reference){var lc=new LetterOfCredit();lc.setReference(reference);return lcs.saveAndFlush(lc);}
 AppRole createRole(String name){var role=new AppRole();role.setName(name);role.setBaseRole(UserRole.ADMIN);role.setSystemRole(true);return roles.saveAndFlush(role);}
 de.corporate.lc.document.domain.DocumentInboxItem createInbox(){var item=new de.corporate.lc.document.domain.DocumentInboxItem();item.setOriginalFilename("synthetic.pdf");item.setContentType("application/pdf");item.setContent(new byte[]{1});item.setFileSize(1);item.setReceivedBy("synthetic-user");item.setExtractionStatus("QUEUED");return inbox.saveAndFlush(item);}
 de.corporate.lc.document.domain.LcDocument createDocument(LetterOfCredit lc){var doc=new de.corporate.lc.document.domain.LcDocument();doc.setLetterOfCredit(lc);doc.setDocumentType(de.corporate.lc.document.domain.DocumentType.ANNEX);doc.setOriginalFilename("synthetic.pdf");doc.setContentType("application/pdf");doc.setContent(new byte[]{1});doc.setFileSize(1);return documents.saveAndFlush(doc);}
 @Test void inboxListDownloadLookupLocksAndQueueCandidatesAreScoped(){
  var own=createInbox();de.corporate.lc.document.domain.DocumentInboxItem foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createInbox();}
  assertThat(inbox.findTop100ByStatusOrderByReceivedAtDesc("OPEN")).extracting(de.corporate.lc.document.domain.DocumentInboxItem::getId).containsExactly(own.getId());
  assertThat(inbox.findById(foreign.getId())).isEmpty();assertThat(inbox.findForUpdate(foreign.getId())).isEmpty();
  assertThat(inbox.findExtractionCandidates(java.time.LocalDateTime.now(),org.springframework.data.domain.PageRequest.of(0,10))).containsExactly(own.getId());
 }
 @Test void documentIdDownloadAndLcListingCannotReadForeignDocuments(){
  var ownLc=createLc("OWN-DOCUMENT");var own=createDocument(ownLc);de.corporate.lc.document.domain.LcDocument foreign;LetterOfCredit foreignLc;
  try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-DOCUMENT");foreign=createDocument(foreignLc);}
  assertThat(documents.findById(foreign.getId())).isEmpty();assertThat(documents.findById(own.getId())).isPresent();
  assertThat(documents.findByLetterOfCreditIdOrderByUploadedAtDesc(foreignLc.getId())).isEmpty();assertThat(documents.countByLetterOfCreditId(foreignLc.getId())).isZero();
  var service=new de.corporate.lc.document.service.DocumentService(documents,lcs,org.mockito.Mockito.mock(de.corporate.lc.document.service.DocumentExtractionService.class));
  assertThatThrownBy(()->service.one(foreign.getId())).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->service.forLc(foreignLc.getId())).isInstanceOf(java.util.NoSuchElementException.class);
 }
 @Test void documentCannotBeLinkedToAForeignLc(){
  LetterOfCredit foreignLc;try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-PARENT");}
  assertThatThrownBy(()->createDocument(foreignLc)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
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
