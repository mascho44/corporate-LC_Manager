package de.ostms.lc.ai;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-256-GCM for the AI provider keys of the tenants; the row id is authenticated so a blob cannot be moved to another tenant's row. Never log plaintext or ciphertext. */
@Component
public class AiSecretCipher {
 private final String secret;private final SecureRandom random=new SecureRandom();
 /** lc.ai.encryption-key, falling back to the EBICS key (separate key derivation label). */
 public AiSecretCipher(@Value("${lc.ai.encryption-key:}") String secret){this.secret=secret;}
 public boolean configured(){return secret!=null&&secret.length()>=32;}
 private SecretKeySpec key()throws GeneralSecurityException{
  if(!configured())throw new IllegalStateException("API-Schlüssel können erst gespeichert werden, wenn LC_AI_ENCRYPTION_KEY (oder EBICS_ENCRYPTION_KEY, mindestens 32 Zeichen) gesetzt ist.");
  return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("lcm-ai-v1:"+secret).getBytes(StandardCharsets.UTF_8)),"AES");
 }
 public String encrypt(String row,String plain){
  try{
   byte[] nonce=new byte[12];random.nextBytes(nonce);
   var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));c.updateAAD(row.getBytes(StandardCharsets.UTF_8));
   byte[] sealed=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[nonce.length+sealed.length];
   System.arraycopy(nonce,0,out,0,nonce.length);System.arraycopy(sealed,0,out,nonce.length,sealed.length);return Base64.getEncoder().encodeToString(out);
  }catch(GeneralSecurityException e){throw new IllegalStateException("API-Schlüssel konnte nicht verschlüsselt werden.");}
 }
 public String decrypt(String row,String sealedBase64){
  try{
   byte[] sealed=Base64.getDecoder().decode(sealedBase64);if(sealed.length<28)throw new GeneralSecurityException();
   var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(sealed,0,12)));c.updateAAD(row.getBytes(StandardCharsets.UTF_8));
   return new String(c.doFinal(sealed,12,sealed.length-12),StandardCharsets.UTF_8);
  }catch(GeneralSecurityException|IllegalArgumentException e){throw new IllegalStateException("API-Schlüssel konnte nicht entschlüsselt werden.");}
 }
}
