package de.corporate.lc.user.service;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Separate key domain and authenticated row binding; never log plaintext or ciphertext. */
@Component public class InvitationMailCipher {
 private final String secret;private final SecureRandom random=new SecureRandom();
 public InvitationMailCipher(@Value("${app.mail.invitation-encryption-key:${app.security.totp-encryption-key:}}") String secret){this.secret=secret;}
 private SecretKeySpec key()throws GeneralSecurityException{if(secret==null||secret.length()<32)throw new IllegalStateException("Invitation mail encryption requires a configured key of at least 32 characters.");return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("lc-invitation-mail-v1\0"+secret).getBytes(StandardCharsets.UTF_8)),"AES");}
 public String encrypt(String row,String value){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));c.updateAAD(row.getBytes(StandardCharsets.UTF_8));byte[] bytes=c.doFinal(value.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+bytes.length).put(nonce).put(bytes).array());}catch(GeneralSecurityException e){throw new IllegalStateException("Invitation mail encryption failed.");}}
 public String decrypt(String row,String value){try{byte[] all=Base64.getDecoder().decode(value);if(all.length<28)throw new GeneralSecurityException();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(all,12)));c.updateAAD(row.getBytes(StandardCharsets.UTF_8));return new String(c.doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8);}catch(GeneralSecurityException|IllegalArgumentException e){throw new IllegalStateException("Invitation mail decryption failed.");}}
}
