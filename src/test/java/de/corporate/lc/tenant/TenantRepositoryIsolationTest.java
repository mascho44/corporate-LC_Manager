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
 @Autowired de.corporate.lc.tenant.repository.TenantMembershipRepository memberships;
 @Autowired jakarta.persistence.EntityManager entityManager;
 @Autowired de.corporate.lc.document.repository.DocumentInboxRepository inbox;
 @Autowired de.corporate.lc.document.repository.LcDocumentRepository documents;
 @Autowired de.corporate.lc.training.repository.TrainingSessionRepository training;
 @Autowired de.corporate.lc.training.repository.TrainingLearningControlRepository learningControls;
 @Autowired de.corporate.lc.audit.repository.AuditEventRepository auditEvents;
 @Autowired de.corporate.lc.messaging.repository.OutboxMessageRepository outbox;
 @Autowired de.corporate.lc.company.repository.CompanyProfileRepository companies;
 @Autowired de.corporate.lc.document.repository.DocumentTemplateRepository templates;
 @Autowired de.corporate.lc.imports.repository.SwiftImportRecordRepository imports;
 @Autowired de.corporate.lc.charges.ChargeProfileRepository chargeProfiles;
 @Autowired de.corporate.lc.charges.ChargeEstimateRepository chargeEstimates;
 @Autowired de.corporate.lc.rulepack.PackVersionRepository packVersions;
 @Autowired de.corporate.lc.rulepack.PackSelectionRepository packSelections;
 @Autowired de.corporate.lc.lc.repository.LcNoteRepository notes;
 @Autowired de.corporate.lc.lc.repository.LcTaskRepository tasks;
 @Autowired de.corporate.lc.lc.repository.AmendmentRepository amendments;
 @Autowired de.corporate.lc.check.repository.DocumentCheckDecisionRepository decisions;
 @Autowired de.corporate.lc.check.repository.LcRequirementMappingRepository mappings;
 @Autowired de.corporate.lc.document.repository.DocumentDraftRepository drafts;
 @Autowired de.corporate.lc.document.repository.DocumentApprovalThresholdRepository approvalThresholds;
 @Autowired de.corporate.lc.document.repository.DocumentComparisonRepository comparisons;
 @Autowired de.corporate.lc.email.repository.EmailDeliveryRepository deliveries;
 final UUID foreignTenant=UUID.fromString("00000000-0000-0000-0000-000000000099");
 UUID seedMembership(AppUser user,AppRole role,UUID tenantId){var id=UUID.randomUUID();entityManager.createNativeQuery("insert into tenant_membership(id,tenant_id,user_id,role_id,active) values(:id,:tenant,:user,:role,true)").setParameter("id",id).setParameter("tenant",tenantId).setParameter("user",user.getId()).setParameter("role",role.getId()).executeUpdate();return id;}
 @Test void membershipsExposeOnlyCurrentTenantAndInheritRolePermissions(){
  var ownRole=createRole("Own membership role");ownRole.setPermissions(java.util.Set.of(UserPermission.USER_MANAGE));roles.flush();var own=createTenantUser("own-membership",ownRole);var ownId=seedMembership(own,ownRole,Tenant.DEFAULT_ID);
  AppUser foreign;UUID foreignId;try(var scope=TenantContext.open(foreignTenant)){var role=createRole("Foreign membership role");foreign=createTenantUser("foreign-membership",role);foreignId=seedMembership(foreign,role,foreignTenant);}
  var service=new de.corporate.lc.tenant.service.TenantMembershipService(memberships);assertThat(service.list()).hasSize(1);assertThat(service.forUser(own.getId()).orElseThrow().permissions()).containsExactly(UserPermission.USER_MANAGE);assertThat(service.forUser(foreign.getId())).isEmpty();assertThat(memberships.findById(foreignId)).isEmpty();assertThat(memberships.findAllById(java.util.List.of(ownId,foreignId))).hasSize(1);assertThat(memberships.findAll(org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements()).isEqualTo(1);
  ownRole.setPermissions(java.util.Set.of(UserPermission.AUDIT_VIEW));roles.flush();assertThat(service.forUser(own.getId()).orElseThrow().permissions()).containsExactly(UserPermission.AUDIT_VIEW);
 }
 @Test void membershipCannotBeDeletedThroughTheReadModel(){
  var role=createRole("Read-only membership");var user=createTenantUser("readonly-membership",role);var id=seedMembership(user,role,Tenant.DEFAULT_ID);
  assertThatThrownBy(()->{memberships.deleteById(id);memberships.flush();}).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 AppUser createTenantUser(String name,AppRole role){var u=new AppUser();u.setUsername(name);u.setDisplayName("Synthetic user");u.setPasswordHash("synthetic-hash");u.setAssignedRole(role);return users.saveAndFlush(u);}
 @Test void standardUserAndRoleReadsAndDeletionAreTenantScoped(){
  var ownRole=createRole("Own identity role");var own=createTenantUser("own-identity",ownRole);AppRole foreignRole;AppUser foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreignRole=createRole("Foreign identity role");foreign=createTenantUser("foreign-identity",foreignRole);}
  assertThat(users.findAll()).extracting(AppUser::getId).containsExactly(own.getId());assertThat(users.count()).isEqualTo(1);assertThat(users.findAll(org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements()).isEqualTo(1);
  assertThat(users.findAllById(java.util.List.of(own.getId(),foreign.getId()))).hasSize(1);assertThat(users.existsById(foreign.getId())).isFalse();assertThat(roles.count()).isEqualTo(1);assertThat(roles.findAllById(java.util.List.of(ownRole.getId(),foreignRole.getId()))).hasSize(1);
  users.deleteAllById(java.util.List.of(foreign.getId()));roles.deleteAllById(java.util.List.of(foreignRole.getId()));users.flush();roles.flush();
  try(var scope=TenantContext.open(foreignTenant)){assertThat(users.findById(foreign.getId())).isPresent();assertThat(roles.findById(foreignRole.getId())).isPresent();}
  assertThat(users.findByUsernameIgnoreCase("foreign-identity")).isPresent();
 }
 @Test void profilePasswordAndTotpRejectForeignIdentityBeforeSensitiveAccess(){
  AppUser foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createTenantUser("foreign-profile",createRole("Foreign profile role"));}
  var avatars=org.mockito.Mockito.mock(de.corporate.lc.user.repository.UserAvatarRepository.class);var profile=new de.corporate.lc.user.service.ProfileService(users,avatars);var encoder=org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
  assertThatThrownBy(()->profile.profile(foreign.getUsername())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->profile.avatar(foreign.getUsername())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->profile.deleteAvatar(foreign.getUsername())).isInstanceOf(AccessDeniedException.class);org.mockito.Mockito.verifyNoInteractions(avatars);
  var service=new de.corporate.lc.user.service.UserService(users,roles,encoder);assertThatThrownBy(()->service.changePassword(foreign.getUsername(),"old","NewPassword123")).isInstanceOf(AccessDeniedException.class);
  var totp=new de.corporate.lc.user.service.TotpService(users,encoder,"");assertThatThrownBy(()->totp.enabled(foreign.getUsername())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->totp.setup(foreign.getUsername())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->totp.verifyLogin(foreign.getUsername(),"123456")).isInstanceOf(AccessDeniedException.class);org.mockito.Mockito.verifyNoInteractions(encoder);
 }
 @Test void disabledTenantPasswordResetCreatesNoTokenOrMail(){
  AppUser foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createTenantUser("foreign-reset",createRole("Foreign reset role"));foreign.setEmail("recipient@example.invalid");users.flush();}
  var tokens=org.mockito.Mockito.mock(de.corporate.lc.user.repository.PasswordResetTokenRepository.class);var mail=org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);var audit=org.mockito.Mockito.mock(de.corporate.lc.audit.service.AuditService.class);var encoder=org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
  var reset=new de.corporate.lc.user.service.PasswordResetService(users,tokens,encoder,mail,audit,true,"sender@example.invalid","https://example.invalid");reset.request(foreign.getUsername(),foreign.getEmail());org.mockito.Mockito.verifyNoInteractions(tokens,mail,audit,encoder);
  var saved=new de.corporate.lc.user.domain.PasswordResetToken("synthetic-hash",foreign.getId(),"synthetic-stamp",foreign.getEmail(),java.time.Instant.now().plusSeconds(600));org.mockito.Mockito.when(tokens.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.of(saved));
  assertThatThrownBy(()->reset.complete("A".repeat(43),"NewPassword123")).isInstanceOf(IllegalArgumentException.class);org.mockito.Mockito.verify(tokens,org.mockito.Mockito.never()).existsById(org.mockito.ArgumentMatchers.anyString());org.mockito.Mockito.verify(tokens,org.mockito.Mockito.never()).deleteForUser(org.mockito.ArgumentMatchers.any());org.mockito.Mockito.verifyNoInteractions(mail,audit,encoder);
 }
 void createLearningControl(String ruleId,boolean active){var control=new de.corporate.lc.training.domain.TrainingLearningControl();control.setRuleId(ruleId);control.setActive(active);learningControls.saveAndFlush(control);}
 @Test void standardDocumentReadsPagesAndReferencesAreTenantScoped(){
  var own=createDocument(createLc("OWN-STANDARD-READ"));de.corporate.lc.document.domain.LcDocument foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createDocument(createLc("FOREIGN-STANDARD-READ"));}
  assertThat(documents.findAll()).extracting(de.corporate.lc.document.domain.LcDocument::getId).containsExactly(own.getId());
  assertThat(documents.count()).isEqualTo(1);assertThat(documents.existsById(foreign.getId())).isFalse();
  assertThat(documents.findAllById(java.util.List.of(own.getId(),foreign.getId(),own.getId()))).extracting(de.corporate.lc.document.domain.LcDocument::getId).containsExactly(own.getId());
  assertThat(documents.findAll(org.springframework.data.domain.Sort.by("originalFilename"))).hasSize(1);
  var page=documents.findAll(org.springframework.data.domain.PageRequest.of(0,1));assertThat(page.getTotalElements()).isEqualTo(1);assertThat(page.getContent()).extracting(de.corporate.lc.document.domain.LcDocument::getId).containsExactly(own.getId());
  assertThat(documents.getReferenceById(own.getId()).getId()).isEqualTo(own.getId());assertThatThrownBy(()->documents.getReferenceById(foreign.getId())).hasRootCauseInstanceOf(jakarta.persistence.EntityNotFoundException.class);
 }
 @Test void scopedBulkDeletionPreservesForeignInboxRecords(){
  var own=createInbox();de.corporate.lc.document.domain.DocumentInboxItem foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createInbox();}
  assertThat(inbox.count()).isEqualTo(1);inbox.deleteAllById(java.util.List.of(own.getId(),foreign.getId()));inbox.flush();assertThat(inbox.count()).isZero();
  createInbox();inbox.deleteAll();inbox.flush();assertThat(inbox.count()).isZero();
  try(var scope=TenantContext.open(foreignTenant)){assertThat(inbox.findById(foreign.getId())).isPresent();assertThat(inbox.count()).isEqualTo(1);}
 }
 @Test void batchDeletionCannotBypassLifecycleOrAuditProtection(){
  var own=createDocument(createLc("OWN-BATCH-SAFETY"));
  assertThatThrownBy(documents::deleteAllInBatch).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->documents.deleteAllInBatch(java.util.List.of(own))).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->documents.deleteAllByIdInBatch(java.util.List.of(own.getId()))).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(auditEvents::deleteAllInBatch).isInstanceOf(UnsupportedOperationException.class);assertThat(documents.findById(own.getId())).isPresent();
 }
 @Test void genericReadsSupportIntegerAndCompositeTenantIdentities(){
  createCompany(10);createLearningControl("same-rule",true);try(var scope=TenantContext.open(foreignTenant)){createCompany(20);createLearningControl("same-rule",false);}
  assertThat(companies.findAllById(java.util.List.of(10,20))).hasSize(1);assertThat(companies.findAll(org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements()).isEqualTo(1);
  assertThat(learningControls.count()).isEqualTo(1);assertThat(learningControls.findAll(org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements()).isEqualTo(1);
  var foreignKey=new de.corporate.lc.training.domain.TrainingLearningControl.Key(foreignTenant,"same-rule");assertThat(learningControls.findAllById(java.util.List.of(foreignKey))).isEmpty();assertThat(learningControls.existsById(foreignKey)).isFalse();
 }
 @Test void queryByExampleAndFluentQueriesCannotBypassTenantFilters(){
  var example=org.springframework.data.domain.Example.of(new de.corporate.lc.document.domain.DocumentInboxItem());
  assertThatThrownBy(()->inbox.findOne(example)).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->inbox.findAll(example)).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->inbox.findAll(example,org.springframework.data.domain.Sort.unsorted())).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->inbox.findAll(example,org.springframework.data.domain.PageRequest.of(0,1))).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->inbox.count(example)).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->inbox.exists(example)).isInstanceOf(UnsupportedOperationException.class);
  assertThatThrownBy(()->inbox.findBy(example,query->query.all())).isInstanceOf(UnsupportedOperationException.class);
 }
 de.corporate.lc.document.domain.DocumentComparison createComparison(LetterOfCredit lc){var c=new de.corporate.lc.document.domain.DocumentComparison();c.lcId=lc.getId();c.beforeDocumentId=UUID.randomUUID();c.afterDocumentId=UUID.randomUUID();c.resultJson="{}";c.createdBy="synthetic-user";c.createdAt=java.time.LocalDateTime.now();return comparisons.saveAndFlush(c);}
 de.corporate.lc.email.domain.EmailDelivery createDelivery(LetterOfCredit lc){var e=new de.corporate.lc.email.domain.EmailDelivery();e.setLetterOfCredit(lc);e.setRecipients("recipient@example.invalid");e.setSubject("Synthetic subject");e.setStatus("FAILED");e.setSentBy("synthetic-user");return deliveries.saveAndFlush(e);}
 @Test void comparisonAndEmailHistoryAreTenantLocal(){
  var ownLc=createLc("OWN-HISTORY");var ownComparison=createComparison(ownLc);var ownEmail=createDelivery(ownLc);
  LetterOfCredit foreignLc;de.corporate.lc.document.domain.DocumentComparison foreignComparison;de.corporate.lc.email.domain.EmailDelivery foreignEmail;
  try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-HISTORY");foreignComparison=createComparison(foreignLc);foreignEmail=createDelivery(foreignLc);}
  assertThat(comparisons.findById(foreignComparison.id)).isEmpty();assertThat(comparisons.findByLcIdOrderByCreatedAtDesc(foreignLc.getId())).isEmpty();assertThat(comparisons.findAll()).extracting(c->c.id).containsExactly(ownComparison.id);
  assertThat(deliveries.findById(foreignEmail.getId())).isEmpty();assertThat(deliveries.findByLetterOfCreditIdOrderBySentAtDesc(foreignLc.getId())).isEmpty();assertThat(deliveries.findAll()).extracting(de.corporate.lc.email.domain.EmailDelivery::getId).containsExactly(ownEmail.getId());
  var comparisonService=new de.corporate.lc.document.service.DocumentComparisonService(documents,comparisons,new com.fasterxml.jackson.databind.ObjectMapper());assertThat(comparisonService.history(foreignLc.getId())).isEmpty();assertThat(comparisonService.history(ownLc.getId())).hasSize(1);
  var emailService=new de.corporate.lc.email.service.EmailService(null,lcs,documents,deliveries,false,"");assertThatThrownBy(()->emailService.history(foreignLc.getId())).isInstanceOf(java.util.NoSuchElementException.class);assertThat(emailService.history(ownLc.getId())).hasSize(1);
 }
 @Test void foreignAttachmentsCannotBeComparedOrSentAndDoNotContactMail(){
  var ownLc=createLc("OWN-ATTACHMENT");var own=createDocument(ownLc);LetterOfCredit foreignLc;de.corporate.lc.document.domain.LcDocument foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-ATTACHMENT");foreign=createDocument(foreignLc);}
  var comparisonService=new de.corporate.lc.document.service.DocumentComparisonService(documents,comparisons,new com.fasterxml.jackson.databind.ObjectMapper());
  assertThatThrownBy(()->comparisonService.compare(ownLc.getId(),own.getId(),foreign.getId(),"synthetic-user")).isInstanceOf(java.util.NoSuchElementException.class);assertThat(comparisons.findAll()).isEmpty();
  var mail=org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);var emailService=new de.corporate.lc.email.service.EmailService(mail,lcs,documents,deliveries,true,"sender@example.invalid");
  var request=new de.corporate.lc.email.api.EmailSendRequest(java.util.List.of("recipient@example.invalid"),"Synthetic subject","Synthetic body",java.util.List.of(own.getId(),foreign.getId()));
  assertThatThrownBy(()->emailService.send(ownLc.getId(),request,"synthetic-user")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->emailService.send(foreignLc.getId(),request,"synthetic-user")).isInstanceOf(java.util.NoSuchElementException.class);
  assertThat(deliveries.findAll()).isEmpty();org.mockito.Mockito.verifyNoInteractions(mail);
 }
 @Test void loadedForeignComparisonCannotBeChanged(){
  de.corporate.lc.document.domain.DocumentComparison foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createComparison(createLc("FOREIGN-COMPARISON-WRITE"));}
  foreign.resultJson="{\"forbidden\":true}";assertThatThrownBy(()->comparisons.saveAndFlush(foreign)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 @Test void loadedForeignDeliveryCannotBeChanged(){
  de.corporate.lc.email.domain.EmailDelivery foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createDelivery(createLc("FOREIGN-EMAIL-WRITE"));}
  foreign.setSubject("Forbidden");assertThatThrownBy(()->deliveries.saveAndFlush(foreign)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 de.corporate.lc.document.domain.DocumentDraft createDraft(LetterOfCredit lc){var d=new de.corporate.lc.document.domain.DocumentDraft();d.setLcId(lc.getId());d.setDocumentType(de.corporate.lc.document.domain.DocumentType.COMMERCIAL_INVOICE);d.setDocumentNumber("SYNTHETIC-1");d.setDataJson("{}");d.setCreatedBy("synthetic-user");d.setUpdatedBy("synthetic-user");return drafts.saveAndFlush(d);}
 de.corporate.lc.document.domain.DocumentApprovalThreshold createThreshold(int approvals){var t=new de.corporate.lc.document.domain.DocumentApprovalThreshold();t.setCurrency("EUR");t.setMinimumAmount(java.math.BigDecimal.ZERO);t.setRequiredApprovals(approvals);return approvalThresholds.saveAndFlush(t);}
 @Test void foreignDraftCannotBeViewedChangedDeletedOrApproved(){
  var own=createDraft(createLc("OWN-DRAFT"));LetterOfCredit foreignLc;de.corporate.lc.document.domain.DocumentDraft foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-DRAFT");foreign=createDraft(foreignLc);}
  assertThat(drafts.findById(foreign.getId())).isEmpty();assertThat(drafts.findByLcIdOrderByUpdatedAtDesc(foreignLc.getId())).isEmpty();assertThat(drafts.findAll()).extracting(de.corporate.lc.document.domain.DocumentDraft::getId).containsExactly(own.getId());
  var service=new de.corporate.lc.document.service.DocumentDraftService(drafts,lcs,new com.fasterxml.jackson.databind.ObjectMapper(),null,new de.corporate.lc.document.service.DocumentApprovalPolicyService(approvalThresholds));
  assertThatThrownBy(()->service.list(foreignLc.getId())).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->service.update(foreignLc.getId(),foreign.getId(),null,"synthetic-user")).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->service.status(foreignLc.getId(),foreign.getId(),de.corporate.lc.document.domain.DocumentDraftStatus.SUBMITTED,"synthetic-user")).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->service.status(foreignLc.getId(),foreign.getId(),de.corporate.lc.document.domain.DocumentDraftStatus.FINAL,"synthetic-approver")).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->service.delete(foreignLc.getId(),foreign.getId())).isInstanceOf(java.util.NoSuchElementException.class);
 }
 @Test void replacingApprovalThresholdsOnlyChangesCurrentTenant(){
  createThreshold(2);de.corporate.lc.document.domain.DocumentApprovalThreshold foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createThreshold(5);}
  var policy=new de.corporate.lc.document.service.DocumentApprovalPolicyService(approvalThresholds);
  assertThat(policy.requiredApprovals(java.math.BigDecimal.TEN,"EUR")).isEqualTo(2);assertThat(approvalThresholds.findById(foreign.getId())).isEmpty();
  policy.replace(java.util.List.of(new de.corporate.lc.document.api.ApprovalThresholdRequest("EUR",java.math.BigDecimal.ZERO,3)));approvalThresholds.flush();
  assertThat(policy.all()).hasSize(1);assertThat(policy.requiredApprovals(java.math.BigDecimal.TEN,"EUR")).isEqualTo(3);
  try(var scope=TenantContext.open(foreignTenant)){assertThat(approvalThresholds.findById(foreign.getId())).isPresent();assertThat(policy.requiredApprovals(java.math.BigDecimal.TEN,"EUR")).isEqualTo(5);}
 }
 record Children(de.corporate.lc.lc.domain.LcNote note,de.corporate.lc.lc.domain.LcTask task,de.corporate.lc.lc.domain.Amendment amendment,de.corporate.lc.check.domain.DocumentCheckDecision decision,de.corporate.lc.check.domain.LcRequirementMapping mapping){}
 Children createChildren(LetterOfCredit lc){
  var n=new de.corporate.lc.lc.domain.LcNote();n.setLetterOfCreditId(lc.getId());n.setUsername("synthetic-user");n.setContent("Synthetic note");notes.saveAndFlush(n);
  var t=new de.corporate.lc.lc.domain.LcTask();t.setLetterOfCreditId(lc.getId());t.setTitle("Synthetic task");t.setCreatedBy("synthetic-user");tasks.saveAndFlush(t);
  var a=new de.corporate.lc.lc.domain.Amendment();a.setLetterOfCredit(lc);a.setAmendmentNumber("1");amendments.saveAndFlush(a);
  var d=new de.corporate.lc.check.domain.DocumentCheckDecision();d.setLcId(lc.getId());d.setFindingCode("SYNTHETIC");d.setFindingFingerprint("synthetic-fingerprint");d.setDecision("ACCEPTED");d.setReviewedBy("synthetic-user");decisions.saveAndFlush(d);
  var m=new de.corporate.lc.check.domain.LcRequirementMapping();m.setLcId(lc.getId());m.setRequirement("Synthetic invoice");m.setDocumentType(de.corporate.lc.document.domain.DocumentType.COMMERCIAL_INVOICE);m.setMappedBy("synthetic-user");mappings.saveAndFlush(m);
  return new Children(n,t,a,d,m);
 }
 @Test void caseChildrenAndWorkQueueDoNotRevealForeignRecords(){
  var ownLc=createLc("OWN-CHILDREN");var own=createChildren(ownLc);LetterOfCredit foreignLc;Children foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-CHILDREN");foreign=createChildren(foreignLc);}
  assertThat(notes.findTop100ByLetterOfCreditIdOrderByCreatedAtDesc(foreignLc.getId())).isEmpty();assertThat(notes.findById(foreign.note().getId())).isEmpty();assertThat(notes.findAll()).hasSize(1);
  assertThat(tasks.findByLetterOfCreditIdOrderByCompletedAscDueDateAscCreatedAtDesc(foreignLc.getId())).isEmpty();assertThat(tasks.findById(foreign.task().getId())).isEmpty();assertThat(tasks.findByCompletedFalseOrderByDueDateAscCreatedAtAsc()).extracting(de.corporate.lc.lc.domain.LcTask::getId).containsExactly(own.task().getId());
  assertThat(amendments.findByLetterOfCreditIdOrderByImportedAtDesc(foreignLc.getId())).isEmpty();assertThat(amendments.findById(foreign.amendment().getId())).isEmpty();assertThat(amendments.existsByLetterOfCreditIdAndAmendmentNumber(foreignLc.getId(),"1")).isFalse();assertThat(amendments.existsByLetterOfCreditIdAndAmendmentNumber(ownLc.getId(),"1")).isTrue();
  assertThat(decisions.findByLcId(foreignLc.getId())).isEmpty();assertThat(decisions.findById(foreign.decision().getId())).isEmpty();assertThat(decisions.findByLcIdAndFindingCodeAndDocumentNameAndFindingFingerprint(foreignLc.getId(),"SYNTHETIC","","synthetic-fingerprint")).isEmpty();
  assertThat(mappings.findByLcIdOrderByMappedAtDesc(foreignLc.getId())).isEmpty();assertThat(mappings.findByLcIdAndRequirement(foreignLc.getId(),"Synthetic invoice")).isEmpty();assertThat(mappings.findById(foreign.mapping().getId())).isEmpty();
  decisions.deleteAllByLcId(foreignLc.getId());decisions.flush();
  try(var scope=TenantContext.open(foreignTenant)){assertThat(decisions.findById(foreign.decision().getId())).isPresent();assertThat(tasks.findById(own.task().getId())).isEmpty();}
 }
 @Test void loadedForeignCaseChildCannotBeChanged(){
  Children foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createChildren(createLc("FOREIGN-CHILD-WRITE"));}
  foreign.task().setTitle("Forbidden change");assertThatThrownBy(()->tasks.saveAndFlush(foreign.task())).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
 de.corporate.lc.rulepack.StoredPackVersion createPackVersion(String packId){var v=new de.corporate.lc.rulepack.StoredPackVersion();v.packId=packId;v.version="1.0";v.definitionJson="{}";v.checksum="synthetic";v.testsPassed=true;v.importedBy="synthetic-user";return packVersions.saveAndFlush(v);}
 de.corporate.lc.rulepack.PackSelection createSelection(de.corporate.lc.rulepack.StoredPackVersion version){var s=new de.corporate.lc.rulepack.PackSelection();s.id=version.packId;s.activeVersionId=version.id;return packSelections.saveAndFlush(s);}
 @Test void packVersionsIdsAndActivationLocksAreTenantLocal(){
  var own=createPackVersion("same-pack");createSelection(own);de.corporate.lc.rulepack.StoredPackVersion foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createPackVersion("same-pack");createSelection(foreign);createSelection(createPackVersion("foreign-only"));}
  assertThat(packVersions.findAllByOrderByImportedAtDesc()).extracting(v->v.id).containsExactly(own.id);assertThat(packVersions.findById(foreign.id)).isEmpty();
  assertThat(packVersions.existsByPackIdAndVersion("foreign-only","1.0")).isFalse();assertThat(packVersions.existsByPackIdAndVersion("same-pack","1.0")).isTrue();
  assertThat(packSelections.locked("foreign-only")).isEmpty();assertThat(packSelections.existsById("foreign-only")).isFalse();assertThat(packSelections.findAll()).hasSize(1);
  assertThat(packSelections.findById(new de.corporate.lc.rulepack.PackSelection.Key(foreignTenant,"same-pack"))).isEmpty();
  var service=new de.corporate.lc.rulepack.InternalPackService(new de.corporate.lc.rulepack.PackCodec(new com.fasterxml.jackson.databind.ObjectMapper()),packVersions,packSelections,org.mockito.Mockito.mock(de.corporate.lc.audit.service.AuditService.class));
  assertThatThrownBy(()->service.test(foreign.id)).isInstanceOf(java.util.NoSuchElementException.class);assertThatThrownBy(()->service.activate(foreign.id,true,null)).isInstanceOf(java.util.NoSuchElementException.class);
  service.deactivate("same-pack",null);packSelections.flush();assertThat(packSelections.findById("same-pack").orElseThrow().activeVersionId).isNull();
  try(var scope=TenantContext.open(foreignTenant)){assertThat(packSelections.findById("same-pack").orElseThrow().activeVersionId).isEqualTo(foreign.id);}
 }
 @Test void ruleEvaluationRejectsForeignLcBeforeReadingPacks(){
  LetterOfCredit foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=new LetterOfCredit();}
  var service=new de.corporate.lc.rulepack.InternalPackService(null,packVersions,packSelections,org.mockito.Mockito.mock(de.corporate.lc.audit.service.AuditService.class));
  assertThatThrownBy(()->service.evaluate(foreign,java.util.List.of())).isInstanceOf(AccessDeniedException.class);
 }
 de.corporate.lc.imports.domain.SwiftImportRecord createImport(String reference){var r=new de.corporate.lc.imports.domain.SwiftImportRecord();r.setFilename("synthetic.txt");r.setReference(reference);r.setStatus("SUCCESS");return imports.saveAndFlush(r);}
 de.corporate.lc.charges.ChargeProfile createChargeProfile(){var p=new de.corporate.lc.charges.ChargeProfile();p.name="Synthetic tariff";p.currency="EUR";p.rulesJson="[]";p.createdBy="synthetic-user";return chargeProfiles.saveAndFlush(p);}
 de.corporate.lc.charges.ChargeEstimate createEstimate(LetterOfCredit lc,de.corporate.lc.charges.ChargeProfile profile){var e=new de.corporate.lc.charges.ChargeEstimate();e.lcId=lc.getId();e.profileId=profile.id;e.resultJson="{}";e.profileSnapshot="{}";e.createdBy="synthetic-user";return chargeEstimates.saveAndFlush(e);}
 @Test void importHistoryCountsAndIdsAreTenantLocal(){
  var own=createImport("OWN-IMPORT");de.corporate.lc.imports.domain.SwiftImportRecord foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreign=createImport("FOREIGN-IMPORT");}
  assertThat(imports.findTop20ByOrderByImportedAtDesc()).extracting(de.corporate.lc.imports.domain.SwiftImportRecord::getId).containsExactly(own.getId());
  assertThat(imports.findById(foreign.getId())).isEmpty();assertThat(imports.findAll()).hasSize(1);assertThat(imports.countByStatus("SUCCESS")).isEqualTo(1);
 }
 @Test void chargesCannotListReadOrUseForeignProfilesOrEstimates(){
  var ownLc=createLc("OWN-CHARGES");var ownProfile=createChargeProfile();var own=createEstimate(ownLc,ownProfile);
  LetterOfCredit foreignLc;de.corporate.lc.charges.ChargeProfile foreignProfile;de.corporate.lc.charges.ChargeEstimate foreign;
  try(var scope=TenantContext.open(foreignTenant)){foreignLc=createLc("FOREIGN-CHARGES");foreignProfile=createChargeProfile();foreign=createEstimate(foreignLc,foreignProfile);}
  assertThat(chargeProfiles.findAll()).extracting(p->p.id).containsExactly(ownProfile.id);assertThat(chargeProfiles.findById(foreignProfile.id)).isEmpty();assertThat(chargeEstimates.findById(foreign.id)).isEmpty();
  assertThat(chargeEstimates.findByLcIdOrderByCreatedAtDesc(foreignLc.getId())).isEmpty();assertThat(chargeEstimates.findAll()).extracting(e->e.id).containsExactly(own.id);
  var controller=new de.corporate.lc.charges.ChargeController(chargeProfiles,chargeEstimates,lcs,new com.fasterxml.jackson.databind.ObjectMapper(),org.mockito.Mockito.mock(de.corporate.lc.audit.service.AuditService.class));
  assertThatThrownBy(()->controller.history(foreignLc.getId())).isInstanceOf(java.util.NoSuchElementException.class);
  assertThatThrownBy(()->controller.estimate(ownLc.getId(),new de.corporate.lc.charges.ChargeController.EstimateRequest(foreignProfile.id,java.util.Map.of()),null)).isInstanceOf(java.util.NoSuchElementException.class);
 }
 @Test void modifyingForeignTariffIsRejected(){
  de.corporate.lc.charges.ChargeProfile foreign;try(var scope=TenantContext.open(foreignTenant)){foreign=createChargeProfile();}
  foreign.name="Forbidden";assertThatThrownBy(()->chargeProfiles.saveAndFlush(foreign)).satisfies(error->{Throwable root=error;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(AccessDeniedException.class);});
 }
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
