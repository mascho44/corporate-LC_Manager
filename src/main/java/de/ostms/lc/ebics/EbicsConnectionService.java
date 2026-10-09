package de.ostms.lc.ebics;
import de.ostms.lc.audit.service.AuditService;
import org.kopi.ebics.client.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/** Configures the tenant's EBICS bank connection and runs the key setup (INI/HIA, then HPB after the bank released the subscriber). */
@Service
public class EbicsConnectionService {
 public record View(boolean configured,String url,String hostId,String partnerId,String userId,String status,String lastError,boolean encryptionConfigured){}
 public record Fingerprints(String a005,String e002,String x002){}
 public record Request(String url,String hostId,String partnerId,String userId){}
 private static final Pattern ID=Pattern.compile("[A-Za-z0-9]{1,35}");
 private final EbicsConnectionRepository connections;private final EbicsClientPort client;private final EbicsCipher cipher;
 private final EbicsUrlPolicy urls;private final AuditService audit;
 public EbicsConnectionService(EbicsConnectionRepository c,EbicsClientPort p,EbicsCipher ci,EbicsUrlPolicy u,AuditService a){connections=c;client=p;cipher=ci;urls=u;audit=a;}

 @Transactional(readOnly=true) public View view(){
  return connections.findCurrent().map(c->new View(true,c.getUrl(),c.getHostId(),c.getPartnerId(),c.getUserId(),c.getStatus().name(),c.getLastError(),cipher.configured()))
   .orElse(new View(false,null,null,null,null,"NEW",null,cipher.configured()));
 }

 @Transactional public View save(Request r,Authentication auth){
  if(r==null)throw new IllegalArgumentException("Angaben fehlen.");
  String url=urls.check(r.url()).toString();
  String host=id(r.hostId(),"Host-ID"),partner=id(r.partnerId(),"Partner-ID"),user=id(r.userId(),"Teilnehmer-ID");
  var existing=connections.findCurrent();
  var c=existing.orElseGet(EbicsConnection::new);
  boolean changed=existing.isPresent()&&(!c.getUrl().equals(url)||!c.getHostId().equals(host)||!c.getPartnerId().equals(partner)||!c.getUserId().equals(user));
  if(changed&&c.getStatus()!=EbicsStatus.NEW)throw new IllegalStateException("Die Verbindung ist bereits eingerichtet. Bitte zuerst zurücksetzen, dann ändern.");
  c.setUrl(url);c.setHostId(host);c.setPartnerId(partner);c.setUserId(user);
  connections.save(c);
  audit.recordInTransaction(auth,existing.isPresent()?"EBICS_CONNECTION_UPDATED":"EBICS_CONNECTION_CREATED","EBICS_CONNECTION",c.getId(),"Host "+host+" · Partner "+partner+" · Teilnehmer "+user+" · "+url);
  return view();
 }

 /** Generates keys and sends INI and HIA. Keys are persisted (encrypted) before anything is sent, so a bank-side success can never be lost. */
 @Transactional(noRollbackFor=IllegalStateException.class) public Fingerprints initialise(Authentication auth){
  var c=current();
  if(c.getStatus()==EbicsStatus.KEYS_SENT||c.getStatus()==EbicsStatus.ACTIVE)throw new IllegalStateException("Die Schlüssel wurden bereits gesendet (Status "+c.getStatus()+").");
  String row=c.getId().toString();
  try{
   var keys=client.createKeys(urls.check(c.getUrl()),c.getHostId(),c.getPartnerId(),c.getUserId());
   c.setBankBlob(cipher.encrypt(row,EbicsSerialisierung.serialisieren(keys.bank())));
   c.setPartnerBlob(cipher.encrypt(row,EbicsSerialisierung.serialisieren(keys.partner())));
   c.setUserBlob(cipher.encrypt(row,EbicsSerialisierung.serialisieren(keys.user())));
   client.sendIni(keys.user());
   client.sendHia(keys.user());
   c.setStatus(EbicsStatus.KEYS_SENT);c.setLastError(null);connections.save(c);
   audit.recordInTransaction(auth,"EBICS_KEYS_SENT","EBICS_CONNECTION",c.getId(),"INI/HIA gesendet · Host "+c.getHostId()+" · Teilnehmer "+c.getUserId());
   return fingerprints(keys.user());
  }catch(IllegalStateException e){throw e;}
  catch(Exception e){fail(c,"INI/HIA fehlgeschlagen: "+e.getMessage(),auth);throw new IllegalStateException("INI/HIA fehlgeschlagen. Details im Audit-Protokoll.");}
 }

 /** HPB works only after the bank released the subscriber; afterwards the connection is ACTIVE. */
 @Transactional public Fingerprints fetchBankKeys(Authentication auth){
  var c=current();
  if(c.getStatus()!=EbicsStatus.KEYS_SENT)throw new IllegalStateException("Bankschlüssel können erst nach gesendeten eigenen Schlüsseln abgeholt werden (Status "+c.getStatus()+").");
  String row=c.getId().toString();
  try{
   var bank=EbicsSerialisierung.bankLesen(cipher.decrypt(row,c.getBankBlob()));
   var partner=EbicsSerialisierung.partnerLesen(cipher.decrypt(row,c.getPartnerBlob()),bank);
   var user=EbicsSerialisierung.userLesen(cipher.decrypt(row,c.getUserBlob()),partner);
   client.fetchBankKeys(user);
   c.setBankBlob(cipher.encrypt(row,EbicsSerialisierung.serialisieren(bank)));
   c.setStatus(EbicsStatus.ACTIVE);c.setLastError(null);connections.save(c);
   audit.recordInTransaction(auth,"EBICS_BANK_KEYS_FETCHED","EBICS_CONNECTION",c.getId(),"HPB abgeholt · Host "+c.getHostId());
   return fingerprints(user);
  }catch(IllegalStateException e){throw e;}
  catch(Exception e){
   audit.record(auth,"EBICS_BANK_KEYS_FAILED","EBICS_CONNECTION",c.getId(),"HPB fehlgeschlagen: "+trim(e.getMessage()));
   throw new IllegalStateException("Bankschlüssel konnten nicht abgeholt werden. Wurde der Teilnehmer bankseitig schon freigegeben?");
  }
 }

 @Transactional(readOnly=true) public Fingerprints fingerprints(){
  var c=current();
  if(c.getStatus()==EbicsStatus.NEW)throw new IllegalStateException("Es wurden noch keine Schlüssel erzeugt.");
  try{
   String row=c.getId().toString();
   var bank=EbicsSerialisierung.bankLesen(cipher.decrypt(row,c.getBankBlob()));
   var partner=EbicsSerialisierung.partnerLesen(cipher.decrypt(row,c.getPartnerBlob()),bank);
   return fingerprints(EbicsSerialisierung.userLesen(cipher.decrypt(row,c.getUserBlob()),partner));
  }catch(IllegalStateException e){throw e;}
  catch(Exception e){throw new IllegalStateException("Fingerabdrücke konnten nicht ermittelt werden.");}
 }

 /** Discards keys, e.g. after the bank reset the subscriber. The old keys are useless afterwards. */
 @Transactional public View reset(Authentication auth){
  var c=current();c.clearKeys();c.setStatus(EbicsStatus.NEW);c.setLastError(null);connections.save(c);
  audit.recordInTransaction(auth,"EBICS_CONNECTION_RESET","EBICS_CONNECTION",c.getId(),"Schlüssel verworfen · Host "+c.getHostId());
  return view();
 }

 private void fail(EbicsConnection c,String message,Authentication auth){
  c.setStatus(EbicsStatus.ERROR);c.setLastError(trim(message));connections.save(c);
  audit.recordInTransaction(auth,"EBICS_KEYS_FAILED","EBICS_CONNECTION",c.getId(),trim(message));
 }
 private EbicsConnection current(){return connections.findCurrent().orElseThrow(()->new IllegalStateException("Es ist noch keine EBICS-Verbindung angelegt."));}
 private static String id(String v,String label){
  String t=v==null?"":v.trim();
  if(!ID.matcher(t).matches())throw new IllegalArgumentException(label+" darf nur Buchstaben und Ziffern enthalten (höchstens 35 Zeichen).");
  return t;
 }
 private static String trim(String s){String t=s==null?"":s.replaceAll("[\\r\\n]+"," ");return t.length()>480?t.substring(0,480):t;}
 private static Fingerprints fingerprints(User user)throws IllegalStateException{
  try{return new Fingerprints(sha(user.getA005Certificate()),sha(user.getE002Certificate()),sha(user.getX002Certificate()));}
  catch(Exception e){throw new IllegalStateException("Fingerabdrücke konnten nicht berechnet werden.");}
 }
 private static String sha(byte[] data)throws Exception{return HexFormat.of().withUpperCase().withDelimiter(" ").formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
}
