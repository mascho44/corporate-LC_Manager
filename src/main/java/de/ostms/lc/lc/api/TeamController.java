package de.ostms.lc.lc.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.service.GroupInboxService;
import de.ostms.lc.lc.service.TeamService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
public class TeamController {
 private final TeamService teams;private final GroupInboxService inbox;private final AuditService audit;
 public TeamController(TeamService t,GroupInboxService i,AuditService a){teams=t;inbox=i;audit=a;}

 @GetMapping("/api/teams") public List<TeamService.View> all(){return teams.all();}
 @GetMapping("/api/teams/mine") public List<TeamService.View> mine(Authentication auth){return teams.mine(auth.getName()).stream().map(t->new TeamService.View(t.getId(),t.getName(),t.getDescription(),t.isActive(),List.copyOf(t.getMembers()))).toList();}
 @PostMapping("/api/teams") public TeamService.View create(@RequestBody TeamService.Request request,Authentication auth){
  var v=teams.create(request);audit.record(auth,"TEAM_CREATED","TEAM",v.id(),v.name()+" · "+v.members().size()+" Mitglieder");return v;
 }
 @PutMapping("/api/teams/{id}") public TeamService.View update(@PathVariable UUID id,@RequestBody TeamService.Request request,Authentication auth){
  var v=teams.update(id,request);audit.record(auth,"TEAM_UPDATED","TEAM",v.id(),v.name()+" · "+v.members().size()+" Mitglieder"+(v.active()?"":" · inaktiv"));return v;
 }
 @GetMapping("/api/tasks/inbox") public List<GroupInboxService.Item> inbox(Authentication auth){return inbox.inbox(auth.getName());}
 @PostMapping("/api/tasks/{id}/claim") public LcTask claim(@PathVariable UUID id,Authentication auth){
  var task=inbox.claim(id,auth.getName());audit.record(auth,"TASK_CLAIMED","LETTER_OF_CREDIT",task.getLetterOfCreditId(),task.getTitle());return task;
 }
 @PostMapping("/api/tasks/{id}/release") public LcTask release(@PathVariable UUID id,Authentication auth){
  boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("PERM_USER_MANAGE"));
  var task=inbox.release(id,auth.getName(),admin);audit.record(auth,"TASK_RELEASED","LETTER_OF_CREDIT",task.getLetterOfCreditId(),task.getTitle());return task;
 }
}
