package de.corporate.lc.tenant.domain;
import jakarta.persistence.*;
import java.util.UUID;
/** Bootstrap only: additional tenants are forbidden until access isolation is implemented. */
@Entity @Table(name="tenant")
public class Tenant {
 public static final UUID DEFAULT_ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
 @Id private UUID id=DEFAULT_ID;
 @Column(nullable=false,unique=true,length=100) private String code="default";
 @Column(nullable=false,length=255) private String name="Default tenant";
 @Column(nullable=false,length=20) private String defaultLanguage="en";
 @Column(nullable=false) private boolean bankEnabled=true;
 @Column(nullable=false) private boolean corporateEnabled=false;
 public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public String getDefaultLanguage(){return defaultLanguage;}
 public boolean isBankEnabled(){return bankEnabled;} public boolean isCorporateEnabled(){return corporateEnabled;}
}
