package de.ostms.lc.ai;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.assertj.core.api.Assertions.*;

class AiProviderPolicyTest {
 private final AiProviderPolicy policy=new AiProviderPolicy("api.mistral.ai, localhost",false);

 @Test void onlyReleasedHostsOverHttpsAreAccepted(){
  assertThat(policy.validate("mistral","")).isEqualTo("https://api.mistral.ai/v1");
  assertThat(policy.validate("custom","https://api.mistral.ai/v1/")).isEqualTo("https://api.mistral.ai/v1");
  assertThatThrownBy(()->policy.validate("custom","https://evil.example/v1")).hasMessageContaining("nicht freigegeben");
  assertThatThrownBy(()->policy.validate("custom","http://api.mistral.ai/v1")).hasMessageContaining("HTTPS");
  assertThatThrownBy(()->policy.validate("custom","https://api.mistral.ai:8443/v1")).hasMessageContaining("443");
  assertThatThrownBy(()->policy.validate("custom","https://user:pw@api.mistral.ai/v1")).hasMessageContaining("ungültig");
  assertThatThrownBy(()->policy.validate("custom","https://api.mistral.ai/v1?x=1")).hasMessageContaining("ungültig");
  assertThatThrownBy(()->policy.validate("custom","not a url")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->policy.validate("nope","https://api.mistral.ai/v1")).hasMessageContaining("Unbekannt");
 }
 @Test void withoutAReleasedHostNothingIsPossible(){
  assertThatThrownBy(()->new AiProviderPolicy("",false).validate("mistral","")).hasMessageContaining("LC_AI_ALLOWED_HOSTS");
 }
 @Test void localEndpointsNeedTheOperatorSwitchAndTheHostList(){
  assertThatThrownBy(()->policy.validate("ollama","")).hasMessageContaining("nicht erlaubt");
  var local=new AiProviderPolicy("localhost",true);assertThat(local.validate("ollama","")).isEqualTo("http://localhost:11434");
  assertThatThrownBy(()->new AiProviderPolicy("",true).validate("ollama","")).hasMessageContaining("nicht freigegeben");
 }
 @Test void internalAddressesAreRecognised()throws Exception{
  for(String ip:new String[]{"127.0.0.1","10.1.2.3","172.16.0.5","192.168.1.1","169.254.169.254","100.64.0.1","0.0.0.0","::1","fd00::1","fe80::1"})
   assertThat(AiProviderPolicy.internal(InetAddress.getByName(ip))).as(ip).isTrue();
  for(String ip:new String[]{"8.8.8.8","93.184.216.34","2606:4700:4700::1111"})assertThat(AiProviderPolicy.internal(InetAddress.getByName(ip))).as(ip).isFalse();
  assertThatThrownBy(()->policy.checkNotInternal("localhost")).hasMessageContaining("interne Adresse");
 }
}
