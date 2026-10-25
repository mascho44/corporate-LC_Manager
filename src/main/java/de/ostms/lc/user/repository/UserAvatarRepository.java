package de.ostms.lc.user.repository;
import de.ostms.lc.user.domain.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> { }
