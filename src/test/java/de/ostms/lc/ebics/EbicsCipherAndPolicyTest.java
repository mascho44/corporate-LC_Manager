package de.ostms.lc.ebics;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EbicsCipherAndPolicyTest {
 private static final String KEY="synthetic-test-key-0123456789abcdef0123";
 @Test void roundTripBindsTheRow(){
  var cipher=new EbicsCipher(KEY);byte[] plain="synthetic key material".getBytes();
  byte[] sealed=cipher.encrypt("row-1",plain);
  assertThat(sealed).isNotEqualTo(plain);
  assertThat(cipher.decrypt("row-1",sealed)).isEqualTo(plain);
  assertThatThrownBy(()->cipher.decrypt("row-2",sealed)).isInstanceOf(IllegalStateException.class);
  sealed[sealed.length-1]^=1;
  assertThatThrownBy(()->cipher.decrypt("row-1",sealed)).isInstanceOf(IllegalStateException.class);
 }
 @Test void refusesToWorkWithoutAKey(){
  assertThat(new EbicsCipher("").configured()).isFalse();
  assertThatThrownBy(()->new EbicsCipher("short").encrypt("r",new byte[]{1})).isInstanceOf(IllegalStateException.class);
 }
 @Test void urlPolicyRejectsUnsafeTargets(){
  var policy=new EbicsUrlPolicy("");
  for(String bad:new String[]{"http://example.org/ebicsweb","https://user:pw@example.org/","https://localhost/ebicsweb","https://127.0.0.1/ebicsweb","https://[::1]/x","https://169.254.169.254/latest","https://10.0.0.5/x","ftp://example.org/","","not a url"})
   assertThatThrownBy(()->policy.check(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->policy.check(null)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void allowListPermitsInternalHost(){
  assertThat(new EbicsUrlPolicy("localhost, other").check("https://localhost:8443/ebicsweb").getHost()).isEqualTo("localhost");
 }
}
