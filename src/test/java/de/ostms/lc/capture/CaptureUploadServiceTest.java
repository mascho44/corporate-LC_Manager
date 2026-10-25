package de.ostms.lc.capture;

import de.ostms.lc.document.api.DocumentInboxItemView;
import de.ostms.lc.document.service.DocumentInboxService;
import de.ostms.lc.document.service.PdfPagePreviewService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:captureupload;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class CaptureUploadServiceTest {
 @Autowired InboxCaptureInfoRepository infos;@Autowired TenantRepository tenants;
 final DocumentInboxService inbox=Mockito.mock(DocumentInboxService.class);final PdfPagePreviewService pdf=Mockito.mock(PdfPagePreviewService.class);
 CaptureUploadService service;

 MockMultipartFile file(String name,String content){return new MockMultipartFile("file",name,"application/pdf",content.getBytes());}
 CaptureUploadService.Meta meta(Integer pages,String sha){return new CaptureUploadService.Meta("Stapel 17","Scanner 2",pages,"2026-10-13T08:15:00+02:00",sha);}
 DocumentInboxItemView view(UUID id){
  var item=new de.ostms.lc.document.domain.DocumentInboxItem();item.setOriginalFilename("scan.pdf");item.setContentType("application/pdf");item.setReceivedBy("capture:x");
  org.springframework.test.util.ReflectionTestUtils.setField(item,"id",id);
  return DocumentInboxItemView.from(item,List.of());
 }
 void answerWithNewItems()throws Exception{
  when(inbox.receive(any(),anyString())).thenAnswer(call->{return List.of(view(UUID.randomUUID()));});
 }
 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  service=new CaptureUploadService(new de.ostms.lc.intake.InboxIntakeService(inbox,infos,pdf),infos);
 }

 @Test void aDeliveryIsStoredWithItsMetadataAndAnIdenticalSecondOneIsRecognised()throws Exception{
  answerWithNewItems();when(pdf.pageCount(any())).thenReturn(4);
  var first=service.receive(List.of(file("scan.pdf","INHALT")),meta(4,null),"capture:Poststelle").get(0);
  assertThat(first.status()).isEqualTo("CREATED");assertThat(first.pageCountMatches()).isTrue();assertThat(first.actualPages()).isEqualTo(4);assertThat(first.note()).isNull();
  var again=service.receive(List.of(file("andere-bezeichnung.pdf","INHALT")),meta(4,null),"capture:Poststelle").get(0);
  assertThat(again.status()).isEqualTo("DUPLICATE");assertThat(again.inboxItemId()).isEqualTo(first.inboxItemId());assertThat(again.note()).contains("schon eingegangen");
  verify(inbox,times(1)).receive(any(),anyString());
  var info=service.overview().get(first.inboxItemId());
  assertThat(info.source()).isEqualTo("capture:Poststelle");assertThat(info.batchId()).isEqualTo("Stapel 17");assertThat(info.scanner()).isEqualTo("Scanner 2");assertThat(info.scannedAt()).isNotNull();
 }

 @Test void aDifferentPageCountThanDeclaredIsReported()throws Exception{
  answerWithNewItems();when(pdf.pageCount(any())).thenReturn(3);
  var receipt=service.receive(List.of(file("scan.pdf","ZWEI")),meta(4,null),"capture:Poststelle").get(0);
  assertThat(receipt.pageCountMatches()).isFalse();assertThat(receipt.note()).contains("Gemeldet 4 Seiten, erkannt 3");
  assertThat(service.overview().get(receipt.inboxItemId()).pageCountMatches()).isFalse();
 }

 @Test void aWrongChecksumTheArchiveAndMultipleFilesWithPagesAreRefused()throws Exception{
  answerWithNewItems();
  assertThatThrownBy(()->service.receive(List.of(file("scan.pdf","A")),meta(null,"0".repeat(64)),"capture:x")).hasMessageContaining("Prüfwert");
  assertThatThrownBy(()->service.receive(List.of(file("alles.zip","A")),meta(null,null),"capture:x")).hasMessageContaining("ZIP");
  assertThatThrownBy(()->service.receive(List.of(file("a.pdf","A"),file("b.pdf","B")),meta(2,null),"capture:x")).hasMessageContaining("einzeln");
  assertThatThrownBy(()->service.receive(List.of(file("a.pdf","A")),meta(0,null),"capture:x")).hasMessageContaining("Seitenzahl");
  assertThatThrownBy(()->service.receive(List.of(file("a.pdf","A")),new CaptureUploadService.Meta(null,null,null,"gestern",null),"capture:x")).hasMessageContaining("Scanzeitpunkt");
  verify(inbox,never()).receive(any(),anyString());
 }

 @Test void theCorrectChecksumIsAccepted()throws Exception{
  answerWithNewItems();when(pdf.pageCount(any())).thenReturn(1);
  var sha=CaptureUploadService.sha256("RICHTIG".getBytes());
  assertThat(service.receive(List.of(file("scan.pdf","RICHTIG")),meta(null,sha.toUpperCase()),"capture:x").get(0).status()).isEqualTo("CREATED");
 }
}
