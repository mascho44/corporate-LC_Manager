package de.ostms.lc.sla;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Banking days: Monday to Friday without the nation-wide public holidays of Germany (New Year, Good Friday, Easter Monday, 1 May, Ascension, Whit Monday, 3 October, Christmas Day, Boxing Day). */
public final class BusinessCalendar {
 private BusinessCalendar(){}

 static LocalDate easter(int year){
  int a=year%19,b=year/100,c=year%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3,h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451;
  int month=(h+l-7*m+114)/31,day=(h+l-7*m+114)%31+1;
  return LocalDate.of(year,month,day);
 }
 static Set<LocalDate> holidays(int year){
  var easter=easter(year);var set=new HashSet<LocalDate>();
  set.add(LocalDate.of(year,1,1));set.add(easter.minusDays(2));set.add(easter.plusDays(1));set.add(LocalDate.of(year,5,1));set.add(easter.plusDays(39));set.add(easter.plusDays(50));
  set.add(LocalDate.of(year,10,3));set.add(LocalDate.of(year,12,25));set.add(LocalDate.of(year,12,26));
  return set;
 }
 public static boolean isBusinessDay(LocalDate date){
  DayOfWeek day=date.getDayOfWeek();
  return day!=DayOfWeek.SATURDAY&&day!=DayOfWeek.SUNDAY&&!holidays(date.getYear()).contains(date);
 }
 /** The date that is {@code days} banking days after {@code from} (the start day itself does not count). */
 public static LocalDate addBusinessDays(LocalDate from,int days){
  LocalDate date=from;int left=days;
  while(left>0){date=date.plusDays(1);if(isBusinessDay(date))left--;}
  return date;
 }
 /** Banking days after {@code from} up to and including {@code to}; 0 for the same day or an earlier date. */
 public static int businessDaysBetween(LocalDate from,LocalDate to){
  int count=0;
  for(LocalDate date=from.plusDays(1);!date.isAfter(to);date=date.plusDays(1))if(isBusinessDay(date))count++;
  return count;
 }
}
