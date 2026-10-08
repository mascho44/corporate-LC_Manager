package de.ostms.lc.user.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class TotpService {
    private static final char[] BASE32="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final AppUserRepository users;private final PasswordEncoder passwords;private final String encryptionKey;private final SecureRandom random=new SecureRandom();
    public TotpService(AppUserRepository users,PasswordEncoder passwords,@Value("${app.security.totp-encryption-key:}")String encryptionKey){this.users=users;this.passwords=passwords;this.encryptionKey=encryptionKey;}
    public record Setup(String secret,String otpauthUri,String qrCodeDataUrl){}
    public boolean enabled(String username){var identity=users.findByUsernameIgnoreCase(username).orElseThrow(()->new NoSuchElementException("Benutzer nicht gefunden"));if(!de.ostms.lc.tenant.domain.Tenant.DEFAULT_ID.equals(identity.getTenantId()))throw new org.springframework.security.access.AccessDeniedException("Global identities must belong to the identity administration tenant.");return identity.isTotpEnabled();}
    public Setup setup(String username){user(username);byte[] bytes=new byte[20];random.nextBytes(bytes);String secret=base32(bytes),label="Corporate LC Manager:"+username,uri="otpauth://totp/"+url(label)+"?secret="+secret+"&issuer="+url("Corporate LC Manager")+"&algorithm=SHA1&digits=6&period=30";return new Setup(secret,uri,qr(uri));}
    @Transactional public List<String> enable(String username,String secret,String code){if(!valid(secret,code))throw new IllegalArgumentException("Der TOTP-Code ist nicht gültig.");AppUser user=user(username);List<String> recovery=new ArrayList<>(),hashes=new ArrayList<>();for(int i=0;i<8;i++){String value=recoveryCode();recovery.add(value);hashes.add(passwords.encode(normalize(value)));}user.setTotpSecretEncrypted(encrypt(secret));user.setRecoveryCodeHashes(String.join("\n",hashes));user.setTotpEnabled(true);return recovery;}
    @Transactional public void disable(String username,String password,String code){AppUser user=user(username);if(user.isPlatformAdministrator()||user.getRole()==de.ostms.lc.user.domain.UserRole.ADMIN||users.hasActiveAdministratorMembership(user.getId()))throw new IllegalArgumentException("Für Administratoren ist Zwei-Faktor-Anmeldung verpflichtend.");if(!passwords.matches(password,user.getPasswordHash()))throw new IllegalArgumentException("Das Passwort ist falsch.");if(!verify(user,code,false))throw new IllegalArgumentException("Der TOTP- oder Notfallcode ist nicht gültig.");user.setTotpEnabled(false);user.setTotpSecretEncrypted(null);user.setRecoveryCodeHashes(null);}
    @Transactional public boolean verifyLogin(String username,String code){return verify(user(username),code,true);}
    private boolean verify(AppUser user,String code,boolean consumeRecovery){if(!user.isTotpEnabled()||user.getTotpSecretEncrypted()==null)return false;String normalized=normalize(code);if(valid(decrypt(user.getTotpSecretEncrypted()),normalized))return true;List<String> hashes=new ArrayList<>(user.getRecoveryCodeHashes()==null?List.of():Arrays.asList(user.getRecoveryCodeHashes().split("\\R")));for(int i=0;i<hashes.size();i++)if(passwords.matches(normalized,hashes.get(i))){if(consumeRecovery){hashes.remove(i);user.setRecoveryCodeHashes(String.join("\n",hashes));}return true;}return false;}
    private boolean valid(String secret,String code){String normalized=normalize(code);if(!normalized.matches("\\d{6}"))return false;long step=System.currentTimeMillis()/30000L;for(long offset=-1;offset<=1;offset++)if(code(secret,step+offset).equals(normalized))return true;return false;}
    String currentCode(String secret){return code(secret,System.currentTimeMillis()/30000L);}
    private String code(String secret,long counter){try{Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(base32Decode(secret),"HmacSHA1"));byte[] hash=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());int offset=hash[hash.length-1]&15,value=((hash[offset]&127)<<24)|((hash[offset+1]&255)<<16)|((hash[offset+2]&255)<<8)|(hash[offset+3]&255);return String.format(Locale.ROOT,"%06d",value%1_000_000);}catch(GeneralSecurityException e){throw new IllegalStateException("TOTP konnte nicht berechnet werden.",e);}}
    private String encrypt(String value){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),all=ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array();return Base64.getEncoder().encodeToString(all);}catch(GeneralSecurityException e){throw new IllegalStateException("TOTP-Schlüssel konnte nicht verschlüsselt werden.",e);}}
    private String decrypt(String value){try{byte[] all=Base64.getDecoder().decode(value),nonce=Arrays.copyOfRange(all,0,12),encrypted=Arrays.copyOfRange(all,12,all.length);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);}catch(GeneralSecurityException e){throw new IllegalStateException("TOTP-Schlüssel konnte nicht entschlüsselt werden.",e);}}
    private SecretKeySpec key(){if(encryptionKey==null||encryptionKey.length()<32)throw new IllegalStateException("TOTP_ENCRYPTION_KEY muss mindestens 32 Zeichen lang sein.");try{return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8)),"AES");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String qr(String uri){try{var matrix=new QRCodeWriter().encode(uri,BarcodeFormat.QR_CODE,260,260);ByteArrayOutputStream out=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",out);return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());}catch(Exception e){throw new IllegalStateException("QR-Code konnte nicht erstellt werden.",e);}}
    private AppUser user(String username){var user=users.findByUsernameIgnoreCase(username).orElseThrow(()->new NoSuchElementException("Benutzer nicht gefunden"));de.ostms.lc.tenant.domain.TenantContext.require(user.getTenantId());return user;}
    private String recoveryCode(){byte[] value=new byte[6];random.nextBytes(value);String hex=HexFormat.of().formatHex(value).toUpperCase(Locale.ROOT);return hex.substring(0,4)+"-"+hex.substring(4,8)+"-"+hex.substring(8,12);}
    private String normalize(String value){return value==null?"":value.replaceAll("[-\\s]","").toUpperCase(Locale.ROOT);}
    private String url(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private String base32(byte[] bytes){StringBuilder out=new StringBuilder();int buffer=0,bits=0;for(byte b:bytes){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){out.append(BASE32[(buffer>>(bits-5))&31]);bits-=5;}}if(bits>0)out.append(BASE32[(buffer<<(5-bits))&31]);return out.toString();}
    private byte[] base32Decode(String value){ByteArrayOutputStream out=new ByteArrayOutputStream();int buffer=0,bits=0;for(char c:value.replace("=","").toUpperCase(Locale.ROOT).toCharArray()){int index="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);if(index<0)continue;buffer=(buffer<<5)|index;bits+=5;if(bits>=8){out.write((buffer>>(bits-8))&255);bits-=8;}}return out.toByteArray();}
}
