package de.ostms.lc.ai;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.Optional;

/**
 * Chooses the model for the current tenant: the tenant's own configuration if it has one, otherwise the operator default (lc.ai.*), otherwise none.
 * Tenant endpoints are validated against the operator's host list on every use, so a later change of the list takes effect immediately.
 */
@Service
public class LlmClientFactory implements LlmClientProvider {
 private static final Logger LOG=LoggerFactory.getLogger(LlmClientFactory.class);
 public record Resolved(LlmClient client,String source){}
 private final TenantAiSettingRepository repo;private final AiSecretCipher cipher;private final AiProviderPolicy policy;
 private final boolean globalEnabled;private final String globalProvider,globalUrl,globalModel,globalKey,globalKeyFile;private final int globalTimeout;
 private volatile LlmClient global;
 public LlmClientFactory(TenantAiSettingRepository repo,AiSecretCipher cipher,AiProviderPolicy policy,
   @Value("${lc.ai.enabled:false}") boolean globalEnabled,@Value("${lc.ai.provider:ollama}") String globalProvider,@Value("${lc.ai.base-url:http://localhost:11434}") String globalUrl,
   @Value("${lc.ai.model:qwen2.5:3b}") String globalModel,@Value("${lc.ai.api-key:}") String globalKey,@Value("${lc.ai.api-key-file:}") String globalKeyFile,@Value("${lc.ai.timeout-seconds:240}") int globalTimeout){
  this.repo=repo;this.cipher=cipher;this.policy=policy;this.globalEnabled=globalEnabled;this.globalProvider=globalProvider;this.globalUrl=globalUrl;this.globalModel=globalModel;this.globalKey=globalKey;this.globalKeyFile=globalKeyFile;this.globalTimeout=globalTimeout;
 }
 /** Whether the operator default exists (configured and switched on). */
 public boolean operatorDefault(){return globalEnabled;}

 /** For the connection test: throws with a message the tenant administrator can act on. */
 @Transactional(readOnly=true) public Optional<Resolved> resolveOrThrow(){
  var s=repo.current();
  if(s.isPresent()&&s.get().configured())return Optional.of(new Resolved(forTenant(s.get()),"mandant"));
  var g=operatorClient();
  return g==null?Optional.empty():Optional.of(new Resolved(g,"betreiber"));
 }
 @Override @Transactional(readOnly=true) public Optional<LlmClient> current(){
  try{return resolveOrThrow().map(Resolved::client);}
  catch(RuntimeException e){LOG.warn("KI-Konfiguration nicht nutzbar: {}",e.getMessage());return Optional.empty();}
 }
 private LlmClient forTenant(TenantAiSetting s){
  var preset=AiProviderPolicy.preset(s.getProvider());
  if(preset==null)throw new IllegalStateException("Unbekannter KI-Anbieter.");
  String url=policy.validate(s.getProvider(),s.getBaseUrl());
  int timeout=s.getTimeoutSeconds()==null?120:Math.max(10,Math.min(600,s.getTimeoutSeconds()));
  if(preset.local())return new OllamaClient(url,s.getModel(),timeout);
  if(!s.hasKey())throw new IllegalStateException("Für diesen Anbieter fehlt der API-Schlüssel.");
  try{return new OpenAiCompatibleClient(url,s.getModel(),cipher.decrypt(s.getId().toString(),s.getApiKeyCipher()),"",timeout);}
  catch(IOException e){throw new IllegalStateException("KI-Client konnte nicht aufgebaut werden.");}
 }
 private LlmClient operatorClient(){
  if(!globalEnabled)return null;
  var c=global;if(c!=null)return c;
  synchronized(this){
   if(global==null){
    try{global="openai-compatible".equals(globalProvider)?new OpenAiCompatibleClient(globalUrl,globalModel,globalKey,globalKeyFile,globalTimeout):new OllamaClient(globalUrl,globalModel,globalTimeout);}
    catch(IOException e){throw new IllegalStateException("Betreiber-KI konnte nicht aufgebaut werden.");}
   }
   return global;
  }
 }
}
