package de.ostms.lc.sla;

import de.ostms.lc.lc.service.AutoTaskService;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

/** Creates one team task in the group inbox for every breached SLA clock of a rule with an escalation team (once per clock). */
@Service
public class SlaEscalationService {
 private final SlaService sla;private final AutoTaskService tasks;private final LetterOfCreditRepository lcs;
 public SlaEscalationService(SlaService sla,AutoTaskService tasks,LetterOfCreditRepository lcs){this.sla=sla;this.tasks=tasks;this.lcs=lcs;}

 @Transactional public int escalate(LocalDate today){
  var rules=sla.activeRules();int created=0;
  var references=new java.util.HashMap<UUID,String>();lcs.findAll().forEach(lc->references.put(lc.getId(),lc.getReference()));
  for(var entry:sla.statusOfAll(today).entrySet()){
   for(var item:entry.getValue().instances()){
    var rule=rules.get(item.ruleId());
    if(rule==null||rule.getEscalationTeamId()==null||item.clock().stoppedAt()!=null||item.clock().state()!=SlaEvaluator.State.BREACHED)continue;
    String key="SLA:"+rule.getId()+":"+entry.getKey()+":"+item.clock().startedAt().toLocalDate();
    String title="SLA verletzt: "+references.getOrDefault(entry.getKey(),"Akte")+" – "+rule.getName()+" (fällig "+item.clock().dueDate()+")";
    if(tasks.createForTeam(rule.getEscalationTeamId(),"AUTO_SLA",title,entry.getKey(),key,today).isPresent())created++;
   }
  }
  for(var clock:sla.inboxStatus(today)){
   var rule=rules.get(clock.ruleId());
   if(rule==null||rule.getEscalationTeamId()==null||!"BREACHED".equals(clock.state()))continue;
   if(tasks.createForTeam(rule.getEscalationTeamId(),"AUTO_SLA","SLA verletzt: Posteingang – "+clock.filename()+" (fällig "+clock.dueDate()+")",null,"SLA:INBOX:"+rule.getId()+":"+clock.itemId(),today).isPresent())created++;
  }
  return created;
 }
}
