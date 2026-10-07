package de.corporate.lc.tenant.domain;
import jakarta.persistence.*;
import java.util.UUID;
/** Workspace metadata; access requires a verified membership. */
@Entity @Table(name="tenant")
public class Tenant {
 public static final UUID DEFAULT_ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
 @Id private UUID id=DEFAULT_ID;
 @Column(nullable=false,unique=true,length=100) private String code="default";
 @Column(nullable=false,length=255) private String name="Default tenant";
 @Column(nullable=false,length=20) private String defaultLanguage="en";
 @Column(nullable=false) private boolean bankEnabled=true;
 @Column(nullable=false) private boolean corporateEnabled=false;
 @Column(nullable=false) private boolean active=true;
 public Tenant(){}
 public Tenant(String code,String name,String language,boolean bank,boolean corporate){this.id=UUID.randomUUID();this.code=code;this.name=name;this.defaultLanguage=language;this.bankEnabled=bank;this.corporateEnabled=corporate;}
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public String getDefaultLanguage(){return defaultLanguage;}
 public boolean isBankEnabled(){return bankEnabled;} public boolean isCorporateEnabled(){return corporateEnabled;}
 public void updatePresentation(String name,String language){this.name=name;this.defaultLanguage=language;}
 public boolean isActive(){return active;}
 public void setActive(boolean active){this.active=active;}
 public void updateProfile(boolean bank,boolean corporate){if(!bank&&!corporate)throw new IllegalArgumentException("Select at least one profile.");this.bankEnabled=bank;this.corporateEnabled=corporate;}
}
