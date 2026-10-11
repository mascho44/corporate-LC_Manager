package de.ostms.lc.ai;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiSettingsServiceTest {
 private final TenantAiSettingRepository repo=mock(TenantAiSettingRepository.class);private final LlmClientFactory factory=mock(LlmClientFactory.class);
 private final AiSecretCipher cipher=new AiSecretCipher("0123456789abcdef0123456789abcdef");
 private final AiProviderPolicy policy=new AiProviderPolicy("api.mistral.ai",false);
 private final AiSettingsService service=new AiSettingsService(repo,factory,cipher,policy);
 private static LlmClient model(){var c=mock(LlmClient.class);when(c.provider()).thenReturn("openai-compatible");when(c.model()).thenReturn("m");return c;}

 @Test void activeNeedsTheTenantSwitchAndAUsableModel(){
  var m=model();when(repo.current()).thenReturn(Optional.empty());when(factory.current()).thenReturn(Optional.of(m));assertThat(service.active()).isFalse();
  var on=new TenantAiSetting();on.change(true,"anna");when(repo.current()).thenReturn(Optional.of(on));assertThat(service.active()).isTrue();
  when(factory.current()).thenReturn(Optional.empty());assertThat(service.active()).isFalse();
 }
 @Test void switchingOnWithoutAUsableModelIsRefused(){
  when(repo.current()).thenReturn(Optional.empty());when(factory.current()).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.change(true,"anna")).isInstanceOf(IllegalStateException.class).hasMessageContaining("konfigurieren");
 }
 @Test void aKeyIsStoredEncryptedAndNeverShownAndTheAuditSummaryHasNoSecret(){
  var row=new TenantAiSetting();when(repo.current()).thenReturn(Optional.of(row));
  String summary=service.configure(new AiSettingsService.Config("mistral","","mistral-large-latest",90,"sk-very-secret-key"),"anna");
  assertThat(row.getApiKeyCipher()).isNotBlank().doesNotContain("sk-very-secret-key");
  assertThat(cipher.decrypt(row.getId().toString(),row.getApiKeyCipher())).isEqualTo("sk-very-secret-key");
  assertThat(row.getBaseUrl()).isEqualTo("https://api.mistral.ai/v1");assertThat(row.getModel()).isEqualTo("mistral-large-latest");
  assertThat(summary).doesNotContain("sk-very-secret-key").contains("neu gesetzt");
  var view=service.view();assertThat(view.keySet()).isTrue();assertThat(view.toString()).doesNotContain("sk-very-secret-key");
 }
 @Test void anEmptyKeyKeepsTheStoredOneButAChangedEndpointNeverKeepsIt(){
  var row=new TenantAiSetting();when(repo.current()).thenReturn(Optional.of(row));
  service.configure(new AiSettingsService.Config("mistral","","m1",null,"k1"),"anna");String stored=row.getApiKeyCipher();
  service.configure(new AiSettingsService.Config("mistral","","m2",null,""),"anna");
  assertThat(row.getApiKeyCipher()).isEqualTo(stored);assertThat(row.getModel()).isEqualTo("m2");
  var other=new AiProviderPolicy("api.mistral.ai,api.openai.com",false);var s2=new AiSettingsService(repo,factory,cipher,other);
  assertThatThrownBy(()->s2.configure(new AiSettingsService.Config("openai","","gpt-x",null,""),"anna")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("neu eingeben");
  assertThat(row.getApiKeyCipher()).isEqualTo(stored);
 }
 @Test void invalidInputIsRefusedBeforeAnythingIsSaved(){
  var row=new TenantAiSetting();when(repo.current()).thenReturn(Optional.of(row));
  assertThatThrownBy(()->service.configure(new AiSettingsService.Config("mistral","","  ",null,"k"),"a")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.configure(new AiSettingsService.Config("mistral","","m",5,"k"),"a")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.configure(new AiSettingsService.Config("mistral","","m",null,""),"a")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Schlüssel");
  assertThatThrownBy(()->service.configure(new AiSettingsService.Config("unknown","","m",null,"k"),"a")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.configure(new AiSettingsService.Config("custom","https://evil.example/v1","m",null,"k"),"a")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht freigegeben");
  verify(repo,never()).save(any());assertThat(row.configured()).isFalse();
 }
 @Test void removingTheKeySwitchesTheTenantOff(){
  var row=new TenantAiSetting();when(repo.current()).thenReturn(Optional.of(row));
  service.configure(new AiSettingsService.Config("mistral","","m",null,"k"),"anna");row.change(true,"anna");
  service.clearKey("anna");assertThat(row.hasKey()).isFalse();assertThat(row.isEnabled()).isFalse();
 }
}
