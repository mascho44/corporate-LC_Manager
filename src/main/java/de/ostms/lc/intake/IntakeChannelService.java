package de.ostms.lc.intake;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** Administration and running of the intake channels of the current tenant. */
@Service
public class IntakeChannelService {
 public record MailView(String host,int port,String security,String username,String folder,String processedFolder,String rejectedFolder,List<String> allowedSenders,boolean passwordSet){}
 public record View(UUID id,String type,String name,boolean enabled,int intervalMinutes,LocalDateTime lastRunAt,String lastResult,String folder,MailView mail){}
 /** Mailbox data of a request; fields that are null keep their value on update; an empty password keeps the stored one. */
 public record MailRequest(String host,Integer port,String security,String username,String password,String folder,String processedFolder,String rejectedFolder,List<String> allowedSenders){}
 public record Request(String type,String name,Boolean enabled,Integer intervalMinutes,MailRequest mail){
  public Request(String type,String name,Boolean enabled,Integer intervalMinutes){this(type,name,enabled,intervalMinutes,null);}
 }
 @org.springframework.beans.factory.annotation.Autowired(required=false) private MailIntakeChannel mailChannel;
 @org.springframework.beans.factory.annotation.Autowired(required=false) private IntakeCipher cipher;
 @org.springframework.beans.factory.annotation.Autowired(required=false) private MailHostPolicy hosts;
 private final IntakeChannelRepository channels;private final InboxIntakeService intake;private final AuditService audit;private final Map<String,IntakeChannel> byType=new HashMap<>();private final FolderIntakeChannel folder;
 public IntakeChannelService(IntakeChannelRepository channels,InboxIntakeService intake,AuditService audit,List<IntakeChannel> implementations,FolderIntakeChannel folder){
  this.channels=channels;this.intake=intake;this.audit=audit;this.folder=folder;implementations.forEach(c->byType.put(c.type(),c));
 }
 private View view(IntakeChannelConfig c){
  String where=FolderIntakeChannel.TYPE.equals(c.getType())?folder.dropFolder(TenantContext.currentId(),c.getName()).toString():null;
  MailView mail=null;
  if(MailIntakeChannel.TYPE.equals(c.getType())&&c.getSettings()!=null){var m=MailboxSettings.fromJson(c.getSettings());mail=new MailView(m.host(),m.port(),m.security(),m.username(),m.folder(),m.processedFolder(),m.rejectedFolder(),m.allowedSenders(),c.hasSecret());}
  return new View(c.getId(),c.getType(),c.getName(),c.isEnabled(),c.getIntervalMinutes(),c.getLastRunAt(),c.getLastResult(),where,mail);
 }
 static int interval(Integer minutes){
  int m=minutes==null?2:minutes;
  if(m<1||m>1440)throw new IllegalArgumentException("Das Intervall muss zwischen 1 und 1440 Minuten liegen.");
  return m;
 }
 public List<View> list(){return channels.findAll().stream().map(this::view).toList();}
 public View create(Request request,Authentication auth)throws java.io.IOException{
  if(request==null||request.type()==null||!byType.containsKey(request.type()))throw new IllegalArgumentException("Unbekannter Kanaltyp.");
  String name=request.name()==null?"":request.name().strip();
  if(channels.byName(name).isPresent())throw new IllegalArgumentException("Es gibt schon einen Kanal mit diesem Namen.");
  if(FolderIntakeChannel.TYPE.equals(request.type()))folder.folders(TenantContext.currentId(),name); // validates the name and creates the folders
  else if(!name.matches("[a-z0-9][a-z0-9-]{0,39}"))throw new IllegalArgumentException("Der Name darf nur Kleinbuchstaben, Ziffern und Bindestriche enthalten (höchstens 40 Zeichen).");
  boolean enabled=Boolean.TRUE.equals(request.enabled());
  var config=new IntakeChannelConfig(request.type(),name,enabled,interval(request.intervalMinutes()),auth.getName());
  if(MailIntakeChannel.TYPE.equals(request.type()))applyMail(config,request.mail(),enabled);
  var saved=channels.save(config);
  audit.record(auth,"INTAKE_CHANNEL_CREATED","INTAKE_CHANNEL",saved.getId(),request.type()+" · "+name);
  return view(saved);
 }
 public View update(UUID id,Request request,Authentication auth){
  var c=channels.findById(id).orElseThrow(()->new NoSuchElementException("Kanal nicht gefunden."));
  boolean enabled=request.enabled()==null?c.isEnabled():request.enabled();
  if(MailIntakeChannel.TYPE.equals(c.getType()))applyMail(c,request.mail(),enabled);
  c.update(enabled,request.intervalMinutes()==null?c.getIntervalMinutes():interval(request.intervalMinutes()));
  var saved=channels.save(c);
  audit.record(auth,"INTAKE_CHANNEL_UPDATED","INTAKE_CHANNEL",id,saved.getName()+" · "+(saved.isEnabled()?"an":"aus")+" · alle "+saved.getIntervalMinutes()+" Min.");
  return view(saved);
 }
 public void delete(UUID id,Authentication auth){
  var c=channels.findById(id).orElseThrow(()->new NoSuchElementException("Kanal nicht gefunden."));
  channels.delete(c);audit.record(auth,"INTAKE_CHANNEL_DELETED","INTAKE_CHANNEL",id,c.getName());
 }
 /** Takes the mailbox data of a request into the configuration: validates, encrypts the password, refuses to switch on a mailbox that cannot work. */
 private void applyMail(IntakeChannelConfig c,MailRequest m,boolean enabled){
  var old=c.getSettings()==null?null:MailboxSettings.fromJson(c.getSettings());
  if(m==null&&old==null)throw new IllegalArgumentException("Bitte die Postfach-Daten angeben.");
  if(m!=null){
   String security=m.security()!=null?m.security():old!=null?old.security():MailboxSettings.SSL;
   int port=m.port()!=null?m.port():old!=null&&m.security()==null?old.port():MailboxSettings.STARTTLS.equalsIgnoreCase(security)?143:993;
   var next=new MailboxSettings(m.host()!=null?m.host():old==null?null:old.host(),port,security,m.username()!=null?m.username():old==null?null:old.username(),
    m.folder()!=null?m.folder():old==null?null:old.folder(),m.processedFolder()!=null?m.processedFolder():old==null?null:old.processedFolder(),m.rejectedFolder()!=null?m.rejectedFolder():old==null?null:old.rejectedFolder(),
    m.allowedSenders()!=null?m.allowedSenders():old==null?null:old.allowedSenders());
   if(hosts!=null)hosts.check(next.host());
   c.setSettings(next.toJson());
   if(m.password()!=null&&!m.password().isEmpty()){
    if(cipher==null||!cipher.configured())throw new IllegalStateException("Das Postfach-Kennwort kann erst gespeichert werden, wenn INTAKE_ENCRYPTION_KEY (mindestens 32 Zeichen) gesetzt ist.");
    if(m.password().length()>500)throw new IllegalArgumentException("Das Kennwort ist zu lang.");
    c.setSecret(cipher.encrypt(c.getId().toString(),m.password()));
   }
  }
  if(enabled){
   if(!c.hasSecret())throw new IllegalArgumentException("Bitte das Kennwort des Postfachs eintragen, bevor der Kanal eingeschaltet wird.");
   if(MailboxSettings.fromJson(c.getSettings()).allowedSenders().isEmpty())throw new IllegalArgumentException("Bitte mindestens einen freigegebenen Absender angeben; ohne Liste wird keine Mail übernommen.");
  }
 }
 /** Connection test: nothing is read or changed; returns a sentence for the administrator. */
 public String test(UUID id,Authentication auth){
  var c=channels.findById(id).orElseThrow(()->new NoSuchElementException("Kanal nicht gefunden."));
  String result;
  if(MailIntakeChannel.TYPE.equals(c.getType())&&mailChannel!=null){
   try{result="Verbindung in Ordnung, "+mailChannel.test(c)+" ungelesene Mail(s) im Ordner.";}
   catch(Exception failure){result="Fehler: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
  }else result="Der Ordner ist erreichbar.";
  audit.record(auth,"INTAKE_CHANNEL_TEST","INTAKE_CHANNEL",id,c.getName()+" · "+result);
  return result;
 }
 /** Runs one channel now (also when switched off, for a test); the outcome is stored with the channel. */
 public View runNow(UUID id,Authentication auth){
  var c=channels.findById(id).orElseThrow(()->new NoSuchElementException("Kanal nicht gefunden."));
  run(c);
  audit.record(auth,"INTAKE_CHANNEL_RUN","INTAKE_CHANNEL",id,c.getName()+" · "+c.getLastResult());
  return view(c);
 }
 /** For the worker, inside a tenant scope: every channel that is on and due. */
 public void pollDue(){
  var now=LocalDateTime.now();
  for(var c:channels.findAll())if(c.due(now))run(c);
 }
 private void run(IntakeChannelConfig c){
  var channel=byType.get(c.getType());
  String result;
  if(channel==null)result="Fehler: Kanaltyp nicht verfügbar";
  else try{result=channel.poll(c,intake::accept).summary();}
  catch(Exception failure){result="Fehler: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
  c.recordRun(result,LocalDateTime.now());channels.save(c);
 }
}
