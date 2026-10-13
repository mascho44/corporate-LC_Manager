package de.ostms.lc.capture;

import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** Tokens for scan stations (mailroom). A token authorises exactly one thing: delivering documents to the inbox of its tenant. */
@Service
public class CaptureTokenService {
 public static final String PREFIX="lcc_";
 static final int MAX_ACTIVE=20;
 public record View(UUID id,String name,String prefix,String createdBy,LocalDateTime createdAt,LocalDateTime lastUsedAt,boolean revoked){}
 public record Created(View token,String secret){}
 public record Identity(UUID tokenId,UUID tenantId,String name){}

 private final CaptureTokenRepository tokens;private final TenantRepository tenants;private final SecureRandom random=new SecureRandom();
 public CaptureTokenService(CaptureTokenRepository tokens,TenantRepository tenants){this.tokens=tokens;this.tenants=tenants;}

 static String hash(String secret){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 static View view(CaptureToken t){return new View(t.getId(),t.getName(),t.getTokenPrefix(),t.getCreatedBy(),t.getCreatedAt(),t.getLastUsedAt(),t.isRevoked());}

 @Transactional(readOnly=true) public List<View> list(){return tokens.forTenant(TenantContext.currentId()).stream().map(CaptureTokenService::view).toList();}

 /** The secret is returned once and never stored; only its hash is kept. */
 @Transactional public Created create(String name,String user){
  String clean=name==null?"":name.strip();
  if(clean.isEmpty()||clean.length()>100)throw new IllegalArgumentException("Bitte einen Namen (höchstens 100 Zeichen) angeben, zum Beispiel „Poststelle Scanner 2“.");
  UUID tenant=TenantContext.currentId();
  if(tokens.activeCount(tenant)>=MAX_ACTIVE)throw new IllegalArgumentException("Es sind bereits "+MAX_ACTIVE+" Zugänge aktiv. Bitte nicht mehr benötigte widerrufen.");
  byte[] bytes=new byte[32];random.nextBytes(bytes);
  String secret=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var token=tokens.save(new CaptureToken(tenant,clean,hash(secret),secret.substring(0,PREFIX.length()+4),user));
  return new Created(view(token),secret);
 }

 @Transactional public View revoke(UUID id){
  var token=tokens.findById(id).filter(t->t.getTenantId().equals(TenantContext.currentId())).orElseThrow(()->new NoSuchElementException("Zugang nicht gefunden."));
  token.revoke();return view(tokens.save(token));
 }

 /** Resolves a presented secret; empty when unknown, revoked or when the tenant is not active. The last use is noted at most once a minute. */
 @Transactional public Optional<Identity> authenticate(String secret){
  if(secret==null||!secret.startsWith(PREFIX)||secret.length()>200)return Optional.empty();
  var token=tokens.findByTokenHash(hash(secret)).filter(t->!t.isRevoked()).orElse(null);
  if(token==null)return Optional.empty();
  var tenant=tenants.findById(token.getTenantId()).orElse(null);
  if(tenant==null||!tenant.isActive())return Optional.empty();
  var now=LocalDateTime.now();
  if(token.getLastUsedAt()==null||token.getLastUsedAt().isBefore(now.minusMinutes(1))){token.used(now);tokens.save(token);}
  return Optional.of(new Identity(token.getId(),token.getTenantId(),token.getName()));
 }
}
