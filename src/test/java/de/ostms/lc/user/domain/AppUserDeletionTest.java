package de.ostms.lc.user.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppUserDeletionTest {
 @Test void aDeletedAccountIsAnEmptyShellThatKeepsTheUserName(){
  var user=new AppUser();user.setUsername("anna");user.setDisplayName("Anna Beispiel");user.setEmail("anna@example.org");user.setPasswordHash("hash");user.setPlatformAdministrator(true);
  assertFalse(user.isDeleted());
  user.markDeleted();
  assertTrue(user.isDeleted());assertFalse(user.isActive());assertFalse(user.isPlatformAdministrator());assertFalse(user.isInvitationPending());assertFalse(user.isTotpEnabled());
  assertEquals("anna",user.getUsername());assertEquals("Gelöschter Benutzer",user.getDisplayName());assertNull(user.getEmail());assertTrue(user.getPasswordHash().startsWith("{deleted}"));
 }
}
