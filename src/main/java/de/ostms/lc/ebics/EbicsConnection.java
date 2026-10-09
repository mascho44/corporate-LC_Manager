package de.ostms.lc.ebics;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="ebics_connection")
public class EbicsConnection extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=500) private String url;
 @Column(name="host_id",nullable=false,length=35) private String hostId;
 @Column(name="partner_id",nullable=false,length=35) private String partnerId;
 @Column(name="user_id",nullable=false,length=35) private String userId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EbicsStatus status=EbicsStatus.NEW;
 @Column(name="bank_blob",length=1000000) private byte[] bankBlob;
 @Column(name="partner_blob",length=1000000) private byte[] partnerBlob;
 @Column(name="user_blob",length=1000000) private byte[] userBlob;
 @Column(name="last_error",length=500) private String lastError;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt=LocalDateTime.now();
 @PreUpdate private void touch(){updatedAt=LocalDateTime.now();}
 public UUID getId(){return id;}
 public String getUrl(){return url;} public void setUrl(String v){url=v;}
 public String getHostId(){return hostId;} public void setHostId(String v){hostId=v;}
 public String getPartnerId(){return partnerId;} public void setPartnerId(String v){partnerId=v;}
 public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
 public EbicsStatus getStatus(){return status;} public void setStatus(EbicsStatus v){status=v;}
 public byte[] getBankBlob(){return bankBlob;} public void setBankBlob(byte[] v){bankBlob=v;}
 public byte[] getPartnerBlob(){return partnerBlob;} public void setPartnerBlob(byte[] v){partnerBlob=v;}
 public byte[] getUserBlob(){return userBlob;} public void setUserBlob(byte[] v){userBlob=v;}
 public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;}
 public LocalDateTime getUpdatedAt(){return updatedAt;}
 public void clearKeys(){bankBlob=null;partnerBlob=null;userBlob=null;}
}
