package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.LcTask;
import de.ostms.lc.lc.domain.Team;
import de.ostms.lc.lc.repository.LcTaskRepository;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Group inbox: open tasks handed to the user's teams. A member claims a task (atomically) or hands it back. */
@Service
public class GroupInboxService {
 public record Item(UUID taskId,UUID lcId,String lcReference,String title,LocalDate dueDate,UUID teamId,String teamName,String assignedTo,LocalDateTime claimedAt,String createdBy){}
 private final LcTaskRepository tasks;private final TeamService teams;private final LetterOfCreditRepository lcs;
 public GroupInboxService(LcTaskRepository t,TeamService te,LetterOfCreditRepository l){tasks=t;teams=te;lcs=l;}

 /** Open tasks of all teams the user belongs to: unclaimed ones first, then what colleagues are working on. */
 @Transactional(readOnly=true) public List<Item> inbox(String username){
  var mine=teams.mine(username);
  if(mine.isEmpty())return List.of();
  var byId=new HashMap<UUID,Team>();mine.forEach(t->byId.put(t.getId(),t));
  var items=new ArrayList<Item>();
  for(LcTask task:tasks.openForTeams(byId.keySet())){
   var lc=lcs.findById(task.getLetterOfCreditId()).orElse(null);
   items.add(new Item(task.getId(),task.getLetterOfCreditId(),lc==null?null:lc.getReference(),task.getTitle(),task.getDueDate(),task.getTeamId(),byId.get(task.getTeamId()).getName(),task.getAssignedTo(),task.getClaimedAt(),task.getCreatedBy()));
  }
  items.sort(Comparator.comparing((Item i)->i.assignedTo()!=null).thenComparing(i->i.dueDate()==null?LocalDate.MAX:i.dueDate()));
  return items;
 }

 @Transactional public LcTask claim(UUID taskId,String username){
  var task=tasks.findById(taskId).orElseThrow(()->new NoSuchElementException("Auftrag nicht gefunden."));
  var team=requireMember(task,username);
  if(task.isCompleted())throw new IllegalStateException("Der Auftrag ist bereits erledigt.");
  if(tasks.claim(taskId,username,LocalDateTime.now())==0){
   var current=tasks.findById(taskId).orElseThrow();
   throw new IllegalStateException(current.getAssignedTo()==null?"Der Auftrag kann nicht übernommen werden.":"Der Auftrag wurde bereits von "+current.getAssignedTo()+" übernommen.");
  }
  return tasks.findById(taskId).orElseThrow();
 }

 /** The claimer (or a team member managing users) puts the task back into the inbox. */
 @Transactional public LcTask release(UUID taskId,String username,boolean administrator){
  var task=tasks.findById(taskId).orElseThrow(()->new NoSuchElementException("Auftrag nicht gefunden."));
  requireTeam(task);
  if(task.getAssignedTo()==null)throw new IllegalStateException("Der Auftrag liegt bereits in der Gruppeninbox.");
  if(!task.getAssignedTo().equalsIgnoreCase(username)&&!administrator)throw new org.springframework.security.access.AccessDeniedException("Nur die übernehmende Person kann den Auftrag zurückgeben.");
  task.setAssignedTo(null);task.setClaimedAt(null);
  return tasks.save(task);
 }

 private Team requireTeam(LcTask task){
  if(task.getTeamId()==null)throw new IllegalArgumentException("Der Auftrag gehört keinem Team.");
  return teams.one(task.getTeamId());
 }
 private Team requireMember(LcTask task,String username){
  var team=requireTeam(task);
  if(!team.isActive()||!team.hasMember(username))throw new org.springframework.security.access.AccessDeniedException("Sie gehören nicht zum Team dieses Auftrags.");
  return team;
 }
}
