package de.ostms.lc.lc.domain;
import de.ostms.lc.tenant.domain.TenantOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name="team")
public class Team extends TenantOwnedEntity {
 @Id private UUID id=UUID.randomUUID();
 @Column(nullable=false,length=100) private String name;
 @Column(length=300) private String description;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="team_member",joinColumns=@JoinColumn(name="team_id")) @Column(name="username",length=100)
 private Set<String> members=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
 protected Team(){}
 public Team(String name,String description){this.name=name;this.description=description;}
 public UUID getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
 public String getDescription(){return description;} public void setDescription(String v){description=v;}
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
 public Set<String> getMembers(){return members;}
 public void setMembers(Collection<String> usernames){members.clear();if(usernames!=null)members.addAll(usernames);}
 public boolean hasMember(String username){return username!=null&&members.contains(username);}
}
