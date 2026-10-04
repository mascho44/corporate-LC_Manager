package de.corporate.lc.user.repository;
import de.corporate.lc.user.domain.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> { }
