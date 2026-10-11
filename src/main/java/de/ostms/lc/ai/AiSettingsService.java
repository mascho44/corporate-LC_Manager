package de.ostms.lc.ai;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** AI settings of the current tenant: the provider configuration (own key, encrypted) and the on/off switch. */
@Service
public class AiSettingsService {
 /** apiKey: null or blank keeps the stored key. */
 public record Config(String provider,String baseUrl,String model,Integer timeoutSeconds,String apiKey){}
 public record View(boolean available,boolean enabled,String source,String provider,String baseUrl,String model,boolean keySet,Integer timeoutSeconds,
   List<String> allowedHosts,boolean allowLocal,List<AiProviderPolicy.Preset> presets,boolean operatorDefault,boolean encryptionConfigured,String changedBy,LocalDateTime changedAt){}
 private final TenantAiSettingRepository repo;private final LlmClientFactory factory;private final AiSecretCipher cipher;private final AiProviderPolicy policy;
 public AiSettingsService(TenantAiSettingRepository repo,LlmClientFactory factory,AiSecretCipher cipher,AiProviderPolicy policy){this.repo=repo;this.factory=factory;this.cipher=cipher;this.policy=policy;}

 /** Some way to get a model exists in this installation (hosts released for own configuration, or an operator default). */
 public boolean available(){return !policy.allowedHosts().isEmpty()||factory.operatorDefault();}
 /** True only if a model can be reached for this tenant and the tenant switched AI on. */
 @Transactional(readOnly=true) public boolean active(){return repo.current().map(TenantAiSetting::isEnabled).orElse(false)&&factory.current().isPresent();}
 @Transactional(readOnly=true) public View view(){
  var s=repo.current();var own=s.filter(TenantAiSetting::configured);
  String source=own.isPresent()?"mandant":factory.operatorDefault()?"betreiber":null;
  var client=factory.current().orElse(null);
  return new View(available(),s.map(TenantAiSetting::isEnabled).orElse(false)&&client!=null,source,
   own.map(TenantAiSetting::getProvider).orElse(client==null?null:client.provider()),own.map(TenantAiSetting::getBaseUrl).orElse(null),own.map(TenantAiSetting::getModel).orElse(client==null?null:client.model()),
   own.map(TenantAiSetting::hasKey).orElse(false),own.map(TenantAiSetting::getTimeoutSeconds).orElse(null),List.copyOf(policy.allowedHosts()),policy.allowLocal(),AiProviderPolicy.PRESETS,factory.operatorDefault(),cipher.configured(),
   s.map(TenantAiSetting::getChangedBy).orElse(null),s.map(TenantAiSetting::getChangedAt).orElse(null));
 }
 /** Returns the previous state for the audit trail. */
 @Transactional public boolean change(boolean enabled,String user){
  if(enabled&&factory.current().isEmpty())throw new IllegalStateException("Bitte zuerst einen KI-Dienst konfigurieren (Anbieter, Modell, Schlüssel).");
  var s=repo.current().orElseGet(TenantAiSetting::new);boolean before=s.isEnabled();s.change(enabled,user);repo.save(s);return before;
 }
 /** Validates and stores the tenant's provider configuration; returns a summary without secrets for the audit trail. */
 @Transactional public String configure(Config c,String user){
  if(c==null)throw new IllegalArgumentException("Konfiguration fehlt.");
  var preset=AiProviderPolicy.preset(c.provider());if(preset==null)throw new IllegalArgumentException("Unbekannter KI-Anbieter.");
  String url=policy.validate(c.provider(),c.baseUrl());
  String model=c.model()==null?"":c.model().trim();
  if(model.isEmpty()||model.length()>100||!model.matches("[A-Za-z0-9._:/@-]+"))throw new IllegalArgumentException("Bitte einen gültigen Modellnamen angeben.");
  int timeout=c.timeoutSeconds()==null?120:c.timeoutSeconds();if(timeout<10||timeout>600)throw new IllegalArgumentException("Das Zeitlimit muss zwischen 10 und 600 Sekunden liegen.");
  var s=repo.current().orElseGet(TenantAiSetting::new);
  boolean endpointChanged=!c.provider().equals(s.getProvider())||!host(url).equals(host(s.getBaseUrl()));
  boolean keyGiven=c.apiKey()!=null&&!c.apiKey().isBlank();
  if(!preset.local()){
   if(!keyGiven&&(!s.hasKey()||endpointChanged))throw new IllegalArgumentException(s.hasKey()?"Bei geändertem Anbieter oder geänderter Adresse bitte den API-Schlüssel neu eingeben.":"Bitte den API-Schlüssel angeben.");
  }
  if(keyGiven){if(c.apiKey().length()>500)throw new IllegalArgumentException("Der API-Schlüssel ist zu lang.");repo.save(s);s.setApiKeyCipher(cipher.encrypt(s.getId().toString(),c.apiKey().trim()),user);}
  else if(endpointChanged)s.setApiKeyCipher(null,user); // a key never travels to a different endpoint
  s.configure(c.provider(),url,model,timeout,user);repo.save(s);
  return "Anbieter "+c.provider()+", Adresse "+url+", Modell "+model+", Zeitlimit "+timeout+" s, Schlüssel "+(keyGiven?"neu gesetzt":s.hasKey()?"unverändert":"keiner");
 }
 /** Removes the stored key and switches AI off for the tenant. */
 @Transactional public void clearKey(String user){
  var s=repo.current().orElse(null);if(s==null)return;
  s.setApiKeyCipher(null,user);s.change(false,user);repo.save(s);
 }
 private static String host(String url){try{String h=url==null?null:URI.create(url).getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(RuntimeException e){return "";}}
}
