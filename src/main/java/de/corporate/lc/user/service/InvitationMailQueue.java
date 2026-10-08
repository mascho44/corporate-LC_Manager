package de.corporate.lc.user.service;
import de.corporate.lc.tenant.domain.*;
import de.corporate.lc.tenant.repository.TenantRepository;
import de.corporate.lc.user.repository.*;
import de.corporate.lc.user.domain.PlatformInvitation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.Objects;
@Service public class InvitationMailQueue {
 private final PlatformInvitationRepository invitations;private final AppUserRepository users;private final AppRoleRepository roles;private final TenantRepository tenants;private final TenantAdministrationLock lock;private final InvitationMailCipher cipher;private final PlatformInvitationMailService delivery;private final ObjectMapper mapper=new ObjectMapper();
 public InvitationMailQueue(PlatformInvitationRepository invitations,AppUserRepository users,AppRoleRepository roles,TenantRepository tenants,TenantAdministrationLock lock,InvitationMailCipher cipher,PlatformInvitationMailService delivery){this.invitations=invitations;this.users=users;this.roles=roles;this.tenants=tenants;this.lock=lock;this.cipher=cipher;this.delivery=delivery;}
 @Transactional(propagation=Propagation.MANDATORY) public void enqueue(PlatformInvitationService.MailRequested event){
  var saved=invitations.findById(CredentialStamp.of(event.token())).orElseThrow(()->new IllegalStateException("Invitation missing during queue insertion."));
  try{saved.setEncryptedMailPayload(cipher.encrypt(saved.getTokenHash(),mapper.writeValueAsString(event)));saved.setNextDeliveryAttempt(Instant.now());invitations.saveAndFlush(saved);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invitation queue serialization failed.");}
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void deliverNext(){
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){
   // Same home-lock order as resend/revoke/accept; SMTP has bounded timeouts.
   lock.acquire();Instant now=Instant.now();invitations.clearExpiredMail(now);var candidates=invitations.findDue(now,PageRequest.of(0,1));if(candidates.isEmpty())return;var saved=candidates.get(0);
   if(!eligible(saved)){saved.setDeliveryStatus("MAIL_CANCELLED");saved.setDeliveryError("ACCESS_CHANGED");clear(saved);return;}
   PlatformInvitationService.MailRequested event;
   try{event=mapper.readValue(cipher.decrypt(saved.getTokenHash(),saved.getEncryptedMailPayload()),PlatformInvitationService.MailRequested.class);if(!saved.getTokenHash().equals(CredentialStamp.of(event.token()))||!saved.getUserId().equals(event.userId())||!saved.getEmail().equals(event.email()))throw new IllegalStateException();}
   catch(Exception failure){saved.setDeliveryStatus("MAIL_FAILED");saved.setDeliveryError("PAYLOAD_UNREADABLE");clear(saved);return;}
   saved.setDeliveryAttempts(saved.getDeliveryAttempts()+1);delivery.deliver(event);
   if("SENT".equals(saved.getDeliveryStatus())){saved.setDeliveryError(null);clear(saved);return;}
   saved.setDeliveryError("SMTP_FAILED");
   if(saved.getDeliveryAttempts()>=5){saved.setDeliveryStatus("MAIL_FAILED");clear(saved);return;}
   saved.setDeliveryStatus("MAIL_RETRY");saved.setNextDeliveryAttempt(now.plusSeconds(new long[]{30,120,600,1800}[saved.getDeliveryAttempts()-1]));invitations.saveAndFlush(saved);
  }
 }
 private boolean eligible(PlatformInvitation saved){
  var user=users.findForPasswordReset(saved.getUserId()).orElse(null);
  if(user==null||!user.isInvitationPending()||user.isActive()||!saved.getEmail().equals(user.getEmail())||!saved.getCredentialStamp().equals(CredentialStamp.of(user.getPasswordHash()))||!users.hasLivePlatformAccess(saved.getIssuerId())||!tenants.findById(saved.getTenantId()).map(Tenant::isActive).orElse(false))return false;
  try(var scope=TenantContext.open(saved.getTenantId())){return roles.findById(saved.getRoleId()).filter(r->Objects.equals(r.getTenantId(),saved.getTenantId())&&PlatformInvitationService.roleStamp(r).equals(saved.getRoleStamp())).isPresent();}
 }
 private void clear(PlatformInvitation saved){saved.setEncryptedMailPayload(null);saved.setNextDeliveryAttempt(null);invitations.saveAndFlush(saved);}
}
