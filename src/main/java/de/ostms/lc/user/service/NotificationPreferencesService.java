package de.ostms.lc.user.service;

import de.ostms.lc.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;

/** The mails a person has switched on for themselves (today: the daily deadline summary). */
@Service
public class NotificationPreferencesService {
 public record Preferences(boolean deadlineDigest,boolean hasEmail) {}
 private final AppUserRepository users;
 public NotificationPreferencesService(AppUserRepository users){this.users=users;}
 @Transactional(readOnly=true) public Preferences get(String username){
  var user=users.findByUsernameIgnoreCase(username).orElseThrow(()->new NoSuchElementException("Benutzer nicht gefunden."));
  return new Preferences(user.isDeadlineDigest(),user.getEmail()!=null&&!user.getEmail().isBlank());
 }
 @Transactional public Preferences update(String username,boolean deadlineDigest){
  var user=users.findByUsernameIgnoreCase(username).orElseThrow(()->new NoSuchElementException("Benutzer nicht gefunden."));
  if(deadlineDigest&&(user.getEmail()==null||user.getEmail().isBlank()))throw new IllegalArgumentException("Bitte zuerst im Profil eine E-Mail-Adresse hinterlegen.");
  try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(user.getTenantId())){user.setDeadlineDigest(deadlineDigest);users.saveAndFlush(user);}
  return get(username);
 }
}
