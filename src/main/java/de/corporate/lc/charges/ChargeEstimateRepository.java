package de.corporate.lc.charges;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ChargeEstimateRepository extends JpaRepository<ChargeEstimate,UUID>{List<ChargeEstimate> findByLcIdOrderByCreatedAtDesc(UUID lcId);}
