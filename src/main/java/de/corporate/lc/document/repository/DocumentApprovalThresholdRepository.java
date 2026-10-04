package de.corporate.lc.document.repository;

import de.corporate.lc.document.domain.DocumentApprovalThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentApprovalThresholdRepository extends JpaRepository<DocumentApprovalThreshold, UUID> {
    List<DocumentApprovalThreshold> findAllByOrderByCurrencyAscMinimumAmountAsc();
}
