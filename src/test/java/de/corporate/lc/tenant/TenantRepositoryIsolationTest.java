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
 @Autowired de.corporate.lc.training.repository.TrainingSessionRepository training;
 @Autowired de.corporate.lc.training.repository.TrainingLearningControlRepository learningControls;
 @Autowired de.corporate.lc.audit.repository.AuditEventRepository auditEvents;
 @Autowired de.corporate.lc.messaging.repository.OutboxMessageRepository outbox;
 @Autowired de.corporate.lc.company.repository.CompanyProfileRepository companies;
 @Autowired de.corporate.lc.document.repository.DocumentTemplateRepository templates;
 final UUID foreignTenant=UUID.fromString("00000000-0000-0000-0000-000000000099");
 de.corporate.lc.company.domain.CompanyProfile createCompany(int id){var c=new de.corporate.lc.company.domain.CompanyProfile();c.setId(id);c.setLegalName("Synthetic company "+id);c.setLogo(new byte[]{1});c.setLogoContentType("image/png");return companies.saveAndFlush(c);}
 de.corporate.lc.document.domain.DocumentTemplate createTemplate(Integer companyId,String name,byte content){var t=new de.corporate.lc.document.domain.DocumentTemplate();t.setCompanyId(companyId);t.setCompanyName(name);t.setDocumentType(de.corporate.lc.document.domain.DocumentType.COMMERCIAL_INVOICE);t.setOriginalFilename("synthetic.docx");t.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");t.setContent(new byte[]{content});t.setFileSize(1);t.setUploadedBy("synthetic-user");return templates.saveAndFlush(t);}
 @Test void companyListsLogoLookupsAndDefaultAreTenantLocal(){
  var own=createCompany(10);de.corporate.lc.company.domain.CompanyProfile foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createCompany(1);}
  var service=new de.corporate.lc.company.service.CompanyProfileService(companies);
  assertThat(service.profile().getId()).isEqualTo(own.getId());assertThat(service.all()).hasSize(1);
  assertThat(companies.findById(foreign.getId())).isEmpty();assertThatThrownBy(()->service.profile(foreign.getId())).isInstanceOf(IllegalArgumentException.class);
  try(var scope=TenantContext.open(foreignTenant)){assertThat(service.profile().getId()).isEqualTo(foreign.getId());assertThat(companies.findById(own.getId())).isEmpty();}
  foreign.setLegalName("Forbidden");assertThatThrownBy(()->companies.saveAndFlush(foreign)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 @Test void templateDownloadsFallbackAndCompanySelectionAreTenantLocal(){
  var ownCompany=createCompany(10);var own=createTemplate(null,"*",(byte)1);de.corporate.lc.document.domain.DocumentTemplate foreign;
  try(var scope=TenantContext.open(foreignTenant)){createCompany(20);foreign=createTemplate(null,"*",(byte)2);createTemplate(20,"Same name",(byte)3);}
  var service=new de.corporate.lc.document.service.DocumentTemplateService(templates,new de.corporate.lc.company.service.CompanyProfileService(companies));
  assertThat(templates.findAll()).extracting(de.corporate.lc.document.domain.DocumentTemplate::getId).containsExactly(own.getId());
  assertThat(templates.findById(foreign.getId())).isEmpty();assertThatThrownBy(()->service.one(foreign.getId())).isInstanceOf(java.util.NoSuchElementException.class);assertThatThrownBy(()->service.delete(foreign.getId())).isInstanceOf(java.util.NoSuchElementException.class);
  assertThat(service.content(de.corporate.lc.document.domain.DocumentType.COMMERCIAL_INVOICE,ownCompany.getId(),"Same name").orElseThrow()).containsExactly((byte)1);
  assertThatThrownBy(()->service.content(de.corporate.lc.document.domain.DocumentType.COMMERCIAL_INVOICE,20,"Same name")).isInstanceOf(IllegalArgumentException.class);
  try(var scope=TenantContext.open(foreignTenant)){assertThat(service.content(de.corporate.lc.document.domain.DocumentType.COMMERCIAL_INVOICE,"*").orElseThrow()).containsExactly((byte)2);}
 }
 de.corporate.lc.audit.domain.AuditEvent createAudit(String details){var event=new de.corporate.lc.audit.domain.AuditEvent();event.setUsername("synthetic-user");event.setAction("LC_UPDATED");event.setEntityId("shared-synthetic-id");event.setDetails(details);event.setSuccessful(true);return auditEvents.saveAndFlush(event);}
 @Test void auditListCsvTimelineAndCaseExportCannotSeeForeignEvents(){
  var own=createAudit("OWN-AUDIT-DETAILS");de.corporate.lc.audit.domain.AuditEvent foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createAudit("FOREIGN-AUDIT-DETAILS");}
  assertThat(auditEvents.findTop200ByOrderByOccurredAtDesc()).extracting(de.corporate.lc.audit.domain.AuditEvent::getId).containsExactly(own.getId());
  assertThat(auditEvents.findTop100ByEntityIdOrderByOccurredAtDesc("shared-synthetic-id")).extracting(de.corporate.lc.audit.domain.AuditEvent::getId).containsExactly(own.getId());
  assertThat(auditEvents.findByEntityIdInOrderByOccurredAtAsc(java.util.List.of("shared-synthetic-id"))).extracting(de.corporate.lc.audit.domain.AuditEvent::getId).containsExactly(own.getId());
  assertThat(auditEvents.findById(foreign.getId())).isEmpty();assertThat(auditEvents.findAll()).hasSize(1);
  var service=new de.corporate.lc.audit.service.AuditService(auditEvents,org.mockito.Mockito.mock(de.corporate.lc.messaging.service.OutboxService.class));
  assertThat(new String(service.csv(),java.nio.charset.StandardCharsets.UTF_8)).contains("OWN-AUDIT-DETAILS").doesNotContain("FOREIGN-AUDIT-DETAILS");
 }
 @Test void auditEventsCannotBeChangedThroughJpa(){
  var event=createAudit("Original");event.setDetails("Forbidden change");
  assertThatThrownBy(()->auditEvents.saveAndFlush(event)).hasRootCauseInstanceOf(IllegalStateException.class);
 }
 @Test void auditAndOutboxCarryTenantAndForeignMessagesCannotBePublishedOrRetried(){
  var publisher=org.mockito.Mockito.mock(de.corporate.lc.messaging.service.MessagePublisher.class);
  var delivery=new de.corporate.lc.messaging.service.OutboxService(outbox,new com.fasterxml.jackson.databind.ObjectMapper(),publisher,8);
  var audit=new de.corporate.lc.audit.service.AuditService(auditEvents,delivery);
  audit.record("synthetic-user","LC_UPDATED","LETTER_OF_CREDIT","own-id","Own event",true,null);outbox.flush();
  de.corporate.lc.messaging.domain.OutboxMessage foreign;
  try(var scope=TenantContext.open(foreignTenant)){
   audit.record("synthetic-foreign","LC_UPDATED","LETTER_OF_CREDIT","foreign-id","Foreign event",true,null);outbox.flush();
   assertThat(auditEvents.findAll()).allMatch(e->foreignTenant.equals(e.getTenantId()));
   foreign=outbox.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt("PENDING",java.time.LocalDateTime.now()).get(0);
   assertThat(foreign.getTenantId()).isEqualTo(foreignTenant);
  }
  assertThat(outbox.countByStatus("PENDING")).isEqualTo(1);assertThat(outbox.findById(foreign.getId())).isEmpty();
  assertThat(outbox.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt("PENDING",java.time.LocalDateTime.now())).allMatch(m->Tenant.DEFAULT_ID.equals(m.getTenantId()));
  assertThat(outbox.findTop100ByStatusOrderByCreatedAtDesc("PENDING")).hasSize(1);
  assertThatThrownBy(()->delivery.retry(foreign.getId())).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->delivery.publish(foreign)).isInstanceOf(AccessDeniedException.class);org.mockito.Mockito.verifyNoInteractions(publisher);
 }
 de.corporate.lc.training.domain.TrainingSession createTraining(String target){var session=new de.corporate.lc.training.domain.TrainingSession();session.setFilename("synthetic-training.pdf");session.setOriginalPdf(new byte[]{1});session.setExtractedText(":50:ORIGINAL");session.setStatus("DRAFT");session.setUsername("synthetic-user");session.setMessageType("MT700");session.setReviewsJson("[{\"code\":\"50\",\"originalCode\":\"50\",\"originalValue\":\"ORIGINAL\",\"value\":\""+target+"\",\"review\":\"corrected\"}]");return training.saveAndFlush(session);}
 @Test void trainingDocumentsHistoryExportsAndLocksUseTenantScopedLookups(){
  var own=createTraining("OWN");de.corporate.lc.training.domain.TrainingSession foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createTraining("FOREIGN");}
  assertThat(training.findAll()).extracting(de.corporate.lc.training.domain.TrainingSession::getId).containsExactly(own.getId());
  assertThat(training.findTop100ByOrderByCreatedAtDesc()).extracting(de.corporate.lc.training.domain.TrainingSession::getId).containsExactly(own.getId());
  assertThat(training.findById(foreign.getId())).isEmpty();assertThat(training.findForUpdate(foreign.getId())).isEmpty();
  foreign.setCorrectedText("Forbidden");assertThatThrownBy(()->training.saveAndFlush(foreign)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 @Test void learningAndActivationAreSharedWithinTenantButNeverAcrossTenants(){
  var own=createTraining("OWN");try(var scope=TenantContext.open(foreignTenant)){createTraining("FOREIGN");}
  var service=new de.corporate.lc.training.service.TrainingLearningService(training,learningControls,new com.fasterxml.jackson.databind.ObjectMapper());
  assertThat(service.apply("MT700",":50:ORIGINAL")).isEqualTo(":50:OWN");
  own.setStatus("DELETED");training.flush();assertThat(service.apply("MT700",":50:ORIGINAL")).isEqualTo(":50:OWN");
  String ruleId=service.rules().get(0).id();service.setActive(ruleId,false,"another-user-in-same-tenant");learningControls.flush();
  assertThat(service.apply("MT700",":50:ORIGINAL")).isEqualTo(":50:ORIGINAL");
  try(var scope=TenantContext.open(foreignTenant)){
   assertThat(service.apply("MT700",":50:ORIGINAL")).isEqualTo(":50:FOREIGN");assertThat(learningControls.findByRuleId(ruleId)).isEmpty();
   service.setActive(ruleId,true,"foreign-user");learningControls.flush();
   assertThat(learningControls.findById(new de.corporate.lc.training.domain.TrainingLearningControl.Key(Tenant.DEFAULT_ID,ruleId))).isEmpty();
  }
  assertThat(learningControls.findByRuleId(ruleId).orElseThrow().isActive()).isFalse();
 }
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
