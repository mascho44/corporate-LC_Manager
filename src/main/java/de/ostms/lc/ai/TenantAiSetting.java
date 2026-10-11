package de.ostms.lc.ai;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="tenant_ai_setting")
public class TenantAiSetting extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false) private boolean enabled;
 @Column(name="changed_by",length=100) private String changedBy;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt=LocalDateTime.now();
 @Column(length=30) private String provider;
 @Column(name="base_url",length=300) private String baseUrl;
 @Column(length=100) private String model;
 @Column(name="api_key_cipher",columnDefinition="text") private String apiKeyCipher;
 @Column(name="timeout_seconds") private Integer timeoutSeconds;
 public TenantAiSetting(){}
 public UUID getId(){return id;}
 public String getProvider(){return provider;} public String getBaseUrl(){return baseUrl;} public String getModel(){return model;} public Integer getTimeoutSeconds(){return timeoutSeconds;}
 public String getApiKeyCipher(){return apiKeyCipher;} public boolean hasKey(){return apiKeyCipher!=null&&!apiKeyCipher.isBlank();}
 public boolean configured(){return provider!=null&&model!=null;}
 public void configure(String provider,String baseUrl,String model,Integer timeoutSeconds,String user){this.provider=provider;this.baseUrl=baseUrl;this.model=model;this.timeoutSeconds=timeoutSeconds;this.changedBy=user;this.changedAt=LocalDateTime.now();}
 public void setApiKeyCipher(String cipher,String user){this.apiKeyCipher=cipher;this.changedBy=user;this.changedAt=LocalDateTime.now();}
 public boolean isEnabled(){return enabled;} public String getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;}
 public void change(boolean enabled,String user){this.enabled=enabled;this.changedBy=user;this.changedAt=LocalDateTime.now();}
}
