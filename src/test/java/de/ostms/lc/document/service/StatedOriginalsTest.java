package de.ostms.lc.document.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StatedOriginalsTest {
 @Test void theUsualWordingsAreRead(){
  assertThat(StatedOriginals.detect("Number of original Bills of Lading: 3 (three)")).hasValue(3);
  assertThat(StatedOriginals.detect("No. of originals: THREE")).hasValue(3);
  assertThat(StatedOriginals.detect("NUMBER OF ORIGINAL B/L  2")).hasValue(2);
  assertThat(StatedOriginals.detect("Anzahl der Originale: 3")).hasValue(3);
 }
 @Test void aCarrierTableWithTheCaptionAboveTheValueIsRead(){
  assertThat(StatedOriginals.detect("PORT OF DISCHARGE PLACE OF DELIVERY VOYAGE NO. NO. OF ORIGINALS TYPE OF MOVEMENT\nWUHAN, CHINA 021E 3 / THREE FCL/FCL")).hasValue(3);
  assertThat(StatedOriginals.detect("NUMBER OF ORIGINAL B/L\n\nTHREE (3)")).hasValue(3);
  assertThat(StatedOriginals.detect("No. of Originals\nHAMBURG 3 (THREE) CFS/CFS")).hasValue(3);
 }
 @Test void aBareNumberBelowTheCaptionIsNotTaken(){
  // voyage and container numbers of the same row must not be mistaken for the number of originals
  assertThat(StatedOriginals.detect("NO. OF ORIGINALS TYPE\nWUHAN 021E 3 FCL/FCL")).isEmpty();
  assertThat(StatedOriginals.detect("NO. OF ORIGINALS\n3 / TWO")).isEmpty(); // digit and word disagree
 }
 @Test void contradictoryStatementsAndNoStatementGiveNothing(){
  assertThat(StatedOriginals.detect("No. of originals: 3\nNumber of original B/L: 2")).isEmpty();
  assertThat(StatedOriginals.detect("Shipped on board 5 packages")).isEmpty();
  assertThat(StatedOriginals.detect(null)).isEmpty();assertThat(StatedOriginals.detect(" ")).isEmpty();
 }
}
