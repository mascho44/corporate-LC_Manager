package de.ostms.lc.document.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class FormFieldPairingTest {
 private static OcrEvidence.Word w(String t,int left,int top){return new OcrEvidence.Word(t,.95,1,left,top,t.length()*14,24);}
 @Test void captionsAboveValuesArePairedByColumn(){
  var words=List.of(w("Invoice",100,300),w("No.:",190,300),w("Invoice",600,300),w("Date:",690,300),w("Documentary",1100,300),w("Credit",1290,300),w("No.:",1370,300),
   w("INV-2026-0815",104,340),w("05.12.2026",604,340),w("TEST-LC-2026-003",1104,340));
  assertThat(FormFieldPairing.pairs(words)).containsExactly("Invoice No.: INV-2026-0815","Invoice Date: 05.12.2026","Documentary Credit No.: TEST-LC-2026-003");
 }
 @Test void multiWordValuesStayInTheirColumnAndAddressLinesAreJoined(){
  var words=List.of(w("Terms",100,300),w("of",170,300),w("delivery:",210,300),w("Country",700,300),w("of",800,300),w("origin:",850,300),
   w("CIF",104,340),w("Jebel",160,340),w("Ali",230,340),w("Germany",704,340),w("Stuttgart",104,374));
  assertThat(FormFieldPairing.pairs(words)).containsExactly("Terms of delivery: CIF Jebel Ali, Stuttgart","Country of origin: Germany");
 }
 @Test void aCaptionWithItsValueOnTheSameLineIsLeftAlone(){
  var words=List.of(w("Total",100,300),w("amount:",190,300),w("EUR",300,300),w("125,000.00",360,300));
  assertThat(FormFieldPairing.pairs(words)).isEmpty();
  assertThat(FormFieldPairing.pairs(null)).isEmpty();
 }
 @Test void anInvoiceNumberOnAnotherDocumentTypeIsNotItsOwnNumber(){
  var type=de.ostms.lc.document.domain.DocumentType.class;
  assertThat(FormFieldPairing.belongsTo("Invoice No.: INV-1",de.ostms.lc.document.domain.DocumentType.COMMERCIAL_INVOICE)).isTrue();
  assertThat(FormFieldPairing.belongsTo("Invoice No.: INV-1",null)).isTrue();
  assertThat(FormFieldPairing.belongsTo("Invoice No.: INV-1",de.ostms.lc.document.domain.DocumentType.PACKING_LIST)).isFalse();
  assertThat(FormFieldPairing.belongsTo("Invoice Date: 05.12.2026",de.ostms.lc.document.domain.DocumentType.PACKING_LIST)).isTrue();
  assertThat(type).isNotNull();
 }
 @Test void aCaptionWithoutColonNextToALabelIsAColumnOfItsOwn(){
  var words=List.of(w("ASSURED",100,300),w("Documentary",700,300),w("Credit",880,300),w("No.:",960,300),
   w("BEISPIEL",104,340),w("MASCHINENBAU",250,340),w("GMBH",470,340),w("TEST-LC-2026-003",704,340));
  assertThat(FormFieldPairing.pairs(words)).containsExactly("Documentary Credit No.: TEST-LC-2026-003");
 }
}
