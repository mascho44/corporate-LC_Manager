package de.ostms.lc.lc.api;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.lc.service.WorkflowService;
import de.ostms.lc.lc.service.WorkflowTemplates;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RestController
public class WorkflowController {
 private final WorkflowService service;private final AuditService audit;
 public WorkflowController(WorkflowService s,AuditService a){service=s;audit=a;}
 @GetMapping("/api/workflow-templates") public Collection<WorkflowTemplates.Template> templates(){return WorkflowTemplates.all();}
 @GetMapping("/api/lcs/{lcId}/workflows") public List<WorkflowService.View> forLc(@PathVariable UUID lcId){return service.forLc(lcId);}
 @PostMapping("/api/lcs/{lcId}/workflows") public List<WorkflowService.View> start(@PathVariable UUID lcId,@RequestBody WorkflowService.StartRequest request,Authentication auth){
  var w=service.start(lcId,request,auth.getName());
  audit.record(auth,"WORKFLOW_STARTED","LETTER_OF_CREDIT",lcId,WorkflowTemplates.get(w.getTemplate()).title());
  return service.forLc(lcId);
 }
 @PostMapping("/api/workflows/{id}/cancel") public List<WorkflowService.View> cancel(@PathVariable UUID id,Authentication auth){
  var w=service.cancel(id,auth.getName());
  audit.record(auth,"WORKFLOW_CANCELLED","LETTER_OF_CREDIT",w.getLcId(),WorkflowTemplates.get(w.getTemplate()).title());
  return service.forLc(w.getLcId());
 }
}
