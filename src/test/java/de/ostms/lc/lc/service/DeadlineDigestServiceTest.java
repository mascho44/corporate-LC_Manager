package de.ostms.lc.lc.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DeadlineDigestServiceTest {
 static final LocalDate TODAY=LocalDate.of(2026,10,26);
 static LcDeadlineService.LcDeadlineView due(String title,LocalDate date){return new LcDeadlineService.LcDeadlineView("T",title,date,"src",null,true);}

 @Test void overdueAndNextThreeDaysAreSelectedEarliestFirst(){
  var lines=DeadlineDigestService.select(List.of(due("in 3 Tagen",TODAY.plusDays(3)),due("gestern",TODAY.minusDays(1)),due("heute",TODAY)),TODAY);
  assertThat(lines).extracting(DeadlineDigestService.Line::title).containsExactly("gestern","heute","in 3 Tagen");
  assertThat(lines).extracting(DeadlineDigestService.Line::daysLeft).containsExactly(-1L,0L,3L);
 }
 @Test void farFutureVeryOldAndUndatedDeadlinesAreLeftOut(){
  var lines=DeadlineDigestService.select(List.of(due("in 4 Tagen",TODAY.plusDays(4)),due("vor 31 Tagen",TODAY.minusDays(31)),due("ohne Datum",null),due("vor 30 Tagen",TODAY.minusDays(30))),TODAY);
  assertThat(lines).extracting(DeadlineDigestService.Line::title).containsExactly("vor 30 Tagen");
 }
 @Test void mailTextFollowsTheLanguageAndSaysHowToSwitchOff(){
  var entries=List.of(new DeadlineDigestService.Entry("LC-1",List.of(new DeadlineDigestService.Line("Ablauf",TODAY.minusDays(2),-2),new DeadlineDigestService.Line("Vorlage",TODAY,0))));
  var de=DeadlineDigestService.body(entries,false,"https://lc.example");
  assertThat(de).contains("LC-1").contains("Ablauf, 24.10.2026 (seit 2 Tagen überfällig)").contains("Vorlage, 26.10.2026 (heute fällig)").contains("https://lc.example").contains("ausschalten");
  var en=DeadlineDigestService.body(entries,true,"");
  assertThat(en).contains("overdue by 2 days").contains("due today").contains("switch it off").doesNotContain("http");
  assertThat(DeadlineDigestService.subject(entries,false)).isEqualTo("Corporate LC Manager – 2 Fristen");
  assertThat(DeadlineDigestService.subject(List.of(new DeadlineDigestService.Entry("X",List.of(new DeadlineDigestService.Line("a",TODAY,0)))),true)).isEqualTo("Corporate LC Manager – 1 deadline");
 }
}
