package de.ostms.lc.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Time buckets of the trend charts: weeks (Monday to Sunday) for up to 120 days, months for longer periods. */
final class AnalyticsBuckets {
 record Bucket(LocalDate start,LocalDate end){boolean contains(LocalDate d){return d!=null&&!d.isBefore(start)&&!d.isAfter(end);}}
 private AnalyticsBuckets(){}

 static boolean weekly(int days){return days<=120;}

 /** The buckets ending with the one that contains today; the last one is cut at today only for display (its end stays the end of the week or month). */
 static List<Bucket> past(LocalDate today,int days){
  var out=new ArrayList<Bucket>();
  if(weekly(days)){
   int weeks=Math.max(1,(int)Math.ceil(days/7.0));var monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
   for(int i=weeks-1;i>=0;i--){var start=monday.minusWeeks(i);out.add(new Bucket(start,start.plusDays(6)));}
  }else{
   int months=Math.min(24,Math.max(2,(int)Math.round(days/30.0)));var first=today.withDayOfMonth(1);
   for(int i=months-1;i>=0;i--){var start=first.minusMonths(i);out.add(new Bucket(start,start.plusMonths(1).minusDays(1)));}
  }
  return out;
 }
 /** The next {@code weeks} weeks starting with the current one. */
 static List<Bucket> ahead(LocalDate today,int weeks){
  var out=new ArrayList<Bucket>();var monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  for(int i=0;i<weeks;i++){var start=monday.plusWeeks(i);out.add(new Bucket(start,start.plusDays(6)));}
  return out;
 }
 static int indexOf(List<Bucket> buckets,LocalDate day){for(int i=0;i<buckets.size();i++)if(buckets.get(i).contains(day))return i;return -1;}
 static int indexOf(List<Bucket> buckets,LocalDateTime time){return time==null?-1:indexOf(buckets,time.toLocalDate());}
}
