package de.ostms.lc.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.ostms.lc.company.domain.CompanyProfile;
import de.ostms.lc.company.repository.CompanyProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanyLetterheadTest {
    private final CompanyProfileRepository repository = mock(CompanyProfileRepository.class);
    private final CompanyProfileService service = new CompanyProfileService(repository);

    @Test void withoutAnyCompanyTheSellerOfTheCreditNamesTheLetterhead() {
        when(repository.findDefault()).thenReturn(Optional.empty());
        var letterhead = service.profileForLetterhead(null, "BEISPIEL MASCHINENBAU GMBH\nINDUSTRIESTRASSE 10\n70565 STUTTGART");
        assertThat(letterhead.getLegalName()).isEqualTo("BEISPIEL MASCHINENBAU GMBH");
        assertThat(letterhead.getAddressLine()).isNull();
        assertThat(service.hasLetterhead(null)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test void aMissingCompanyWithoutSellerGivesAnEmptyLetterhead() {
        when(repository.findById(7)).thenReturn(Optional.empty());
        assertThat(service.profileForLetterhead(7, null).getLegalName()).isNull();
        assertThat(service.profileForLetterhead(7, "  ").getLegalName()).isNull();
    }

    @Test void anExistingCompanyIsUsedAsIs() {
        var company = new CompanyProfile(); company.setLegalName("Echte Firma GmbH");
        when(repository.findDefault()).thenReturn(Optional.of(company));
        assertThat(service.profileForLetterhead(null, "Ignored GmbH").getLegalName()).isEqualTo("Echte Firma GmbH");
        assertThat(service.hasLetterhead(null)).isTrue();
    }
}
