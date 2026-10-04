package de.corporate.lc.document.service;

import de.corporate.lc.document.domain.DocumentApprovalThreshold;
import de.corporate.lc.document.repository.DocumentApprovalThresholdRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentApprovalPolicyServiceTest {
    @Test void selectsTheHighestMatchingCurrencyThreshold() {
        DocumentApprovalThreshold base = threshold("EUR", "0", 1);
        DocumentApprovalThreshold high = threshold("EUR", "100000", 2);
        DocumentApprovalThreshold usd = threshold("USD", "0", 3);
        DocumentApprovalThresholdRepository repository = mock(DocumentApprovalThresholdRepository.class);
        when(repository.findAllByOrderByCurrencyAscMinimumAmountAsc()).thenReturn(List.of(base, high, usd));
        DocumentApprovalPolicyService service = new DocumentApprovalPolicyService(repository);

        assertThat(service.requiredApprovals(new BigDecimal("99999.99"), "EUR")).isEqualTo(1);
        assertThat(service.requiredApprovals(new BigDecimal("100000"), "eur")).isEqualTo(2);
        assertThat(service.requiredApprovals(new BigDecimal("1000000"), "USD")).isEqualTo(3);
        assertThat(service.requiredApprovals(new BigDecimal("1000000"), "GBP")).isEqualTo(1);
    }

    private DocumentApprovalThreshold threshold(String currency, String amount, int approvals) {
        DocumentApprovalThreshold threshold = new DocumentApprovalThreshold();
        threshold.setCurrency(currency);
        threshold.setMinimumAmount(new BigDecimal(amount));
        threshold.setRequiredApprovals(approvals);
        return threshold;
    }
}
