package de.corporate.lc.document.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class SplitTrainingReceiptTest {
 @Test void receiptsAreBoundToReviewerTenantAndRuntimeAndRejectTampering()throws Exception{
  var receipts=new SplitTrainingReceipt(new ObjectMapper());String token=receipts.issue("a".repeat(64),3,"reviewer");
  assertThat(receipts.verify(token,"reviewer").pages()).isEqualTo(3);
  assertThatThrownBy(()->receipts.verify(token,"another-user")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->receipts.verify("x"+token,"reviewer")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new SplitTrainingReceipt(new ObjectMapper()).verify(token,"reviewer")).isInstanceOf(IllegalArgumentException.class);
  try(var scope=TenantContext.open(UUID.randomUUID())){assertThatThrownBy(()->receipts.verify(token,"reviewer")).isInstanceOf(IllegalArgumentException.class);}
 }
}
