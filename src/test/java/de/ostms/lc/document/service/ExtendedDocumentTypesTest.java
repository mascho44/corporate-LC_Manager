package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class ExtendedDocumentTypesTest {
 @Test void newTypesAreSuggestedFromExplicitHeadings(){
  var samples=Map.of("SEA WAYBILL",DocumentType.SEA_WAYBILL,"CHARTER PARTY BILL OF LADING",DocumentType.CHARTER_PARTY_BILL_OF_LADING,
   "MULTIMODAL TRANSPORT DOCUMENT",DocumentType.MULTIMODAL_TRANSPORT_DOCUMENT,"WEIGHT LIST",DocumentType.WEIGHT_LIST,"POST RECEIPT",DocumentType.POST_RECEIPT);
  samples.forEach((heading,type)->{var result=DocumentClassifier.classify("synthetic.pdf",heading+"\nSynthetic details");assertThat(result.suggestedType()).isEqualTo(type);});
 }
 @Test void multipleHeadingsRemainAmbiguous(){assertThat(DocumentClassifier.classify("synthetic.pdf","SEA WAYBILL\nWEIGHT LIST").status()).isEqualTo("AMBIGUOUS");}
}
