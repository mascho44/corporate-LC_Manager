package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.domain.Workflow;
import de.ostms.lc.lc.repository.LcTaskRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.repository.WorkflowRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Runs fixed workflow templates as chains of team tasks. Completing a step creates the next one; four-eyes steps need a different person than the step before. */
@Service
public class WorkflowService {
 public record StepView(int no,String key,String title,boolean fourEyes,String state,String completedBy,UUID taskId,String assignedTo,java.time.LocalDate dueDate){}
 public record View(UUID id,UUID lcId,String template,String templateTitle,String status,int currentStep,int stepCount,String startedBy,LocalDateTime startedAt,LocalDateTime completedAt,List<StepView> steps){}
 public record StartRequest(String template,UUID teamId,UUID approvalTeamId){}
 private final WorkflowRepository workflows;private final LcTaskRepository tasks;private final TeamService teams;private final LetterOfCreditRepository lcs;
 public WorkflowService(WorkflowRepository w,LcTaskRepository t,TeamService te,LetterOfCreditRepository l){workflows=w;tasks=t;teams=te;lcs=l;}

 @Transactional public Workflow start(UUID lcId,StartRequest r,String user){
  if(r==null)throw new IllegalArgumentException("Angaben fehlen.");
  lcs.findById(lcId).orElseThrow(()->new NoSuchElementException("Akte nicht gefunden."));
  var template=WorkflowTemplates.get(r.template());
  if(r.teamId()==null)throw new IllegalArgumentException("Bitte ein Team wählen.");
  var team=teams.one(r.teamId());if(!team.isActive())throw new IllegalArgumentException("Das Team ist nicht aktiv.");
  if(r.approvalTeamId()!=null){var approval=teams.one(r.approvalTeamId());if(!approval.isActive())throw new IllegalArgumentException("Das Freigabeteam ist nicht aktiv.");}
  if(workflows.running(lcId,template.id()))throw new IllegalStateException("Für diese Akte läuft „"+template.title()+"“ bereits.");
  var workflow=workflows.save(new Workflow(lcId,template.id(),r.teamId(),r.approvalTeamId(),user));
  createStep(workflow,template,1,user);
  return workflow;
 }

 /** Called when a workflow task is completed by a user; returns true when the workflow is handled here. */
 @Transactional public void completeStep(LcTask task,String user){
  var workflow=workflows.findById(task.getWorkflowId()).orElseThrow(()->new NoSuchElementException("Workflow nicht gefunden."));
  if(!workflow.getStatus().equals("RUNNING"))throw new IllegalStateException("Der Workflow ist nicht mehr aktiv.");
  if(task.isCompleted())throw new IllegalStateException("Dieser Schritt ist bereits erledigt.");
  if(task.getStepNo()==null||task.getStepNo()!=workflow.getCurrentStep())throw new IllegalStateException("Dies ist nicht der aktuelle Schritt.");
  var team=teams.one(task.getTeamId());
  if(!team.hasMember(user))throw new AccessDeniedException("Nur Mitglieder des Teams „"+team.getName()+"“ können diesen Schritt abschließen.");
  if(task.getAssignedTo()!=null&&!task.getAssignedTo().equalsIgnoreCase(user))throw new AccessDeniedException("Der Schritt wurde von "+task.getAssignedTo()+" übernommen.");
  if(task.isFourEyes()){
   var previous=tasks.forWorkflow(workflow.getId()).stream().filter(t->t.getStepNo()!=null&&t.getStepNo()==task.getStepNo()-1).findFirst();
   if(previous.isPresent()&&previous.get().getCompletedBy()!=null&&previous.get().getCompletedBy().equalsIgnoreCase(user))
    throw new AccessDeniedException("Vier-Augen-Prinzip: Dieser Schritt muss von einer anderen Person als dem vorherigen Schritt ("+previous.get().getCompletedBy()+") abgeschlossen werden.");
  }
  task.setAssignedTo(user);if(task.getClaimedAt()==null)task.setClaimedAt(LocalDateTime.now());
  task.setCompleted(true);task.setCompletedAt(LocalDateTime.now());task.setCompletedBy(user);tasks.save(task);
  var template=WorkflowTemplates.get(workflow.getTemplate());
  if(task.getStepNo()>=template.steps().size()){workflow.finish("DONE");workflows.save(workflow);}
  else{workflow.advanceTo(task.getStepNo()+1);workflows.save(workflow);createStep(workflow,template,task.getStepNo()+1,user);}
 }

 /** Cancels a running workflow and removes its open step; completed steps stay as history. */
 @Transactional public Workflow cancel(UUID workflowId,String user){
  var workflow=workflows.findById(workflowId).orElseThrow(()->new NoSuchElementException("Workflow nicht gefunden."));
  if(!workflow.getStatus().equals("RUNNING"))throw new IllegalStateException("Der Workflow ist nicht mehr aktiv.");
  tasks.forWorkflow(workflowId).stream().filter(t->!t.isCompleted()).forEach(tasks::delete);
  workflow.finish("CANCELLED");return workflows.save(workflow);
 }

 @Transactional(readOnly=true) public List<View> forLc(UUID lcId){
  lcs.findById(lcId).orElseThrow(()->new NoSuchElementException("Akte nicht gefunden."));
  return workflows.forLc(lcId).stream().map(this::view).toList();
 }
 @Transactional(readOnly=true) public Optional<Workflow> find(UUID id){return workflows.findById(id);}

 private void createStep(Workflow workflow,WorkflowTemplates.Template template,int no,String user){
  var step=template.steps().get(no-1);
  var task=new LcTask();
  task.setLetterOfCreditId(workflow.getLcId());
  task.setTitle("["+template.title()+"] "+step.title());
  task.setDueDate(LocalDate.now().plusDays(step.dueDays()));
  task.setCreatedBy(user);
  task.setTeamId(step.approvalTeam()&&workflow.getApprovalTeamId()!=null?workflow.getApprovalTeamId():workflow.getTeamId());
  task.setWorkflowId(workflow.getId());task.setStepNo(no);task.setStepKey(step.key());task.setFourEyes(step.fourEyes());
  tasks.save(task);
 }

 private View view(Workflow w){
  var template=WorkflowTemplates.get(w.getTemplate());
  var byStep=new HashMap<Integer,LcTask>();tasks.forWorkflow(w.getId()).forEach(t->byStep.put(t.getStepNo(),t));
  var steps=new ArrayList<StepView>();
  for(int i=1;i<=template.steps().size();i++){
   var def=template.steps().get(i-1);var t=byStep.get(i);
   String state=t==null?(w.getStatus().equals("CANCELLED")?"CANCELLED":"PENDING"):t.isCompleted()?"DONE":"ACTIVE";
   steps.add(new StepView(i,def.key(),def.title(),def.fourEyes(),state,t==null?null:t.getCompletedBy(),t==null?null:t.getId(),t==null?null:t.getAssignedTo(),t==null?null:t.getDueDate()));
  }
  return new View(w.getId(),w.getLcId(),w.getTemplate(),template.title(),w.getStatus(),w.getCurrentStep(),template.steps().size(),w.getStartedBy(),w.getStartedAt(),w.getCompletedAt(),List.copyOf(steps));
 }
}
