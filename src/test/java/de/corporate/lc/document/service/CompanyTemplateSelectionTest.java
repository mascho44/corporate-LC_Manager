package de.corporate.lc.document.service;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.document.repository.DocumentTemplateRepository;
import de.corporate.lc.company.service.CompanyProfileService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
class CompanyTemplateSelectionTest {
 @Test void stableCompanyIdTakesPrecedence(){
  var repo=mock(DocumentTemplateRepository.class);
  var template=new DocumentTemplate();template.setContent(new byte[]{1,2});
  when(repo.findByDocumentTypeAndCompanyId(DocumentType.COMMERCIAL_INVOICE,2)).thenReturn(Optional.of(template));
  var service=new DocumentTemplateService(repo,mock(CompanyProfileService.class));
  assertThat(service.content(DocumentType.COMMERCIAL_INVOICE,2,"Other company").orElseThrow()).containsExactly(1,2);
  verify(repo,never()).findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(any(),any());
 }
 @Test void missingCompanyTemplateUsesGeneralTemplate(){
  var repo=mock(DocumentTemplateRepository.class);
  var template=new DocumentTemplate();template.setContent(new byte[]{3});
  when(repo.findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(DocumentType.PACKING_LIST,"*")).thenReturn(Optional.of(template));
  var service=new DocumentTemplateService(repo,mock(CompanyProfileService.class));
  assertThat(service.content(DocumentType.PACKING_LIST,3,"Other company").orElseThrow()).containsExactly(3);
  verify(repo,never()).findByDocumentTypeAndCompanyIdIsNullAndCompanyNameIgnoreCase(DocumentType.PACKING_LIST,"Other company");
 }
}
