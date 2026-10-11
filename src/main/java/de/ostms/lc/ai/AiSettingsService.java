package de.ostms.lc.ai;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI suggestions run only if the installation provides a model (global switch) and the current tenant has switched them on. */
@Service
public class AiSettingsService {
 public record View(boolean available,boolean enabled,String provider,String model,String changedBy,java.time.LocalDateTime changedAt){}
 private final TenantAiSettingRepository repo;private final LlmClient client;
 @Autowired public AiSettingsService(TenantAiSettingRepository repo,@Autowired(required=false) LlmClient client){this.repo=repo;this.client=client;}
 public boolean available(){return client!=null;}
 /** True only if a model is configured and this tenant opted in. */
 @Transactional(readOnly=true) public boolean active(){return client!=null&&repo.current().map(TenantAiSetting::isEnabled).orElse(false);}
 @Transactional(readOnly=true) public View view(){
  var s=repo.current();
  return new View(client!=null,client!=null&&s.map(TenantAiSetting::isEnabled).orElse(false),client==null?null:client.provider(),client==null?null:client.model(),s.map(TenantAiSetting::getChangedBy).orElse(null),s.map(TenantAiSetting::getChangedAt).orElse(null));
 }
 /** Returns the previous state for the audit trail. */
 @Transactional public boolean change(boolean enabled,String user){
  if(client==null&&enabled)throw new IllegalStateException("In dieser Installation ist kein KI-Modell konfiguriert.");
  var s=repo.current().orElseGet(TenantAiSetting::new);boolean before=s.isEnabled();s.change(enabled,user);repo.save(s);return before;
 }
}
