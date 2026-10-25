package de.ostms.lc.lc.service;
import de.ostms.lc.lc.domain.Team;
import de.ostms.lc.lc.repository.TeamRepository;
import de.ostms.lc.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Teams of a tenant. Members are usernames of active, assignable users. */
@Service
public class TeamService {
 public record Request(String name,String description,List<String> members,Boolean active){}
 public record View(UUID id,String name,String description,boolean active,List<String> members){}
 private final TeamRepository teams;private final UserService users;
 public TeamService(TeamRepository t,UserService u){teams=t;users=u;}

 static View view(Team t){return new View(t.getId(),t.getName(),t.getDescription(),t.isActive(),List.copyOf(t.getMembers()));}
 @Transactional(readOnly=true) public List<View> all(){return teams.all().stream().map(TeamService::view).toList();}
 @Transactional(readOnly=true) public List<Team> mine(String username){return teams.all().stream().filter(t->t.isActive()&&t.hasMember(username)).toList();}
 @Transactional(readOnly=true) public Team one(UUID id){return teams.findById(id).orElseThrow(()->new NoSuchElementException("Team nicht gefunden."));}

 @Transactional public View create(Request r){
  String name=name(r);
  if(teams.nameTaken(name,null))throw new IllegalArgumentException("Ein Team mit diesem Namen gibt es bereits.");
  var team=new Team(name,clean(r.description()));
  team.setMembers(members(r.members()));
  return view(teams.save(team));
 }
 @Transactional public View update(UUID id,Request r){
  var team=one(id);String name=name(r);
  if(teams.nameTaken(name,id))throw new IllegalArgumentException("Ein Team mit diesem Namen gibt es bereits.");
  team.setName(name);team.setDescription(clean(r.description()));team.setMembers(members(r.members()));
  if(r.active()!=null)team.setActive(r.active());
  return view(teams.save(team));
 }
 private static String name(Request r){
  if(r==null||r.name()==null||r.name().isBlank())throw new IllegalArgumentException("Bitte einen Teamnamen angeben.");
  String n=r.name().trim();if(n.length()>100)throw new IllegalArgumentException("Der Teamname ist zu lang.");return n;
 }
 private static String clean(String v){return v==null||v.isBlank()?null:(v.trim().length()>300?v.trim().substring(0,300):v.trim());}
 private List<String> members(List<String> requested){
  var result=new ArrayList<String>();
  if(requested!=null)for(String raw:requested){
   String u=raw==null?"":raw.trim();
   if(u.isEmpty()||result.stream().anyMatch(x->x.equalsIgnoreCase(u)))continue;
   if(!users.isAssignable(u))throw new IllegalArgumentException("„"+u+"“ ist kein aktiver Nutzer dieses Mandanten.");
   result.add(u);
  }
  return result;
 }
}
