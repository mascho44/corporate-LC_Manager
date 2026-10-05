package de.corporate.lc.training.repository;import de.corporate.lc.training.domain.TrainingSession;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;public interface TrainingSessionRepository extends JpaRepository<TrainingSession,UUID>{List<TrainingSession> findTop100ByOrderByCreatedAtDesc();
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select s from TrainingSession s where s.id = :id")
 Optional<TrainingSession> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
