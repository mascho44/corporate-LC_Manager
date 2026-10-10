package de.ostms.lc.lc.service;
import de.ostms.lc.lc.api.LcTaskRequest;import de.ostms.lc.lc.domain.LcTask;import de.ostms.lc.lc.repository.LcTaskRepository;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.LocalDateTime;import java.util.*;
@Service public class LcTaskService {private final LcTaskRepository repo;private final LetterOfCreditService lcs;public LcTaskService(LcTaskRepository r,LetterOfCreditService l){repo=r;lcs=l;}public List<LcTask> forLc(UUID id){lcs.one(id);return repo.findByLetterOfCreditIdOrderByCompletedAscDueDateAscCreatedAtDesc(id);}public List<LcTask> open(){return repo.findByCompletedFalseOrderByDueDateAscCreatedAtAsc();}@Transactional public LcTask create(UUID lcId,LcTaskRequest request,String username){lcs.one(lcId);LcTask task=new LcTask();task.setLetterOfCreditId(lcId);task.setTitle(request.title().trim());task.setAssignedTo(clean(request.assignedTo()));applyTeam(task,request);task.setDueDate(request.dueDate());task.setCreatedBy(username);return repo.save(task);}@org.springframework.beans.factory.annotation.Autowired(required=false) private WorkflowService workflowService;
 @Transactional public LcTask complete(UUID lcId,UUID taskId,boolean completed,String user){
  LcTask task=one(lcId,taskId);
  if(task.getWorkflowId()!=null){
   if(!completed)throw new IllegalStateException("Workflow-Schritte lassen sich nicht wieder öffnen.");
   if(workflowService==null)throw new IllegalStateException("Workflows sind nicht verfügbar.");
   workflowService.completeStep(task,user);return task;
  }
  return complete(lcId,taskId,completed);
 }
 @Transactional public LcTask complete(UUID lcId,UUID taskId,boolean completed){LcTask task=one(lcId,taskId);if(task.getWorkflowId()!=null)throw new IllegalStateException("Workflow-Schritte werden über den Workflow abgeschlossen.");task.setCompleted(completed);task.setCompletedAt(completed?LocalDateTime.now():null);return task;}@Transactional public void delete(UUID lcId,UUID taskId){var task=one(lcId,taskId);if(task.getWorkflowId()!=null)throw new IllegalStateException("Workflow-Schritte lassen sich nur über das Abbrechen des Workflows entfernen.");repo.delete(task);}@org.springframework.beans.factory.annotation.Autowired(required=false) private TeamService teams;
 private void applyTeam(LcTask task,LcTaskRequest request){
  if(request.teamId()==null)return;
  if(teams==null)throw new IllegalStateException("Teams sind nicht verfügbar.");
  var team=teams.one(request.teamId());
  if(!team.isActive())throw new IllegalArgumentException("Das Team ist nicht aktiv.");
  if(task.getAssignedTo()!=null&&!team.hasMember(task.getAssignedTo()))throw new IllegalArgumentException("Der Bearbeiter gehört nicht zum gewählten Team.");
  task.setTeamId(team.getId());
  if(task.getAssignedTo()!=null)task.setClaimedAt(LocalDateTime.now());
 }
 private LcTask one(UUID lcId,UUID id){return repo.findById(id).filter(task->lcId.equals(task.getLetterOfCreditId())).orElseThrow(()->new NoSuchElementException("Aufgabe nicht gefunden"));}private String clean(String value){return value==null||value.isBlank()?null:value.trim();}}
