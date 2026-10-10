package de.ostms.lc.lc.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.service.AutoTaskService;
import de.ostms.lc.lc.service.GroupInboxService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
public class AutomationController {
 private final AutoTaskService autoTasks;private final GroupInboxService inbox;private final AuditService audit;
 public AutomationController(AutoTaskService a,GroupInboxService i,AuditService au){autoTasks=a;inbox=i;audit=au;}
 @GetMapping("/api/automation-rules") public List<AutoTaskService.RuleView> rules(){return autoTasks.rules();}
 @PutMapping("/api/automation-rules") public AutoTaskService.RuleView update(@RequestBody AutoTaskService.RuleRequest request,Authentication auth){
  var v=autoTasks.update(request);
  audit.record(auth,"AUTOMATION_RULE_UPDATED","AUTOMATION_RULE",null,v.trigger()+" · "+(v.enabled()?"an":"aus")+(v.teamName()==null?"":" · "+v.teamName())+(v.trigger().equals("DEADLINE")?" · "+v.leadDays()+" Tage":""));
  return v;
 }
 @PostMapping("/api/tasks/{id}/complete") public LcTask complete(@PathVariable UUID id,Authentication auth){
  var task=inbox.complete(id,auth.getName());audit.record(auth,"TASK_COMPLETED","TASK",id,task.getTitle());return task;
 }
}
