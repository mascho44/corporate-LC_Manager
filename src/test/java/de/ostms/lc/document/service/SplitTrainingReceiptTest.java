package de.ostms.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class SplitTrainingReceiptTest {
 @Test void proposalSnapshotSupportsTwoHundredPagesAndExcludesCopies()throws Exception{
  var receipts=new SplitTrainingReceipt(new ObjectMapper());
  var parts=java.util.stream.IntStream.rangeClosed(1,200).mapToObj(page->new PdfDocumentSplitter.Part(page,page,de.ostms.lc.document.domain.DocumentType.COMMERCIAL_INVOICE,-1)).toList();
  var token=receipts.issue("a".repeat(64),200,"reviewer",parts,"RULE_BASED");
  assertThat(token.length()).isLessThan(40000);
  var snapshot=receipts.verify(token,"reviewer");assertThat(snapshot.proposedParts()).hasSize(200);assertThat(snapshot.proposedParts()).allMatch(part->part.copyNumber()==null);assertThat(snapshot.method()).isEqualTo("RULE_BASED");
 }
 @Test void receiptsAreBoundToReviewerTenantAndRuntimeAndRejectTampering()throws Exception{
  var receipts=new SplitTrainingReceipt(new ObjectMapper());String token=receipts.issue("a".repeat(64),3,"reviewer");
  assertThat(receipts.verify(token,"reviewer").pages()).isEqualTo(3);
  assertThatThrownBy(()->receipts.verify(token,"another-user")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->receipts.verify("x"+token,"reviewer")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new SplitTrainingReceipt(new ObjectMapper()).verify(token,"reviewer")).isInstanceOf(IllegalArgumentException.class);
  try(var scope=TenantContext.open(UUID.randomUUID())){assertThatThrownBy(()->receipts.verify(token,"reviewer")).isInstanceOf(IllegalArgumentException.class);}
 }
}
