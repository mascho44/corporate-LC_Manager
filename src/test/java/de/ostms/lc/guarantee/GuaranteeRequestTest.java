package de.ostms.lc.guarantee;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.service.DocxTemplateService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GuaranteeRequestTest {
 static final LocalDate TODAY=LocalDate.of(2026,10,23);
 private final GuaranteeRequestRepository requests=mock(GuaranteeRequestRepository.class);private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);private final GuaranteeService guarantees=mock(GuaranteeService.class);
 private final DocxTemplateService templates=mock(DocxTemplateService.class);private final AuditService audit=mock(AuditService.class);
 private final GuaranteeRequestService service=new GuaranteeRequestService(requests,lcs,guarantees,templates,audit);
 private LetterOfCredit file(String type){var lc=new LetterOfCredit();ReflectionTestUtils.setField(lc,"id",UUID.randomUUID());lc.setReference("G-1");lc.setInstrumentType(type);return lc;}
 private UUID known(String type){var lc=file(type);when(lcs.findById(lc.getId())).thenReturn(Optional.of(lc));when(requests.save(any())).thenAnswer(i->i.getArgument(0));return lc.getId();}

 @Test void aNewRequestIsADraftAndCanBeSavedWhileOpen() {
  var id=known("GUARANTEE");when(requests.byLc(id)).thenReturn(Optional.empty());
  assertThat(service.get(id).exists()).isFalse();assertThat(service.get(id).status()).isEqualTo("DRAFT");
  var saved=service.save(id,new GuaranteeRequestService.Request("Musterbank AG\nFrankfurt"," Z-77 "," Text des Wortlauts ","  ",LocalDate.of(2026,11,5)),null);
  assertThat(saved.recipient()).startsWith("Musterbank");assertThat(saved.ourReference()).isEqualTo("Z-77");assertThat(saved.remarks()).isNull();assertThat(saved.responseDue()).isEqualTo(LocalDate.of(2026,11,5));assertThat(saved.status()).isEqualTo("DRAFT");
  verify(audit).recordInTransaction(any(),eq("GUARANTEE_REQUEST_SAVED"),eq("LETTER_OF_CREDIT"),eq(id),anyString());
 }

 @Test void theStatusFollowsTheAllowedPathAndSentFixesTheSendDate() {
  var id=known("GUARANTEE");var r=new GuaranteeRequest(id);when(requests.byLc(id)).thenReturn(Optional.of(r));
  assertThatThrownBy(()->service.setStatus(id,new GuaranteeRequestService.StatusRequest("ISSUED",null,null,null),null)).hasMessageContaining("nicht möglich"); // a draft cannot be issued
  var sent=service.setStatus(id,new GuaranteeRequestService.StatusRequest("SENT",LocalDate.of(2026,10,20),null,null),null);
  assertThat(sent.status()).isEqualTo("SENT");assertThat(sent.sentOn()).isEqualTo(LocalDate.of(2026,10,20));assertThat(sent.nextStatuses()).contains("QUERY","APPROVED","ISSUED");
  service.setStatus(id,new GuaranteeRequestService.StatusRequest("QUERY",null,"Rückfrage zum Wortlaut",null),null);
  var again=service.setStatus(id,new GuaranteeRequestService.StatusRequest("SENT",LocalDate.of(2026,10,22),null,null),null);assertThat(again.sentOn()).isEqualTo(LocalDate.of(2026,10,20)); // the first send date stays
  assertThatThrownBy(()->service.setStatus(id,new GuaranteeRequestService.StatusRequest("NOPE",null,null,null),null)).hasMessageContaining("Unbekannter");
  assertThatThrownBy(()->service.setStatus(id,new GuaranteeRequestService.StatusRequest("APPROVED",LocalDate.now().plusDays(1),null,null),null)).hasMessageContaining("Zukunft");
 }

 @Test void issuedActivatesTheGuaranteeRefusedAndWithdrawnCancelItAndFinishedRequestsAreLocked() {
  var id=known("GUARANTEE");var r=new GuaranteeRequest(id);r.setStatus("APPROVED",TODAY,null,null,"t");when(requests.byLc(id)).thenReturn(Optional.of(r));
  var issued=service.setStatus(id,new GuaranteeRequestService.StatusRequest("ISSUED",LocalDate.of(2026,10,21),null,"BANK-4711"),null);
  assertThat(issued.bankReference()).isEqualTo("BANK-4711");assertThat(issued.open()).isFalse();assertThat(issued.nextStatuses()).isEmpty();
  var captor=org.mockito.ArgumentCaptor.forClass(GuaranteeService.StatusRequest.class);verify(guarantees).setStatus(eq(id),captor.capture(),any());assertThat(captor.getValue().status()).isEqualTo("ACTIVE");
  assertThatThrownBy(()->service.save(id,new GuaranteeRequestService.Request("x",null,null,null,null),null)).hasMessageContaining("abgeschlossen");
  var id2=known("GUARANTEE");var r2=new GuaranteeRequest(id2);r2.setStatus("SENT",TODAY,null,null,"t");when(requests.byLc(id2)).thenReturn(Optional.of(r2));
  service.setStatus(id2,new GuaranteeRequestService.StatusRequest("REJECTED",TODAY,"Bonität",null),null);
  var cancel=org.mockito.ArgumentCaptor.forClass(GuaranteeService.StatusRequest.class);verify(guarantees).setStatus(eq(id2),cancel.capture(),any());assertThat(cancel.getValue().status()).isEqualTo("CANCELLED");assertThat(cancel.getValue().note()).contains("abgelehnt").contains("Bonität");
 }

 @Test void onlyGuaranteesHaveRequests() {
  var id=known("LC");assertThatThrownBy(()->service.get(id)).isInstanceOf(NoSuchElementException.class);
  assertThatThrownBy(()->service.setStatus(id,new GuaranteeRequestService.StatusRequest("SENT",null,null,null),null)).isInstanceOf(NoSuchElementException.class);
 }

 private static GuaranteeService.View view(){
  return new GuaranteeService.View(UUID.randomUUID(),"G-1","PERFORMANCE","Musterbank AG\nFrankfurt","Kunde GmbH\nBerlin","EUR",new BigDecimal("50000"),null,LocalDate.of(2026,11,1),LocalDate.of(2027,3,31),LocalDate.of(2027,6,30),true,60,LocalDate.of(2027,1,30),"Liefervertrag 4711","K-17","REQUESTED",null,null,null,false,true,true);
 }
 private static String text(byte[] docx)throws Exception{try(var d=new XWPFDocument(new ByteArrayInputStream(docx))){var sb=new StringBuilder();d.getParagraphs().forEach(p->sb.append(p.getText()).append('\n'));d.getTables().forEach(t->t.getRows().forEach(row->row.getTableCells().forEach(c->sb.append(c.getText()).append('|'))));return sb.toString();}}

 @Test void theWordApplicationHoldsLetterheadFactsWordingAndSignature() throws Exception {
  var r=new GuaranteeRequest(UUID.randomUUID());r.setText("Musterbank AG\nGarantieabteilung","Z-77","Die Garantie soll auf erstes Anfordern zahlbar sein.","Bitte um Eilbearbeitung.",LocalDate.of(2026,11,5),"t");
  var company=Map.<String,Object>of("companyName","Beispiel GmbH","companyAddressLine","Hauptstr. 1, 10115 Berlin","companyContactLine","Tel. 030 123","companyCityOnly","Berlin","companyLegalLine","USt-IdNr. DE1","companyBankLine","IBAN DE00");
  String de=text(GuaranteeRequestService.word(company,view(),r,false,TODAY));
  assertThat(de).contains("Beispiel GmbH").contains("Hauptstr. 1, 10115 Berlin").contains("Musterbank AG").contains("Garantieabteilung").contains("Berlin, 23.10.2026").contains("Unser Zeichen: Z-77")
   .contains("Antrag auf Ausstellung einer Garantie – Vertragserfüllungsgarantie").contains("Kunde GmbH, Berlin").contains("EUR 50000,00").contains("31.03.2027").contains("30.06.2027").contains("verlängert sich automatisch").contains("Liefervertrag 4711").contains("K-17")
   .contains("Gewünschter Wortlaut").contains("auf erstes Anfordern").contains("Eilbearbeitung").contains("06.11.2026".replace("06","05")).contains("Mit freundlichen Grüßen").contains("______").contains("IBAN DE00");
  String en=text(GuaranteeRequestService.word(company,view(),null,true,TODAY));
  assertThat(en).contains("Application for the issue of a guarantee – performance guarantee").contains("Dear Sir or Madam,").contains("EUR 50000.00").contains("Valid until").contains("Kind regards").contains("Musterbank AG"); // without a request the bank of the guarantee is the recipient
 }
}
