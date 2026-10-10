package de.ostms.lc.rulepack;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PackingListFactsTest {
 @Test void labelledPackageCountAndWeightsAreRecognised(){
  var f=PackingListFacts.detect("PACKING LIST\nTotal number of cartons: 48\nGross weight: 1,250.50 KGS\nNet weight: 1,100.00 KGS\n");
  assertThat(f.packageCount()).isEqualTo(48);assertThat(f.packageUnit()).isEqualTo("Kartons");
  assertThat(f.grossWeight()).isEqualTo("1250.50");assertThat(f.netWeight()).isEqualTo("1100.00");assertThat(f.weightUnit()).isEqualTo("KG");
 }
 @Test void commonFormsAndGermanLabelsWork(){
  assertThat(PackingListFacts.detect("TOTAL: 12 PALLETS").packageCount()).isEqualTo(12);
  assertThat(PackingListFacts.detect("Total packages 7").packageCount()).isEqualTo(7);
  var de=PackingListFacts.detect("Anzahl Packstücke: 5\nBruttogewicht: 12.345,6 kg\nNettogewicht 11.000 kg");
  assertThat(de.packageCount()).isEqualTo(5);assertThat(de.grossWeight()).isEqualTo("12345.6");assertThat(de.netWeight()).isEqualTo("11000");
  var abbreviations=PackingListFacts.detect("G.W.: 300 kg\nN.W.: 280 kg");
  assertThat(abbreviations.grossWeight()).isEqualTo("300");assertThat(abbreviations.netWeight()).isEqualTo("280");
  var unitInLabel=PackingListFacts.detect("Gross weight (kgs): 2,5\nNet weight (kgs): 2,1");
  assertThat(unitInLabel.weightUnit()).isEqualTo("KG");assertThat(unitInLabel.grossWeight()).isEqualTo("2.5");
  assertThat(PackingListFacts.detect("Gross weight: 2.5 t").weightUnit()).isEqualTo("T");
 }
 @Test void conflictingOrMissingValuesYieldNothing(){
  var f=PackingListFacts.detect("Total cartons: 10\nTotal cartons: 12\nGross weight: 100 kg\nGross weight: 200 kg");
  assertThat(f.packageCount()).isNull();assertThat(f.grossWeight()).isNull();assertThat(f.empty()).isTrue();
  assertThat(PackingListFacts.detect("Invoice without packing data").empty()).isTrue();assertThat(PackingListFacts.detect(null).empty()).isTrue();assertThat(PackingListFacts.detect("").empty()).isTrue();
 }
 @Test void repeatedIdenticalValuesCountOnce(){
  assertThat(PackingListFacts.detect("Total cartons: 10\nTOTAL: 10 CTNS").packageCount()).isEqualTo(10);
 }
}
