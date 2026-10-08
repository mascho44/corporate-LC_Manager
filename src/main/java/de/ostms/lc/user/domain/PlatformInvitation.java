package de.ostms.lc.user.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="platform_invitation")
public class PlatformInvitation {
 @Id @Column(length=64) private String tokenHash;
 @Column(nullable=false,unique=true) private UUID userId;
 @Column(nullable=false) private UUID issuerId;
 @Column(nullable=false) private UUID tenantId;
 @Column(nullable=false) private UUID roleId;
 @Column(nullable=false,length=64) private String roleStamp;
 @Column(nullable=false,length=64) private String credentialStamp;
 @Column(nullable=false,length=255) private String email;
 @Column(nullable=false) private Instant expiresAt;
 @Column(nullable=false,length=30) private String deliveryStatus="PENDING_MAIL";
 @Column(columnDefinition="text") private String encryptedMailPayload;
 @Column(nullable=false) private int deliveryAttempts;
 private Instant nextDeliveryAttempt;
 @Column(length=40) private String deliveryError;
 public String getDeliveryError(){return deliveryError;}public void setDeliveryError(String value){deliveryError=value;}
 public String getEncryptedMailPayload(){return encryptedMailPayload;}public void setEncryptedMailPayload(String value){encryptedMailPayload=value;}
 public int getDeliveryAttempts(){return deliveryAttempts;}public void setDeliveryAttempts(int value){deliveryAttempts=value;}
 public Instant getNextDeliveryAttempt(){return nextDeliveryAttempt;}public void setNextDeliveryAttempt(Instant value){nextDeliveryAttempt=value;}
 protected PlatformInvitation(){}
 public PlatformInvitation(String tokenHash,UUID userId,UUID issuerId,UUID tenantId,UUID roleId,String roleStamp,String credentialStamp,String email,Instant expiresAt){this.tokenHash=tokenHash;this.userId=userId;this.issuerId=issuerId;this.tenantId=tenantId;this.roleId=roleId;this.roleStamp=roleStamp;this.credentialStamp=credentialStamp;this.email=email;this.expiresAt=expiresAt;}
 public String getTokenHash(){return tokenHash;}public UUID getUserId(){return userId;}public UUID getIssuerId(){return issuerId;}public UUID getTenantId(){return tenantId;}public UUID getRoleId(){return roleId;}public String getRoleStamp(){return roleStamp;}public String getCredentialStamp(){return credentialStamp;}public String getEmail(){return email;}public Instant getExpiresAt(){return expiresAt;}public String getDeliveryStatus(){return deliveryStatus;}public void setDeliveryStatus(String value){deliveryStatus=value;}
 @Override public String toString(){return "PlatformInvitation[redacted]";}
}
