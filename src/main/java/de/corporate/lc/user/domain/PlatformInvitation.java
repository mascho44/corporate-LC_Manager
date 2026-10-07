package de.corporate.lc.user.domain;
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
 protected PlatformInvitation(){}
 public PlatformInvitation(String tokenHash,UUID userId,UUID issuerId,UUID tenantId,UUID roleId,String roleStamp,String credentialStamp,String email,Instant expiresAt){this.tokenHash=tokenHash;this.userId=userId;this.issuerId=issuerId;this.tenantId=tenantId;this.roleId=roleId;this.roleStamp=roleStamp;this.credentialStamp=credentialStamp;this.email=email;this.expiresAt=expiresAt;}
 public String getTokenHash(){return tokenHash;}public UUID getUserId(){return userId;}public UUID getIssuerId(){return issuerId;}public UUID getTenantId(){return tenantId;}public UUID getRoleId(){return roleId;}public String getRoleStamp(){return roleStamp;}public String getCredentialStamp(){return credentialStamp;}public String getEmail(){return email;}public Instant getExpiresAt(){return expiresAt;}public String getDeliveryStatus(){return deliveryStatus;}public void setDeliveryStatus(String value){deliveryStatus=value;}
 @Override public String toString(){return "PlatformInvitation[redacted]";}
}
