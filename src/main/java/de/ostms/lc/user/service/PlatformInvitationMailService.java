package de.ostms.lc.user.service;
import de.ostms.lc.tenant.domain.*;
import de.ostms.lc.user.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.net.URI;

/** Invoked by the durable queue in its transaction; SMTP failures remain retryable. */
@Service public class PlatformInvitationMailService {
 private final JavaMailSender mail;private final PlatformInvitationRepository invitations;private final AppUserRepository users;private final String from,baseUrl;
 public PlatformInvitationMailService(JavaMailSender mail,PlatformInvitationRepository invitations,AppUserRepository users,@Value("${app.mail.from:}") String from,@Value("${app.security.public-base-url:https://lc.example.com}") String baseUrl){this.mail=mail;this.invitations=invitations;this.users=users;this.from=from;URI uri=URI.create(baseUrl);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Public base URL must be an HTTPS address.");this.baseUrl=baseUrl.replaceAll("/+$","");}
 @Transactional(propagation=Propagation.REQUIRED) public void deliver(PlatformInvitationService.MailRequested event){
  try(var home=TenantContext.open(Tenant.DEFAULT_ID)){
   var user=users.findForPasswordReset(event.userId()).orElse(null);if(user==null||!user.isInvitationPending())return;
   var saved=invitations.findById(CredentialStamp.of(event.token())).orElse(null);if(saved==null)return;
   var message=new SimpleMailMessage();message.setFrom(from);message.setTo(event.email());message.setSubject("Corporate LC Manager – account invitation");
   message.setText("You have been invited to Corporate LC Manager.\nUsername: "+event.username()+"\nWorkspace: "+event.tenantName()+"\nTenant code: "+(Tenant.DEFAULT_ID.equals(saved.getTenantId())?"leave empty for the default workspace":event.tenantCode())+"\n\nChoose your own password using this single-use link (valid for 24 hours):\n"+baseUrl+"/invitation.html#token="+event.token()+"\n\nNo password is sent by email. If you did not expect this invitation, contact your administrator.");
   try{mail.send(message);saved.setDeliveryStatus("SENT");}catch(RuntimeException failure){saved.setDeliveryStatus("MAIL_FAILED");}invitations.saveAndFlush(saved);
  }
 }
}
