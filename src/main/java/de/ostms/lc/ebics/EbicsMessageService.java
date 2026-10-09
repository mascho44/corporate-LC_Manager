package de.ostms.lc.ebics;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.imports.api.SwiftImportPreview;
import de.ostms.lc.imports.api.SwiftImportRequest;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.lc.domain.Amendment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Fetches MT7xx messages from the bank, keeps them once per content and lets a user import or discard them. Nothing is imported automatically. */
@Service
public class EbicsMessageService {
 public static final List<String> TYPES=List.of("MT700","MT707","MT710","MT760");
 public record View(UUID id,String messageType,String reference,String status,String note,java.time.LocalDateTime receivedAt,boolean importable){}
 public record FetchResult(int fetched,int alreadyKnown,List<String> errors){}
 private static final Pattern REFERENCE=Pattern.compile("(?m)^:(20|21):(.*)$");
 private final EbicsConnectionRepository connections;private final EbicsMessageRepository messages;private final EbicsClientPort client;
 private final EbicsConnectionService connectionService;private final SwiftImportService swift;private final AuditService audit;private final DocumentCheckService checks;
 public EbicsMessageService(EbicsConnectionRepository c,EbicsMessageRepository m,EbicsClientPort p,EbicsConnectionService cs,SwiftImportService s,AuditService a,DocumentCheckService ch){connections=c;messages=m;client=p;connectionService=cs;swift=s;audit=a;checks=ch;}

 /** Not transactional on purpose: the bank calls must not hold a database transaction; every message is saved on its own. */
 public FetchResult fetch(Authentication auth){
  var c=connections.findCurrent().orElseThrow(()->new IllegalStateException("Es ist noch keine EBICS-Verbindung angelegt."));
  if(c.getStatus()!=EbicsStatus.ACTIVE)throw new IllegalStateException("Die EBICS-Verbindung ist noch nicht aktiv (Status "+c.getStatus()+").");
  org.kopi.ebics.client.User user;
  try{user=connectionService.loadUser(c);}catch(Exception e){throw new IllegalStateException("Die EBICS-Schlüssel konnten nicht geladen werden.");}
  int fetched=0,known=0;var errors=new ArrayList<String>();
  for(String type:TYPES){
   try{
    byte[] data=client.downloadTradeMessage(user,type);
    if(data==null||data.length==0)continue;
    String text=decode(data);
    if(!text.contains(":20:")&&!text.contains(":21:"))throw new IllegalStateException("Die Antwort enthält keine SWIFT-Felder.");
    String sha=sha256(text);
    if(messages.findBySha(sha).isPresent()){known++;continue;}
    var saved=messages.save(new EbicsMessage(type,sha,text));
    fetched++;
    audit.record(auth,"EBICS_MESSAGE_FETCHED","EBICS_MESSAGE",saved.getId(),type+" · "+reference(text));
   }catch(Exception e){
    String m=e.getMessage()==null?"unbekannter Fehler":e.getMessage().replaceAll("[\\r\\n]+"," ");
    errors.add(type+": "+(m.length()>200?m.substring(0,200):m));
    audit.record(auth,"EBICS_FETCH_FAILED","EBICS_MESSAGE",null,type+" · "+(m.length()>300?m.substring(0,300):m));
   }
  }
  return new FetchResult(fetched,known,List.copyOf(errors));
 }

 @Transactional(readOnly=true) public List<View> list(){return messages.newestFirst().stream().map(EbicsMessageService::view).toList();}

 @Transactional(readOnly=true) public SwiftImportPreview preview(UUID id){
  var m=one(id);
  if(m.getMessageType().equals("MT760"))throw new IllegalArgumentException("MT760 wird nur abgelegt und nicht importiert.");
  return swift.preview(new SwiftImportRequest("ebics-"+m.getMessageType()+"-"+m.getId()+".swift",m.getContent()));
 }

 @Transactional public Object importMessage(UUID id,Authentication auth){
  var m=one(id);
  if(!m.getStatus().equals("NEW"))throw new IllegalStateException("Diese Nachricht wurde bereits bearbeitet.");
  if(m.getMessageType().equals("MT760"))throw new IllegalArgumentException("MT760 wird nur abgelegt und nicht importiert.");
  Object result=swift.execute(new SwiftImportRequest("ebics-"+m.getMessageType()+"-"+m.getId()+".swift",m.getContent()));
  m.handle("IMPORTED",auth==null?"unbekannt":auth.getName(),"Importiert");messages.save(m);
  audit.recordInTransaction(auth,"EBICS_MESSAGE_IMPORTED","EBICS_MESSAGE",m.getId(),m.getMessageType()+" · "+reference(m.getContent()));
  if(result instanceof Amendment amendment){
   var lc=amendment.getLetterOfCredit();long reset=checks.invalidateDecisions(lc.getId());
   audit.recordInTransaction(auth,"MT707_IMPORTED","LETTER_OF_CREDIT",lc.getId(),"Amendment "+amendment.getAmendmentNumber()+" (EBICS)");
   if(reset>0)audit.recordInTransaction(auth,"DOCUMENT_CHECK_DECISIONS_RESET","LETTER_OF_CREDIT",lc.getId(),reset+" Entscheidungen wegen MT707-Amendment zurückgesetzt");
  }
  return result;
 }

 @Transactional public View discard(UUID id,Authentication auth){
  var m=one(id);
  if(!m.getStatus().equals("NEW"))throw new IllegalStateException("Diese Nachricht wurde bereits bearbeitet.");
  m.handle("DISCARDED",auth==null?"unbekannt":auth.getName(),"Verworfen");messages.save(m);
  audit.recordInTransaction(auth,"EBICS_MESSAGE_DISCARDED","EBICS_MESSAGE",m.getId(),m.getMessageType()+" · "+reference(m.getContent()));
  return view(m);
 }

 private EbicsMessage one(UUID id){return messages.findById(id).orElseThrow(()->new NoSuchElementException("Nachricht nicht gefunden."));}
 static View view(EbicsMessage m){return new View(m.getId(),m.getMessageType(),reference(m.getContent()),m.getStatus(),m.getNote(),m.getReceivedAt(),!m.getMessageType().equals("MT760")&&m.getStatus().equals("NEW"));}
 /** :21: (credit number) for MT710, otherwise :20:. */
 static String reference(String text){
  String r20=null,r21=null;var m=REFERENCE.matcher(text);
  while(m.find()){if(m.group(1).equals("20")&&r20==null)r20=m.group(2).trim();if(m.group(1).equals("21")&&r21==null)r21=m.group(2).trim();}
  String r=r21!=null&&!r21.isEmpty()&&text.toUpperCase(Locale.ROOT).matches("(?s).*\\{2:[IO]710.*|(?ms).*^:52[AD]:.*")?r21:r20;
  r=r==null?"":r;return r.length()>35?r.substring(0,35):r;
 }
 static String decode(byte[] data)throws CharacterCodingException{
  String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
  if(text.indexOf('\0')>=0)throw new CharacterCodingException();
  return text.replace("\r\n","\n").replace('\r','\n').strip();
 }
 static String sha256(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
}
