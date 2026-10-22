package de.ostms.lc.sla;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Pure calculation: pairs the events of one file into clocks and rates them in banking days. */
public final class SlaEvaluator {
 private SlaEvaluator(){}
 public enum State { ON_TRACK, WARNING, BREACHED, DONE_IN_TIME, DONE_LATE }
 public record Event(String action,LocalDateTime at){}
 public record Instance(LocalDateTime startedAt,LocalDateTime stoppedAt,LocalDate dueDate,int elapsedDays,int targetDays,State state){}

 /** Chronological events: a start action opens a clock if none is open, the next stop action closes it. */
 public static List<Instance> evaluate(List<Event> events,List<String> startActions,List<String> stopActions,int targetDays,int warnPercent,LocalDate today){
  var out=new ArrayList<Instance>();LocalDateTime open=null;
  for(Event event:events){
   if(open==null&&startActions.contains(event.action())){open=event.at();continue;}
   if(open!=null&&stopActions.contains(event.action())){out.add(rate(open,event.at(),targetDays,warnPercent,today));open=null;}
  }
  if(open!=null)out.add(rate(open,null,targetDays,warnPercent,today));
  return out;
 }
 static Instance rate(LocalDateTime start,LocalDateTime stop,int targetDays,int warnPercent,LocalDate today){
  LocalDate from=start.toLocalDate(),due=BusinessCalendar.addBusinessDays(from,targetDays);
  LocalDate until=stop==null?today:stop.toLocalDate();
  int elapsed=BusinessCalendar.businessDaysBetween(from,until);
  State state;
  if(stop!=null)state=until.isAfter(due)?State.DONE_LATE:State.DONE_IN_TIME;
  else if(today.isAfter(due))state=State.BREACHED;
  else state=elapsed*100>=targetDays*warnPercent?State.WARNING:State.ON_TRACK;
  return new Instance(start,stop,due,elapsed,targetDays,state);
 }
 /** The worst state of running clocks (breached, warning, on track), or null when none is running. */
 public static State worstRunning(List<Instance> instances){
  State worst=null;
  for(Instance i:instances){
   if(i.stoppedAt()!=null)continue;
   if(worst==null||i.state().ordinal()>worst.ordinal())worst=i.state();
  }
  return worst;
 }
}
