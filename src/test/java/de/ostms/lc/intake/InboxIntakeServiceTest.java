package de.ostms.lc.intake;

import de.ostms.lc.capture.InboxCaptureInfoRepository;
import de.ostms.lc.document.api.DocumentInboxItemView;
import de.ostms.lc.document.service.DocumentInboxService;
import de.ostms.lc.document.service.PdfPagePreviewService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:intakecore;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class InboxIntakeServiceTest {
 @Autowired InboxCaptureInfoRepository infos;@Autowired TenantRepository tenants;
 final DocumentInboxService inbox=Mockito.mock(DocumentInboxService.class);final PdfPagePreviewService pdf=Mockito.mock(PdfPagePreviewService.class);
 InboxIntakeService service;

 DocumentInboxItemView view(UUID id){
  var item=new de.ostms.lc.document.domain.DocumentInboxItem();item.setOriginalFilename("x.pdf");item.setContentType("application/pdf");item.setReceivedBy("mail");
  org.springframework.test.util.ReflectionTestUtils.setField(item,"id",id);return DocumentInboxItemView.from(item,List.of());
 }
 IntakeOrigin mail(String reference){return new IntakeOrigin(IntakeOrigin.EMAIL,reference,null,null,"einkauf@kunde.example","Dokumente zu LC 4711",null,LocalDateTime.of(2026,10,17,9,30),null,"mail:Posteingang");}
 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  service=new InboxIntakeService(inbox,infos,pdf);
 }

 @Test void aMailAttachmentIsStoredWithItsOriginAndTheSameFileFromAnotherChannelIsADuplicate()throws Exception{
  when(inbox.receive(any(),anyString())).thenAnswer(call->List.of(view(UUID.randomUUID())));
  var first=service.accept(List.of(new IntakeItem("rechnung.pdf","INHALT".getBytes(),"application/pdf",mail("<abc@mail.example>")))).get(0);
  assertThat(first.created()).isTrue();
  var info=infos.findAll().get(0);
  assertThat(info.getChannel()).isEqualTo("EMAIL");assertThat(info.getOriginReference()).isEqualTo("<abc@mail.example>");assertThat(info.getSender()).isEqualTo("einkauf@kunde.example");assertThat(info.getSubject()).isEqualTo("Dokumente zu LC 4711");
  var folder=new IntakeOrigin(IntakeOrigin.FOLDER,"/scan/eingang/rechnung.pdf",null,"Scanner 1",null,null,null,null,null,"folder:scan");
  var second=service.accept(List.of(new IntakeItem("kopie.pdf","INHALT".getBytes(),"application/pdf",folder))).get(0);
  assertThat(second.status()).isEqualTo("DUPLICATE");assertThat(second.inboxItemId()).isEqualTo(first.inboxItemId());
  verify(inbox,times(1)).receive(any(),anyString());
 }
 @Test void aWrongAnnouncedChecksumIsRefusedAndNothingIsStored()throws Exception{
  var origin=new IntakeOrigin(IntakeOrigin.FOLDER,null,null,null,null,null,null,null,"0".repeat(64),"folder:scan");
  assertThatThrownBy(()->service.accept(List.of(new IntakeItem("a.pdf","X".getBytes(),"application/pdf",origin)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Prüfwert");
  verifyNoInteractions(inbox);assertThat(infos.findAll()).isEmpty();
 }
 @Test void originAndItemRejectUnknownChannelsEmptyFilesAndTooLongTexts(){
  assertThatThrownBy(()->new IntakeOrigin("FAX",null,null,null,null,null,null,null,null,"x")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new IntakeOrigin(IntakeOrigin.EMAIL,null,null,null,null,"x".repeat(256),null,null,null,"x")).hasMessageContaining("Betreff");
  assertThatThrownBy(()->new IntakeOrigin(IntakeOrigin.EMAIL,null,null,null,null,null,0,null,null,"x")).hasMessageContaining("Seitenzahl");
  assertThatThrownBy(()->new IntakeItem("a.pdf",new byte[0],"application/pdf",mail("<x>"))).isInstanceOf(IllegalArgumentException.class);
  assertThat(new IntakeItem(" ",new byte[]{1},null,mail("<x>")).filename()).isEqualTo("Dokument");
 }
 @Test void aDeclaredPageCountThatDiffersFromTheRealOneIsNoted()throws Exception{
  when(inbox.receive(any(),anyString())).thenAnswer(call->List.of(view(UUID.randomUUID())));when(pdf.pageCount(any())).thenReturn(3);
  var origin=new IntakeOrigin(IntakeOrigin.SCAN_STATION,null,"Stapel 1","Scanner 2",null,null,4,null,null,"capture:x");
  var receipt=service.accept(List.of(new IntakeItem("s.pdf","SCAN".getBytes(),"application/pdf",origin))).get(0);
  assertThat(receipt.pageCountMatches()).isFalse();assertThat(receipt.note()).contains("4").contains("3");
 }
}
