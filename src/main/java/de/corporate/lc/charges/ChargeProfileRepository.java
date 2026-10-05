package de.corporate.lc.charges;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ChargeProfileRepository extends JpaRepository<ChargeProfile,UUID>{}
