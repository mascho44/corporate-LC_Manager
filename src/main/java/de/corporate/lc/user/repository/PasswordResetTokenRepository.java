package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,String> {
    @Modifying @Query("delete from PasswordResetToken t where t.userId=:id")
    void deleteForUser(@Param("id") UUID id);
    @Modifying @Query("delete from PasswordResetToken t where t.expiresAt<:now")
    void deleteExpired(@Param("now") Instant now);
}
