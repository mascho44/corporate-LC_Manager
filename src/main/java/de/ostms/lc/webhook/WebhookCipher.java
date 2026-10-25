package de.ostms.lc.webhook;

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

/** AES-256-GCM for the signing secret of a webhook; the subscription id is authenticated so a value cannot be moved to another subscription. Never log plaintext or ciphertext. */
@Component
public class WebhookCipher {
 private final String secret;private final SecureRandom random=new SecureRandom();
 public WebhookCipher(@Value("${app.webhooks.encryption-key:}") String secret){this.secret=secret;}
 public boolean configured(){return secret!=null&&secret.length()>=32;}
 private SecretKeySpec key()throws GeneralSecurityException{
  if(!configured())throw new IllegalStateException("Webhooks können erst eingerichtet werden, wenn WEBHOOK_ENCRYPTION_KEY oder EBICS_ENCRYPTION_KEY (mindestens 32 Zeichen) gesetzt ist.");
  return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("lcm-webhook-v1:"+secret).getBytes(StandardCharsets.UTF_8)),"AES");
 }
 public byte[] encrypt(String id,String plain){
  try{
   byte[] nonce=new byte[12];random.nextBytes(nonce);
   var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
   byte[] sealed=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[nonce.length+sealed.length];
   System.arraycopy(nonce,0,out,0,nonce.length);System.arraycopy(sealed,0,out,nonce.length,sealed.length);return out;
  }catch(GeneralSecurityException e){throw new IllegalStateException("Das Webhook-Geheimnis konnte nicht verschlüsselt werden.");}
 }
 public String decrypt(String id,byte[] sealed){
  try{
   if(sealed==null||sealed.length<28)throw new GeneralSecurityException();
   var c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(sealed,0,12)));c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
   return new String(c.doFinal(sealed,12,sealed.length-12),StandardCharsets.UTF_8);
  }catch(GeneralSecurityException e){throw new IllegalStateException("Das Webhook-Geheimnis konnte nicht entschlüsselt werden (Schlüssel geändert?).");}
 }
}
