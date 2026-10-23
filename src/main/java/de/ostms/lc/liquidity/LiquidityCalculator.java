package de.ostms.lc.liquidity;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Pure calculation: groups expected payments by period and currency. Inflows come from export credits, outflows from import credits. */
public final class LiquidityCalculator {
 private LiquidityCalculator(){}
 public enum Granularity { WEEK, MONTH }
 /** direction IN (beneficiary, money expected) or OUT (applicant, money to pay); date null = no maturity known. */
 public record Item(UUID lcId,String reference,String counterparty,String direction,String currency,BigDecimal amount,LocalDate date,String basis){
  public boolean overdue(LocalDate today){return date!=null&&date.isBefore(today);}
 }
 public record Amounts(BigDecimal in,BigDecimal out,BigDecimal net){}
 /** key: OVERDUE, NO_DATE or the period (2026-W43 / 2026-11); from and to are null for the two special rows. */
 public record Period(String key,LocalDate from,LocalDate to,Map<String,Amounts> byCurrency){}
 public record Forecast(Granularity granularity,LocalDate today,List<String> currencies,List<Period> periods){}

 public static Forecast compute(List<Item> items,LocalDate today,Granularity granularity,int count){
  int n=Math.max(1,Math.min(count,granularity==Granularity.WEEK?52:24));
  var currencies=new TreeSet<String>();items.forEach(i->currencies.add(i.currency()));
  var rows=new LinkedHashMap<String,Period>();
  rows.put("OVERDUE",new Period("OVERDUE",null,null,new TreeMap<>()));
  LocalDate start=start(today,granularity);
  for(int i=0;i<n;i++){LocalDate from=granularity==Granularity.WEEK?start.plusWeeks(i):start.plusMonths(i);LocalDate to=granularity==Granularity.WEEK?from.plusDays(6):YearMonth.from(from).atEndOfMonth();rows.put(key(from,granularity),new Period(key(from,granularity),from,to,new TreeMap<>()));}
  rows.put("NO_DATE",new Period("NO_DATE",null,null,new TreeMap<>()));
  LocalDate horizonEnd=rows.values().stream().filter(p->p.to()!=null).map(Period::to).max(Comparator.naturalOrder()).orElse(today);
  for(var item:items){
   String key;
   if(item.date()==null)key="NO_DATE";
   else if(item.overdue(today))key="OVERDUE";
   else if(item.date().isAfter(horizonEnd))continue;
   else key=key(start(item.date(),granularity),granularity);
   var period=rows.get(key);if(period==null)continue;
   period.byCurrency().merge(item.currency(),"IN".equals(item.direction())?new Amounts(item.amount(),BigDecimal.ZERO,item.amount()):new Amounts(BigDecimal.ZERO,item.amount(),item.amount().negate()),
     (a,b)->new Amounts(a.in().add(b.in()),a.out().add(b.out()),a.net().add(b.net())));
  }
  return new Forecast(granularity,today,List.copyOf(currencies),List.copyOf(rows.values()));
 }
 static LocalDate start(LocalDate date,Granularity g){return g==Granularity.WEEK?date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)):date.withDayOfMonth(1);}
 static String key(LocalDate start,Granularity g){return g==Granularity.WEEK?start.get(IsoFields.WEEK_BASED_YEAR)+"-W"+String.format("%02d",start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)):YearMonth.from(start).toString();}
}
