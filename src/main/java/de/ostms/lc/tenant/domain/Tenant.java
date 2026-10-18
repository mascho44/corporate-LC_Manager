package de.ostms.lc.tenant.domain;
import jakarta.persistence.*;
import java.util.UUID;
/** Workspace metadata; access requires a verified membership. */
@Entity @Table(name="tenant")
public class Tenant {
 public static final UUID DEFAULT_ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
 /** The configured home tenant (global accounts, platform sign-in); the legacy default until the setting says otherwise. */
 public static UUID homeId(){return HomeTenant.id();}
 @Id private UUID id=DEFAULT_ID;
 @Column(nullable=false,unique=true,length=100) private String code="default";
 @Column(nullable=false,length=255) private String name="Default tenant";
 @Column(nullable=false,length=20) private String defaultLanguage="en";
 @Column(nullable=false) private boolean bankEnabled=true;
 @Column(nullable=false) private boolean corporateEnabled=false;
 @Column(nullable=false) private boolean active=true;
 @Column(nullable=false,length=16) private String ruleSourceMode="BOTH";
 @Column(precision=3,scale=2) private java.math.BigDecimal ocrConfidenceThreshold;
 @Column private java.time.Instant archivedAt;
 @Column private java.time.Instant deletedAt;
 public String getRuleSourceMode(){return ruleSourceMode;}
 public void setRuleSourceMode(String mode){if(!java.util.List.of("EMBEDDED","IMPORTED","BOTH").contains(mode))throw new IllegalArgumentException("Unknown rule source mode.");this.ruleSourceMode=mode;}
 public static final double OCR_MIN=0.50,OCR_MAX=0.95;
 public Double getOcrConfidenceThreshold(){return ocrConfidenceThreshold==null?null:ocrConfidenceThreshold.doubleValue();}
 /** null restores the installation default. */
 public void setOcrConfidenceThreshold(Double value){
  if(value==null){ocrConfidenceThreshold=null;return;}
  if(!Double.isFinite(value)||value<OCR_MIN||value>OCR_MAX)throw new IllegalArgumentException("OCR-Toleranz: Wert zwischen 50 % und 95 % angeben.");
  ocrConfidenceThreshold=java.math.BigDecimal.valueOf(Math.round(value*100)/100.0).setScale(2,java.math.RoundingMode.HALF_UP);
 }
 public boolean isDeleted(){return deletedAt!=null;}
 public void markDeleted(){if(active||!isArchived()||homeId().equals(id))throw new IllegalArgumentException("Only archived, non-default tenants can be deleted.");deletedAt=java.time.Instant.now();}
 public Tenant(){}
 public Tenant(String code,String name,String language,boolean bank,boolean corporate){this.id=UUID.randomUUID();this.code=code;this.name=name;this.defaultLanguage=language;this.bankEnabled=bank;this.corporateEnabled=corporate;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public String getDefaultLanguage(){return defaultLanguage;}
 public boolean isBankEnabled(){return bankEnabled;} public boolean isCorporateEnabled(){return corporateEnabled;}
 public void updatePresentation(String name,String language){this.name=name;this.defaultLanguage=language;}
 public boolean isActive(){return active;}
 public void setActive(boolean active){if(active&&isArchived())throw new IllegalArgumentException("Restore the archived tenant before activation.");this.active=active;}
 public boolean isArchived(){return archivedAt!=null;}
 public java.time.Instant getArchivedAt(){return archivedAt;}
 public void archive(){if(active)throw new IllegalArgumentException("Suspend the tenant before archiving.");if(archivedAt==null)archivedAt=java.time.Instant.now();}
 public void restoreArchive(){if(isDeleted())throw new IllegalArgumentException("Deleted tenant cannot be restored.");archivedAt=null;active=false;}
 public void updateProfile(boolean bank,boolean corporate){if(!bank&&!corporate)throw new IllegalArgumentException("Select at least one profile.");this.bankEnabled=bank;this.corporateEnabled=corporate;}
}
