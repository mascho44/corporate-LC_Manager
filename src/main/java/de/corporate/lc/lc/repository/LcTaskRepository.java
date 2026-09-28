package de.corporate.lc.lc.repository;
import de.corporate.lc.lc.domain.LcTask;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface LcTaskRepository extends JpaRepository<LcTask,UUID>{List<LcTask> findByLetterOfCreditIdOrderByCompletedAscDueDateAscCreatedAtDesc(UUID lcId);List<LcTask> findByCompletedFalseOrderByDueDateAscCreatedAtAsc();}
