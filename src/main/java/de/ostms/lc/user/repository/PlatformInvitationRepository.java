package de.ostms.lc.user.repository;
import de.ostms.lc.user.domain.PlatformInvitation;
import org.springframework.data.jpa.repository.*;
import java.util.*;
/** Internal global token store. Only the platform and anonymous token-completion services use it. */
public interface PlatformInvitationRepository extends JpaRepository<PlatformInvitation,String> {
 @org.springframework.data.jpa.repository.Query("select i from PlatformInvitation i where i.encryptedMailPayload is not null and i.nextDeliveryAttempt<=:now and i.expiresAt>:now and i.deliveryAttempts<5 order by i.nextDeliveryAttempt,i.tokenHash")
 List<PlatformInvitation> findDue(@org.springframework.data.repository.query.Param("now") java.time.Instant now,org.springframework.data.domain.Pageable limit);
 @Modifying @org.springframework.data.jpa.repository.Query("update PlatformInvitation i set i.encryptedMailPayload=null,i.nextDeliveryAttempt=null where i.encryptedMailPayload is not null and i.expiresAt<=:now")
 void clearExpiredMail(@org.springframework.data.repository.query.Param("now") java.time.Instant now);
 Optional<PlatformInvitation> findByUserId(UUID userId);
 @Modifying @org.springframework.data.jpa.repository.Query("delete from PlatformInvitation i where i.userId=:id")
 void deleteForUser(@org.springframework.data.repository.query.Param("id") UUID id);
}
