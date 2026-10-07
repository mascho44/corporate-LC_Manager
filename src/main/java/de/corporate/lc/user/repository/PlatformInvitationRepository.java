package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.PlatformInvitation;
import org.springframework.data.jpa.repository.*;
import java.util.*;
/** Internal global token store. Only the platform and anonymous token-completion services use it. */
public interface PlatformInvitationRepository extends JpaRepository<PlatformInvitation,String> {
 Optional<PlatformInvitation> findByUserId(UUID userId);
 @Modifying @org.springframework.data.jpa.repository.Query("delete from PlatformInvitation i where i.userId=:id")
 void deleteForUser(@org.springframework.data.repository.query.Param("id") UUID id);
}
