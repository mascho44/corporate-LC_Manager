package de.ostms.lc.ai;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSettingsServiceTest {
 private static LlmClient model(){var c=mock(LlmClient.class);when(c.provider()).thenReturn("test");when(c.model()).thenReturn("m");return c;}
 @Test void activeNeedsBothTheInstallationModelAndTheTenantOptIn(){
  var repo=mock(TenantAiSettingRepository.class);
  var s=new AiSettingsService(repo,model());
  when(repo.current()).thenReturn(Optional.empty());assertThat(s.active()).isFalse();
  var on=new TenantAiSetting();on.change(true,"anna");when(repo.current()).thenReturn(Optional.of(on));assertThat(s.active()).isTrue();
  assertThat(new AiSettingsService(repo,null).active()).isFalse();
 }
 @Test void switchingOnWithoutAConfiguredModelIsRefusedAndTheTenantDefaultIsOff(){
  var repo=mock(TenantAiSettingRepository.class);when(repo.current()).thenReturn(Optional.empty());
  assertThatThrownBy(()->new AiSettingsService(repo,null).change(true,"anna")).isInstanceOf(IllegalStateException.class);
  assertThat(new AiSettingsService(repo,model()).view().enabled()).isFalse();
 }
 @Test void changeReportsThePreviousStateForTheAudit(){
  var repo=mock(TenantAiSettingRepository.class);var row=new TenantAiSetting();when(repo.current()).thenReturn(Optional.of(row));
  var s=new AiSettingsService(repo,model());
  assertThat(s.change(true,"anna")).isFalse();assertThat(s.change(false,"ben")).isTrue();verify(repo,times(2)).save(row);
 }
}
