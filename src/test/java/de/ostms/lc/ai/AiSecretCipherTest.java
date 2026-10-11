package de.ostms.lc.ai;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiSecretCipherTest {
 private final AiSecretCipher cipher=new AiSecretCipher("0123456789abcdef0123456789abcdef");
 @Test void roundTripAndTheRowIdIsAuthenticated(){
  String sealed=cipher.encrypt("row-1","sk-secret");
  assertThat(sealed).doesNotContain("sk-secret");assertThat(cipher.decrypt("row-1",sealed)).isEqualTo("sk-secret");
  assertThatThrownBy(()->cipher.decrypt("row-2",sealed)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("sk-secret");
  assertThat(cipher.encrypt("row-1","sk-secret")).isNotEqualTo(sealed); // fresh nonce every time
 }
 @Test void aShortOrMissingKeyRefusesToStoreAnything(){
  assertThat(new AiSecretCipher("").configured()).isFalse();
  assertThatThrownBy(()->new AiSecretCipher("short").encrypt("r","x")).isInstanceOf(IllegalStateException.class).hasMessageContaining("LC_AI_ENCRYPTION_KEY");
  assertThatThrownBy(()->cipher.decrypt("row","not-base64!!")).isInstanceOf(IllegalStateException.class);
 }
}
