package de.ostms.lc.sla;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** SLA rules per tenant and their evaluation for one file, from the audit events of that file. */
@Service
public class SlaService {
 private static final Pattern ACTION=Pattern.compile("[A-Z][A-Z0-9_]{2,79}");
 public record RuleView(UUID id,String activityKey,String name,List<String> startActions,List<String> stopActions,int targetDays,int warnPercent,boolean active,UUID escalationTeamId){}
 public record RuleRequest(String activityKey,String name,List<String> startActions,List<String> stopActions,Integer targetDays,Integer warnPercent,Boolean active,UUID escalationTeamId){}
 public record InstanceView(UUID ruleId,String ruleName,String activityKey,SlaEvaluator.Instance clock){}
 public record FileStatus(String state,List<InstanceView> instances){}
 private final SlaRuleRepository rules;private final JdbcTemplate jdbc;private final LetterOfCreditRepository lcs;private final AuditService audit;
 public SlaService(SlaRuleRepository rules,JdbcTemplate jdbc,LetterOfCreditRepository lcs,AuditService audit){this.rules=rules;this.jdbc=jdbc;this.lcs=lcs;this.audit=audit;}

 static RuleView view(SlaRule r){return new RuleView(r.getId(),r.getActivityKey(),r.getName(),r.startActions(),r.stopActions(),r.getTargetDays(),r.getWarnPercent(),r.isActive(),r.getEscalationTeamId());}
 @Transactional(readOnly=true) public List<RuleView> list(){return rules.findAll().stream().map(SlaService::view).toList();}

 /** Known audit actions of this tenant plus those of the catalog: what can be chosen as start or stop event. */
 @Transactional(readOnly=true) public List<String> knownActions(){
  var set=new TreeSet<String>();
  SlaCatalog.ACTIVITIES.forEach(a->{set.addAll(a.startActions());set.addAll(a.stopActions());});
  set.addAll(jdbc.queryForList("select distinct action from audit_event where tenant_id=? and entity_type='LETTER_OF_CREDIT' and action ~ '^[A-Z][A-Z0-9_]+$' limit 300",String.class,TenantContext.currentId()));
  return List.copyOf(set);
 }

 static void validate(RuleRequest r,Set<String> taken,UUID selfId,java.util.function.Function<String,Optional<SlaRule>> byName){
  if(r==null||r.name()==null||r.name().isBlank()||r.name().trim().length()>120)throw new IllegalArgumentException("Bitte einen Namen (höchstens 120 Zeichen) angeben.");
  List<String> start=clean(r.startActions()),stop=clean(r.stopActions());
  if(start.isEmpty()||stop.isEmpty())throw new IllegalArgumentException("Bitte mindestens ein Start- und ein Stopp-Ereignis wählen.");
  if(start.size()>8||stop.size()>8)throw new IllegalArgumentException("Höchstens 8 Ereignisse je Seite.");
  if(!Collections.disjoint(start,stop))throw new IllegalArgumentException("Ein Ereignis kann nicht gleichzeitig starten und stoppen.");
  if(r.targetDays()==null||r.targetDays()<1||r.targetDays()>60)throw new IllegalArgumentException("Die Zielzeit muss zwischen 1 und 60 Bankarbeitstagen liegen.");
  int warn=r.warnPercent()==null?80:r.warnPercent();
  if(warn<10||warn>99)throw new IllegalArgumentException("Die Warnschwelle muss zwischen 10 und 99 Prozent liegen.");
  byName.apply(r.name().trim()).ifPresent(other->{if(!other.getId().equals(selfId))throw new IllegalArgumentException("Es gibt schon eine SLA-Regel mit diesem Namen.");});
 }
 static List<String> clean(List<String> actions){
  var out=new LinkedHashSet<String>();
  if(actions!=null)for(String a:actions){String v=a==null?"":a.trim();if(!ACTION.matcher(v).matches())throw new IllegalArgumentException("Ungültiges Ereignis: "+(v.length()>40?v.substring(0,40):v));out.add(v);}
  return List.copyOf(out);
 }
 private static String activityKey(String key){return key!=null&&SlaCatalog.byKey(key)!=null?key:"CUSTOM";}

 @Transactional public RuleView create(RuleRequest r,Authentication auth){
  validate(r,Set.of(),null,rules::byName);
  var rule=new SlaRule();rule.set(activityKey(r.activityKey()),r.name().trim(),clean(r.startActions()),clean(r.stopActions()),r.targetDays(),r.warnPercent()==null?80:r.warnPercent(),r.active()==null||r.active(),r.escalationTeamId(),auth==null?null:auth.getName());
  rules.save(rule);audit.recordInTransaction(auth,"SLA_RULE_CREATED","SLA_RULE",rule.getId(),rule.getName()+" · "+rule.getTargetDays()+" Bankarbeitstage");
  return view(rule);
 }
 @Transactional public RuleView update(UUID id,RuleRequest r,Authentication auth){
  var rule=rules.findById(id).orElseThrow(()->new NoSuchElementException("SLA-Regel nicht gefunden."));
  validate(r,Set.of(),id,rules::byName);
  rule.set(activityKey(r.activityKey()),r.name().trim(),clean(r.startActions()),clean(r.stopActions()),r.targetDays(),r.warnPercent()==null?80:r.warnPercent(),r.active()==null||r.active(),r.escalationTeamId(),auth==null?null:auth.getName());
  rules.save(rule);audit.recordInTransaction(auth,"SLA_RULE_UPDATED","SLA_RULE",id,rule.getName()+" · "+rule.getTargetDays()+" Bankarbeitstage"+(rule.isActive()?"":" · inaktiv"));
  return view(rule);
 }
 @Transactional public void delete(UUID id,Authentication auth){
  var rule=rules.findById(id).orElseThrow(()->new NoSuchElementException("SLA-Regel nicht gefunden."));
  rules.delete(rule);audit.recordInTransaction(auth,"SLA_RULE_DELETED","SLA_RULE",id,rule.getName());
 }

 /** The clocks of all files for the active rules, for the list and the escalation: one query per rule instead of one per file. */
 @Transactional(readOnly=true) public Map<UUID,FileStatus> statusOfAll(LocalDate today){
  var fileIds=new HashSet<String>();lcs.findAll().forEach(lc->fileIds.add(lc.getId().toString()));
  var byFile=new HashMap<String,List<InstanceView>>();
  for(SlaRule rule:rules.findAll()){
   if(!rule.isActive())continue;
   var actions=new ArrayList<>(rule.startActions());actions.addAll(rule.stopActions());
   var events=new LinkedHashMap<String,List<SlaEvaluator.Event>>();
   jdbc.query("select entity_id, action, occurred_at from audit_event where tenant_id=? and action = any(?) and successful and occurred_at > ? order by entity_id, occurred_at, id",rs->{
     String id=rs.getString("entity_id");if(!fileIds.contains(id))return;
     events.computeIfAbsent(id,k->new ArrayList<>()).add(new SlaEvaluator.Event(rs.getString("action"),rs.getTimestamp("occurred_at").toLocalDateTime()));
    },TenantContext.currentId(),actions.toArray(new String[0]),java.sql.Timestamp.valueOf(today.minusDays(500).atStartOfDay()));
   events.forEach((id,list)->{for(var clock:SlaEvaluator.evaluate(list,rule.startActions(),rule.stopActions(),rule.getTargetDays(),rule.getWarnPercent(),today))byFile.computeIfAbsent(id,k->new ArrayList<>()).add(new InstanceView(rule.getId(),rule.getName(),rule.getActivityKey(),clock));});
  }
  var out=new HashMap<UUID,FileStatus>();
  byFile.forEach((id,list)->{
   var worst=SlaEvaluator.worstRunning(list.stream().map(InstanceView::clock).toList());
   list.sort(Comparator.comparing((InstanceView i)->i.clock().stoppedAt()!=null).thenComparing(i->i.clock().dueDate()));
   out.put(UUID.fromString(id),new FileStatus(worst==null?"NONE":worst.name(),List.copyOf(list)));
  });
  return out;
 }
 public record ListEntry(UUID lcId,String state){}
 /** Worst running state per file, only for files with a running clock that is tight or breached. */
 @Transactional(readOnly=true) public List<ListEntry> listStates(LocalDate today){
  var out=new ArrayList<ListEntry>();
  statusOfAll(today).forEach((id,status)->{if(status.state().equals("WARNING")||status.state().equals("BREACHED"))out.add(new ListEntry(id,status.state()));});
  return out;
 }
 /** Rules by id, for the escalation. */
 @Transactional(readOnly=true) public Map<UUID,SlaRule> activeRules(){var m=new HashMap<UUID,SlaRule>();rules.findAll().stream().filter(SlaRule::isActive).forEach(r->m.put(r.getId(),r));return m;}

 /** All clocks of the active rules for one file and the worst state of those still running ("NONE" when nothing runs). */
 @Transactional(readOnly=true) public FileStatus status(UUID lcId,LocalDate today){
  if(!lcs.existsById(lcId))throw new NoSuchElementException("Akte nicht gefunden.");
  var instances=new ArrayList<InstanceView>();var all=new ArrayList<SlaEvaluator.Instance>();
  for(SlaRule rule:rules.findAll()){
   if(!rule.isActive())continue;
   var actions=new ArrayList<>(rule.startActions());actions.addAll(rule.stopActions());
   var events=jdbc.query("select action, occurred_at from audit_event where tenant_id=? and entity_id=? and action = any(?) and successful order by occurred_at, id",
     (rs,i)->new SlaEvaluator.Event(rs.getString("action"),rs.getTimestamp("occurred_at").toLocalDateTime()),TenantContext.currentId(),lcId.toString(),actions.toArray(new String[0]));
   for(var clock:SlaEvaluator.evaluate(events,rule.startActions(),rule.stopActions(),rule.getTargetDays(),rule.getWarnPercent(),today)){instances.add(new InstanceView(rule.getId(),rule.getName(),rule.getActivityKey(),clock));all.add(clock);}
  }
  var worst=SlaEvaluator.worstRunning(all);
  instances.sort(Comparator.comparing((InstanceView i)->i.clock().stoppedAt()!=null).thenComparing(i->i.clock().dueDate()));
  return new FileStatus(worst==null?"NONE":worst.name(),List.copyOf(instances));
 }
}
