package de.ostms.lc.analytics;

import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsBucketsTest {
 @Test void weeklyBucketsEndWithTheWeekOfToday(){
  var today=LocalDate.of(2026,10,25); // Sunday
  var buckets=AnalyticsBuckets.past(today,90);
  assertEquals(13,buckets.size());
  assertEquals(LocalDate.of(2026,10,19),buckets.get(12).start());assertEquals(LocalDate.of(2026,10,25),buckets.get(12).end());
  assertEquals(LocalDate.of(2026,7,27),buckets.get(0).start());
  assertEquals(12,AnalyticsBuckets.indexOf(buckets,today));assertEquals(-1,AnalyticsBuckets.indexOf(buckets,LocalDate.of(2026,7,26)));
 }
 @Test void longPeriodsUseMonths(){
  var buckets=AnalyticsBuckets.past(LocalDate.of(2026,10,25),365);
  assertEquals(12,buckets.size());assertEquals(LocalDate.of(2026,10,1),buckets.get(11).start());assertEquals(LocalDate.of(2026,10,31),buckets.get(11).end());
  assertEquals(LocalDate.of(2025,11,1),buckets.get(0).start());
 }
 @Test void aheadStartsWithTheCurrentWeek(){
  var buckets=AnalyticsBuckets.ahead(LocalDate.of(2026,10,28),12);
  assertEquals(12,buckets.size());assertEquals(LocalDate.of(2026,10,26),buckets.get(0).start());assertEquals(LocalDate.of(2027,1,17),buckets.get(11).end());
 }
 @Test void aFileIsOpenBetweenArrivalAndClosing(){
  var lc=new LetterOfCredit();
  assertTrue(AnalyticsService.openAt(lc,LocalDate.now()));
  lc.setStatus(LetterOfCreditStatus.CLOSED);
  assertNotNull(lc.getClosedAt());assertFalse(AnalyticsService.openAt(lc,LocalDate.now().plusDays(1)));
  lc.setStatus(LetterOfCreditStatus.ACTIVE);
  assertNull(lc.getClosedAt());assertTrue(AnalyticsService.openAt(lc,LocalDate.now().plusDays(30)));
 }
}
