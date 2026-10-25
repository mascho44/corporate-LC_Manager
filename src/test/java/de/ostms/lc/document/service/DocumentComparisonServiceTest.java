package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.*;
import de.ostms.lc.lc.domain.LetterOfCredit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
class DocumentComparisonServiceTest {
 @Test void highlightsChangesButIgnoresDecimalScale(){var a=new LcDocument();var b=new LcDocument();a.setAmount(new BigDecimal("100.00"));b.setAmount(new BigDecimal("100"));a.setCurrency("EUR");b.setCurrency("USD");var result=DocumentComparisonService.diff(a,b);assertThat(result.fields()).anyMatch(f->f.name().equals("Währung")&&f.changed());assertThat(result.fields()).anyMatch(f->f.name().equals("Betrag")&&!f.changed());}
 @Test void rejectsOtherDossierAndSameDocument(){var docs=mock(LcDocumentRepository.class);var repo=mock(DocumentComparisonRepository.class);var service=new DocumentComparisonService(docs,repo,new ObjectMapper());UUID id=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();var lc=new LetterOfCredit();ReflectionTestUtils.setField(lc,"id",UUID.randomUUID());var doc=new LcDocument();doc.setLetterOfCredit(lc);when(docs.findById(a)).thenReturn(Optional.of(doc));assertThatThrownBy(()->service.compare(id,a,b,"reviewer")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->service.compare(id,a,a,"reviewer")).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(repo);}
}
