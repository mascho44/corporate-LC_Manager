package de.ostms.lc.lc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LetterOfCreditCopyTest {
    private final LetterOfCreditRepository repo = mock(LetterOfCreditRepository.class);
    private final LetterOfCreditService service = new LetterOfCreditService(repo, null, null);
    private final UUID id = UUID.randomUUID();

    private LetterOfCredit source() {
        var lc = new LetterOfCredit();
        lc.setReference("LC-1"); lc.setApplicant("BUYER LLC"); lc.setBeneficiary("SELLER GMBH"); lc.setIssuingBank("TESTBANK"); lc.setAmount(new BigDecimal("1000.00")); lc.setCurrency("EUR");
        lc.setExpiryDate(LocalDate.of(2026, 12, 1)); lc.setLatestShipmentDate(LocalDate.of(2026, 11, 1)); lc.setAssignedTo("anna"); lc.setCompanyId(3);
        lc.setRequiredDocuments(List.of("COMMERCIAL INVOICE IN 3 COPIES"));
        lc.setRawMessage(":20:LC-1\n:31D:261201HAMBURG\n:32B:EUR1000,00\n:44C:261101\n:45A:2 UNITS\nCIF HAMBURG");
        when(repo.findById(id)).thenReturn(Optional.of(lc));
        return lc;
    }

    @Test void theCopyKeepsTheTermsButNotTheDatesOrTheAssignment() {
        source();
        when(repo.existsByReference(any())).thenReturn(false);
        when(repo.save(any(LetterOfCredit.class))).thenAnswer(call -> call.getArgument(0));
        var copy = service.copyOf(id, "  LC-1-K2 ");
        assertThat(copy.getReference()).isEqualTo("LC-1-K2");
        assertThat(copy.getApplicant()).isEqualTo("BUYER LLC");
        assertThat(copy.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(copy.getCompanyId()).isEqualTo(3);
        assertThat(copy.getRequiredDocuments()).containsExactly("COMMERCIAL INVOICE IN 3 COPIES");
        assertThat(copy.getExpiryDate()).isNull();
        assertThat(copy.getLatestShipmentDate()).isNull();
        assertThat(copy.getAssignedTo()).isNull();
        assertThat(copy.getRawMessage()).isEqualTo(":20:LC-1-K2\n:32B:EUR1000,00\n:45A:2 UNITS\nCIF HAMBURG");
        copy.getRequiredDocuments().add("PACKING LIST");
        assertThat(repo.findById(id).orElseThrow().getRequiredDocuments()).hasSize(1);
    }

    @Test void aMissingOrTakenReferenceIsRefused() {
        source();
        when(repo.existsByReference("LC-1")).thenReturn(true);
        assertThatThrownBy(() -> service.copyOf(id, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.copyOf(id, "LC-1")).hasMessageContaining("existiert bereits");
    }
}
