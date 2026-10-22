package de.ostms.lc.apikey;

import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** API keys of a tenant for the external REST API (/api/v1). A key carries scopes: it can only do what its scopes allow, and only under /api/v1. */
@Service
public class ApiKeyService {
 public static final String PREFIX="lca_";
 /** READ: look at files, documents and findings. LC_CREATE: create a file from an MT700. DOCUMENT_UPLOAD: attach documents to a file. */
 public static final List<String> SCOPES=List.of("READ","LC_CREATE","DOCUMENT_UPLOAD");
 static final int MAX_ACTIVE=20;
 public record View(UUID id,String name,String prefix,List<String> scopes,String createdBy,LocalDateTime createdAt,LocalDateTime lastUsedAt,boolean revoked){}
 public record Created(View key,String secret){}
 public record Identity(UUID keyId,UUID tenantId,String name,List<String> scopes){}
 private final ApiKeyRepository keys;private final TenantRepository tenants;private final SecureRandom random=new SecureRandom();
 public ApiKeyService(ApiKeyRepository keys,TenantRepository tenants){this.keys=keys;this.tenants=tenants;}

 static String hash(String secret){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 static View view(ApiKey k){return new View(k.getId(),k.getName(),k.getKeyPrefix(),k.scopes(),k.getCreatedBy(),k.getCreatedAt(),k.getLastUsedAt(),k.isRevoked());}
 @Transactional(readOnly=true) public List<View> list(){return keys.forTenant(TenantContext.currentId()).stream().map(ApiKeyService::view).toList();}

 /** The secret is returned once and never stored; only its hash is kept. */
 @Transactional public Created create(String name,List<String> scopes,String user){
  String clean=name==null?"":name.strip();
  if(clean.isEmpty()||clean.length()>100)throw new IllegalArgumentException("Bitte einen Namen (höchstens 100 Zeichen) angeben, zum Beispiel „ERP-Anbindung“.");
  var chosen=new LinkedHashSet<String>();
  for(String s:scopes==null?List.<String>of():scopes){String v=s==null?"":s.trim().toUpperCase(Locale.ROOT);if(v.isEmpty())continue;if(!SCOPES.contains(v))throw new IllegalArgumentException("Unbekannter Berechtigungsumfang.");chosen.add(v);}
  if(chosen.isEmpty())throw new IllegalArgumentException("Bitte mindestens einen Berechtigungsumfang wählen.");
  UUID tenant=TenantContext.currentId();
  if(keys.activeCount(tenant)>=MAX_ACTIVE)throw new IllegalArgumentException("Es sind bereits "+MAX_ACTIVE+" API-Schlüssel aktiv. Bitte nicht mehr benötigte widerrufen.");
  byte[] bytes=new byte[32];random.nextBytes(bytes);
  String secret=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var key=keys.save(new ApiKey(tenant,clean,hash(secret),secret.substring(0,PREFIX.length()+4),List.copyOf(chosen),user));
  return new Created(view(key),secret);
 }
 @Transactional public View revoke(UUID id){
  var key=keys.findById(id).filter(k->k.getTenantId().equals(TenantContext.currentId())).orElseThrow(()->new NoSuchElementException("API-Schlüssel nicht gefunden."));
  key.revoke();return view(keys.save(key));
 }

 /** The bearer arrives before the tenant is known; the lookup by hash runs in the deliberate platform scope. */
 public Optional<Identity> authenticate(String secret){
  if(secret==null||!secret.startsWith(PREFIX)||secret.length()>200)return Optional.empty();
  try(var platform=TenantContext.openPlatform()){return authenticateByHash(secret);}
 }
 @Transactional Optional<Identity> authenticateByHash(String secret){
  var key=keys.findByKeyHash(hash(secret)).filter(k->!k.isRevoked()).orElse(null);
  if(key==null)return Optional.empty();
  var tenant=tenants.findById(key.getTenantId()).orElse(null);
  if(tenant==null||!tenant.isActive())return Optional.empty();
  var now=LocalDateTime.now();
  if(key.getLastUsedAt()==null||key.getLastUsedAt().isBefore(now.minusMinutes(1))){key.used(now);keys.save(key);}
  return Optional.of(new Identity(key.getId(),key.getTenantId(),key.getName(),key.scopes()));
 }
}
