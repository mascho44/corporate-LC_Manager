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
 public record View(UUID id,String type,String name,boolean enabled,int intervalMinutes,LocalDateTime lastRunAt,String lastResult,String folder){}
 public record Request(String type,String name,Boolean enabled,Integer intervalMinutes){}
 private final IntakeChannelRepository channels;private final InboxIntakeService intake;private final AuditService audit;private final Map<String,IntakeChannel> byType=new HashMap<>();private final FolderIntakeChannel folder;
 public IntakeChannelService(IntakeChannelRepository channels,InboxIntakeService intake,AuditService audit,List<IntakeChannel> implementations,FolderIntakeChannel folder){
  this.channels=channels;this.intake=intake;this.audit=audit;this.folder=folder;implementations.forEach(c->byType.put(c.type(),c));
 }
 private View view(IntakeChannelConfig c){
  String where=FolderIntakeChannel.TYPE.equals(c.getType())?folder.dropFolder(TenantContext.currentId(),c.getName()).toString():null;
  return new View(c.getId(),c.getType(),c.getName(),c.isEnabled(),c.getIntervalMinutes(),c.getLastRunAt(),c.getLastResult(),where);
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
  var saved=channels.save(new IntakeChannelConfig(request.type(),name,Boolean.TRUE.equals(request.enabled()),interval(request.intervalMinutes()),auth.getName()));
  audit.record(auth,"INTAKE_CHANNEL_CREATED","INTAKE_CHANNEL",saved.getId(),request.type()+" · "+name);
  return view(saved);
 }
 public View update(UUID id,Request request,Authentication auth){
  var c=channels.findById(id).orElseThrow(()->new NoSuchElementException("Kanal nicht gefunden."));
  c.update(request.enabled()==null?c.isEnabled():request.enabled(),request.intervalMinutes()==null?c.getIntervalMinutes():interval(request.intervalMinutes()));
  var saved=channels.save(c);
  audit.record(auth,"INTAKE_CHANNEL_UPDATED","INTAKE_CHANNEL",id,saved.getName()+" · "+(saved.isEnabled()?"an":"aus")+" · alle "+saved.getIntervalMinutes()+" Min.");
  return view(saved);
 }
 public void delete(UUID id,Authentication auth){
  var c=channels.findById(id).orElseThrow(()->new NoSuchElementException("Kanal nicht gefunden."));
  channels.delete(c);audit.record(auth,"INTAKE_CHANNEL_DELETED","INTAKE_CHANNEL",id,c.getName());
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
