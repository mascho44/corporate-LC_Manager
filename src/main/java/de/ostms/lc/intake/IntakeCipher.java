package de.ostms.lc.intake;

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

/** AES-256-GCM for the mailbox password of an intake channel; the channel id is authenticated so a value cannot be moved to another channel. Never log plaintext or ciphertext. */
@Component
public class IntakeCipher {
 private final String secret;private final SecureRandom random=new SecureRandom();
 public IntakeCipher(@Value("${app.intake.encryption-key:}") String secret){this.secret=secret;}
 public boolean configured(){return secret!=null&&secret.length()>=32;}
 private SecretKeySpec key()throws GeneralSecurityException{
  if(!configured())throw new IllegalStateException("Das Postfach-Kennwort kann erst gespeichert werden, wenn INTAKE_ENCRYPTION_KEY (mindestens 32 Zeichen) gesetzt ist.");
  return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("lcm-intake-v1:"+secret).getBytes(StandardCharsets.UTF_8)),"AES");
 }
 public byte[] encrypt(String channelId,String plain){
  try{
   byte[] nonce=new byte[12];random.nextBytes(nonce);
   var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));c.updateAAD(channelId.getBytes(StandardCharsets.UTF_8));
   byte[] sealed=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[nonce.length+sealed.length];
   System.arraycopy(nonce,0,out,0,nonce.length);System.arraycopy(sealed,0,out,nonce.length,sealed.length);return out;
  }catch(GeneralSecurityException e){throw new IllegalStateException("Das Kennwort konnte nicht verschlüsselt werden.");}
 }
 public String decrypt(String channelId,byte[] sealed){
  try{
   if(sealed==null||sealed.length<28)throw new GeneralSecurityException();
   var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(sealed,0,12)));c.updateAAD(channelId.getBytes(StandardCharsets.UTF_8));
   return new String(c.doFinal(sealed,12,sealed.length-12),StandardCharsets.UTF_8);
  }catch(GeneralSecurityException e){throw new IllegalStateException("Das Kennwort konnte nicht entschlüsselt werden (Schlüssel geändert?).");}
 }
}
