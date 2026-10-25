package de.ostms.lc.document.service;
import de.ostms.lc.document.api.DocumentController;
import de.ostms.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class DocumentPagePreviewControllerTest {
 @Test void rendersAuthorizedPdfAsNonCacheablePngWithPageCount()throws Exception{
  var service=mock(DocumentService.class);var renderer=mock(PdfPagePreviewService.class);var controller=new DocumentController(service,null,null,null);ReflectionTestUtils.setField(controller,"pagePreview",renderer);
  var id=UUID.randomUUID();var doc=new LcDocument();doc.setContentType("application/pdf");byte[] pdf={1},png={2};doc.setContent(pdf);when(service.one(id)).thenReturn(doc);when(renderer.pageCount(pdf)).thenReturn(3);when(renderer.render(pdf,2,true)).thenReturn(png);
  var response=controller.pagePreview(id,2,"large");assertThat(response.getBody()).isEqualTo(png);assertThat(response.getHeaders().getFirst("X-Page-Count")).isEqualTo("3");assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
 }
 @Test void accessFailureCannotStartRendering()throws Exception{
  var service=mock(DocumentService.class);var renderer=mock(PdfPagePreviewService.class);var controller=new DocumentController(service,null,null,null);ReflectionTestUtils.setField(controller,"pagePreview",renderer);var id=UUID.randomUUID();when(service.one(id)).thenThrow(new java.util.NoSuchElementException("not found"));
  assertThatThrownBy(()->controller.pagePreview(id,1,"large")).isInstanceOf(java.util.NoSuchElementException.class);verifyNoInteractions(renderer);
 }
 @Test void thumbSizeRequestsTheSmallRendering()throws Exception{
  var service=mock(DocumentService.class);var renderer=mock(PdfPagePreviewService.class);var controller=new DocumentController(service,null,null,null);ReflectionTestUtils.setField(controller,"pagePreview",renderer);
  var id=UUID.randomUUID();var doc=new LcDocument();doc.setContentType("application/pdf");byte[] pdf={1},small={7};doc.setContent(pdf);when(service.one(id)).thenReturn(doc);when(renderer.pageCount(pdf)).thenReturn(2);when(renderer.render(pdf,1,false)).thenReturn(small);
  assertThat(controller.pagePreview(id,1,"thumb").getBody()).isEqualTo(small);
  verify(renderer,never()).render(pdf,1,true);
 }
}
