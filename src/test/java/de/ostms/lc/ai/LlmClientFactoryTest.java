package de.ostms.lc.ai;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmClientFactoryTest {
 private final TenantAiSettingRepository repo=mock(TenantAiSettingRepository.class);
 private final AiSecretCipher cipher=new AiSecretCipher("0123456789abcdef0123456789abcdef");
 private final AiProviderPolicy policy=new AiProviderPolicy("api.mistral.ai",false);
 private LlmClientFactory factory(boolean operatorEnabled){return new LlmClientFactory(repo,cipher,policy,operatorEnabled,"openai-compatible","https://api.mistral.ai/v1","operator-model","operator-key","",60);}

 private TenantAiSetting tenantRow(){
  var row=new TenantAiSetting();row.configure("mistral","https://api.mistral.ai/v1","tenant-model",90,"anna");row.setApiKeyCipher(cipher.encrypt(row.getId().toString(),"tenant-key"),"anna");return row;
 }
 @Test void theTenantConfigurationWinsOverTheOperatorDefault()throws Exception{
  when(repo.current()).thenReturn(Optional.of(tenantRow()));
  var r=factory(true).resolveOrThrow().orElseThrow();
  assertThat(r.source()).isEqualTo("mandant");assertThat(r.client().model()).isEqualTo("tenant-model");assertThat(r.client().provider()).isEqualTo("openai-compatible");
 }
 @Test void withoutATenantConfigurationTheOperatorDefaultIsUsedIfThereIsOne()throws Exception{
  when(repo.current()).thenReturn(Optional.empty());
  assertThat(factory(true).resolveOrThrow().orElseThrow().source()).isEqualTo("betreiber");
  assertThat(factory(false).current()).isEmpty();
 }
 @Test void aNoLongerAllowedHostMakesTheTenantConfigurationUnusableImmediately(){
  when(repo.current()).thenReturn(Optional.of(tenantRow()));
  var stricter=new LlmClientFactory(repo,cipher,new AiProviderPolicy("api.openai.com",false),false,"ollama","","","","",60);
  assertThat(stricter.current()).isEmpty();
  assertThatThrownBy(stricter::resolveOrThrow).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht freigegeben");
 }
 @Test void aMissingKeyOrUnreadableCipherIsReportedWithoutLeakingAnything(){
  var noKey=new TenantAiSetting();noKey.configure("mistral","https://api.mistral.ai/v1","m",null,"a");when(repo.current()).thenReturn(Optional.of(noKey));
  assertThatThrownBy(()->factory(false).resolveOrThrow()).isInstanceOf(IllegalStateException.class).hasMessageContaining("Schlüssel");
  var broken=tenantRow();broken.setApiKeyCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","a");when(repo.current()).thenReturn(Optional.of(broken));
  assertThatThrownBy(()->factory(false).resolveOrThrow()).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("tenant-key");
  assertThat(factory(false).current()).isEmpty();
 }
}
