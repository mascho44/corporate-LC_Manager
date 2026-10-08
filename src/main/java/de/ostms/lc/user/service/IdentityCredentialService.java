package de.ostms.lc.user.service;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;

/** Self-service identity credentials, not tenant-member administration. */
@Service public class IdentityCredentialService {
 private final AppUserRepository users;
 private final PasswordEncoder encoder;
 public IdentityCredentialService(AppUserRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}
 @Transactional public void changeOwnPassword(String authenticatedUsername,String currentPassword,String newPassword){
  var user=users.findByUsernameIgnoreCase(authenticatedUsername).orElseThrow(()->new NoSuchElementException("Benutzer nicht gefunden"));
  // Retained bootstrap boundary until cross-tenant identity self-service is enabled.
  TenantContext.require(user.getTenantId());
  if(!encoder.matches(currentPassword,user.getPasswordHash()))throw new IllegalArgumentException("Das aktuelle Passwort ist falsch.");
  UserService.validatePassword(newPassword);
  if(encoder.matches(newPassword,user.getPasswordHash()))throw new IllegalArgumentException("Das neue Passwort muss sich vom bisherigen unterscheiden.");
  user.setPasswordHash(encoder.encode(newPassword));
 }
}
