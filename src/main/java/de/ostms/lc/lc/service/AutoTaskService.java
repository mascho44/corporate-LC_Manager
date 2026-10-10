package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.AutomationRule;
import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.AutomationRuleRepository;
import de.ostms.lc.lc.repository.LcTaskRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Creates team tasks from events (EBICS message, inbox document, approaching deadline) according to the tenant's rules, and closes them when the subject is handled. */
@Service
public class AutoTaskService {
 public static final String EBICS_MESSAGE="EBICS_MESSAGE",INBOX_ITEM="INBOX_ITEM",DEADLINE="DEADLINE";
 public static final List<String> TRIGGERS=List.of(EBICS_MESSAGE,INBOX_ITEM,DEADLINE);
 static final String SYSTEM="system:automation";
 public record RuleView(String trigger,boolean enabled,UUID teamId,String teamName,int leadDays){}
 public record RuleRequest(String trigger,boolean enabled,UUID teamId,Integer leadDays){}
 private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.uuuu");
 private final AutomationRuleRepository rules;private final LcTaskRepository tasks;private final LetterOfCreditRepository lcs;private final TeamService teams;
 public AutoTaskService(AutomationRuleRepository r,LcTaskRepository t,LetterOfCreditRepository l,TeamService te){rules=r;tasks=t;lcs=l;teams=te;}

 // ---- configuration -------------------------------------------------------------------------
 @Transactional(readOnly=true) public List<RuleView> rules(){
  var byTrigger=new HashMap<String,AutomationRule>();rules.rules().forEach(r->byTrigger.put(r.getTrigger(),r));
  var names=new HashMap<UUID,String>();teams.all().forEach(t->names.put(t.id(),t.name()));
  var result=new ArrayList<RuleView>();
  for(String trigger:TRIGGERS){
   var r=byTrigger.get(trigger);
   result.add(r==null?new RuleView(trigger,false,null,null,3):new RuleView(trigger,r.isEnabled(),r.getTeamId(),r.getTeamId()==null?null:names.get(r.getTeamId()),r.getLeadDays()));
  }
  return result;
 }
 @Transactional public RuleView update(RuleRequest request){
  if(request==null||!TRIGGERS.contains(request.trigger()))throw new IllegalArgumentException("Unbekannter Auslöser.");
  int lead=request.leadDays()==null?3:request.leadDays();
  if(lead<0||lead>60)throw new IllegalArgumentException("Die Vorlaufzeit muss zwischen 0 und 60 Tagen liegen.");
  if(request.enabled()){
   if(request.teamId()==null)throw new IllegalArgumentException("Bitte ein Team wählen.");
   if(!teams.one(request.teamId()).isActive())throw new IllegalArgumentException("Das Team ist nicht aktiv.");
  }
  var rule=rules.forTrigger(request.trigger()).orElseGet(()->new AutomationRule(request.trigger()));
  rule.configure(request.enabled(),request.teamId(),lead);rules.save(rule);
  return rules().stream().filter(v->v.trigger().equals(request.trigger())).findFirst().orElseThrow();
 }

 // ---- event triggers ------------------------------------------------------------------------
 @Transactional public Optional<LcTask> onEbicsMessage(UUID messageId,String type,String reference){
  return create(EBICS_MESSAGE,"Nachricht prüfen und importieren: "+type+(reference==null||reference.isBlank()?"":" "+reference),null,EBICS_MESSAGE,messageId,"EBICS:"+messageId,LocalDate.now().plusDays(1));
 }
 @Transactional public Optional<LcTask> onInboxItem(UUID itemId,String filename){
  return create(INBOX_ITEM,"Dokument zuordnen: "+(filename==null?"ohne Namen":filename),null,INBOX_ITEM,itemId,"INBOX:"+itemId,LocalDate.now().plusDays(1));
 }
 /** Closes the automatic tasks of a handled subject (imported/discarded message, assigned/deleted inbox document). */
 @Transactional public int closeFor(String subjectType,UUID subjectId,String actor){
  int closed=0;
  for(LcTask task:tasks.openForSubject(subjectType,subjectId)){
   task.setCompleted(true);task.setCompletedAt(LocalDateTime.now());task.setCompletedBy(actor==null?SYSTEM:actor);
   if(task.getAssignedTo()==null)task.setAssignedTo(task.getCompletedBy());
   tasks.save(task);closed++;
  }
  return closed;
 }

 // ---- deadlines -----------------------------------------------------------------------------
 /** Creates one task per dossier and deadline (expiry date, follow-up date) that falls within the lead time; never twice for the same date. */
 @Transactional public int scanDeadlines(LocalDate today){
  var rule=rules.forTrigger(DEADLINE).filter(AutomationRule::isEnabled).orElse(null);
  if(rule==null||rule.getTeamId()==null)return 0;
  var team=teams.one(rule.getTeamId());if(!team.isActive())return 0;
  LocalDate horizon=today.plusDays(rule.getLeadDays());int created=0;
  for(LetterOfCredit lc:lcs.findAll()){
   if(lc.getStatus()==LetterOfCreditStatus.CLOSED||lc.getStatus()==LetterOfCreditStatus.EXPIRED)continue;
   created+=deadline(rule,lc,"EXPIRY","Ablauf",lc.getExpiryDate(),today,horizon)?1:0;
   created+=deadline(rule,lc,"FOLLOWUP","Wiedervorlage",lc.getFollowUpDate(),today,horizon)?1:0;
  }
  return created;
 }
 private boolean deadline(AutomationRule rule,LetterOfCredit lc,String kind,String label,LocalDate date,LocalDate today,LocalDate horizon){
  if(date==null||date.isBefore(today)||date.isAfter(horizon))return false;
  String key="DEADLINE:"+lc.getId()+":"+kind+":"+date;
  return create(DEADLINE,"Frist: "+lc.getReference()+" – "+label+" am "+DATE.format(date),lc.getId(),null,null,key,date).isPresent();
 }

 // ---- shared --------------------------------------------------------------------------------
 private Optional<LcTask> create(String trigger,String title,UUID lcId,String subjectType,UUID subjectId,String key,LocalDate due){
  var rule=rules.forTrigger(trigger).filter(AutomationRule::isEnabled).orElse(null);
  if(rule==null||rule.getTeamId()==null)return Optional.empty();
  var team=teams.one(rule.getTeamId());if(!team.isActive())return Optional.empty();
  if(tasks.autoKeyExists(key))return Optional.empty();
  var task=new LcTask();
  task.setLetterOfCreditId(lcId);task.setSubject(subjectType,subjectId);
  task.setTitle(title.length()>500?title.substring(0,500):title);task.setDueDate(due);task.setCreatedBy(SYSTEM);
  task.setTeamId(team.getId());task.setSource("AUTO_"+trigger);task.setAutoKey(key);
  return Optional.of(tasks.save(task));
 }
}
