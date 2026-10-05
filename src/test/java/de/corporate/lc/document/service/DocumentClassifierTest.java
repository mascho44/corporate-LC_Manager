package de.corporate.lc.document.service;
import de.corporate.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class DocumentClassifierTest {
 @Test void swiftConditionsAreNotDocumentHeadings(){assertThat(DocumentClassifier.classify("lc.pdf",":20:LC123\n:46A:\nCOMMERCIAL INVOICE").suggestedType()).isNull();}
 @Test void recognizesAllRequiredHeadings(){String[] headings={"COMMERCIAL INVOICE","PACKING LIST","BILL OF LADING","AIR WAYBILL","CERTIFICATE OF ORIGIN","INSURANCE CERTIFICATE"};DocumentType[] types={DocumentType.COMMERCIAL_INVOICE,DocumentType.PACKING_LIST,DocumentType.BILL_OF_LADING,DocumentType.AIR_WAYBILL,DocumentType.CERTIFICATE_OF_ORIGIN,DocumentType.INSURANCE_CERTIFICATE};for(int i=0;i<headings.length;i++){var result=DocumentClassifier.classify("scan.pdf",headings[i]+"\nDetails");assertThat(result.suggestedType()).isEqualTo(types[i]);assertThat(result.score()).isEqualTo(.9);}}
 @Test void conflictsAndMentionsDoNotBecomeCertain(){assertThat(DocumentClassifier.classify("scan.pdf","COMMERCIAL INVOICE\nPACKING LIST").status()).isEqualTo("AMBIGUOUS");assertThat(DocumentClassifier.classify("scan.pdf","Please present a commercial invoice and packing list").status()).isEqualTo("UNKNOWN");}
 @Test void filenameIsOnlyWeakHint(){var result=DocumentClassifier.classify("packing_list.pdf",null);assertThat(result.suggestedType()).isEqualTo(DocumentType.PACKING_LIST);assertThat(result.status()).isEqualTo("REVIEW");assertThat(result.score()).isEqualTo(.55);}
 @Test void manualChoiceRetainsAutoEvidence(){var automatic=ClassificationHistory.automatic("scan.pdf","COMMERCIAL INVOICE");var manual=ClassificationHistory.manual(automatic,DocumentType.PACKING_LIST,"reviewer");assertThat(ClassificationHistory.read(manual)).hasSize(2);assertThat(ClassificationHistory.suggestion(manual).suggestedType()).isEqualTo(DocumentType.COMMERCIAL_INVOICE);assertThat(manual).contains("reviewer","MANUAL","PACKING_LIST");}
}
