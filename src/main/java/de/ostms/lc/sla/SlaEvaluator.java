package de.ostms.lc.sla;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Pure calculation: pairs the events of one file into clocks and rates them in banking days. Pause events stop the clock until a resume event. */
public final class SlaEvaluator {
 private SlaEvaluator(){}
 /** PAUSED is a running clock that waits (e.g. for the customer); it is never "worse" than a running clock. */
 public enum State { ON_TRACK, WARNING, BREACHED, DONE_IN_TIME, DONE_LATE, PAUSED }
 public record Event(String action,LocalDateTime at){}
 /** pausedDays: banking days the clock stood still (including a pause that is still running); paused: the clock waits right now. */
 public record Instance(LocalDateTime startedAt,LocalDateTime stoppedAt,LocalDate dueDate,int elapsedDays,int targetDays,State state,int pausedDays,boolean paused){
  public Instance(LocalDateTime startedAt,LocalDateTime stoppedAt,LocalDate dueDate,int elapsedDays,int targetDays,State state){this(startedAt,stoppedAt,dueDate,elapsedDays,targetDays,state,0,false);}
 }

 public static List<Instance> evaluate(List<Event> events,List<String> startActions,List<String> stopActions,int targetDays,int warnPercent,LocalDate today){
  return evaluate(events,startActions,stopActions,List.of(),List.of(),targetDays,warnPercent,today);
 }

 /** Chronological events: a start action opens a clock if none is open, a pause action holds an open clock, a resume action releases it, the next stop action closes it. */
 public static List<Instance> evaluate(List<Event> events,List<String> startActions,List<String> stopActions,List<String> pauseActions,List<String> resumeActions,int targetDays,int warnPercent,LocalDate today){
  var out=new ArrayList<Instance>();LocalDateTime open=null;LocalDate pausedSince=null;int pausedDays=0;
  for(Event event:events){
   String action=event.action();LocalDate day=event.at().toLocalDate();
   if(open==null){if(startActions.contains(action)){open=event.at();pausedSince=null;pausedDays=0;}continue;}
   if(stopActions.contains(action)){
    if(pausedSince!=null)pausedDays+=BusinessCalendar.businessDaysBetween(pausedSince,day);
    out.add(rate(open,event.at(),targetDays,warnPercent,today,pausedDays,0,false));open=null;pausedSince=null;
   }else if(pausedSince==null&&pauseActions.contains(action)){pausedSince=day;}
   else if(pausedSince!=null&&resumeActions.contains(action)){pausedDays+=BusinessCalendar.businessDaysBetween(pausedSince,day);pausedSince=null;}
  }
  if(open!=null){
   int ongoing=pausedSince==null?0:BusinessCalendar.businessDaysBetween(pausedSince,today);
   out.add(rate(open,null,targetDays,warnPercent,today,pausedDays,ongoing,pausedSince!=null));
  }
  return out;
 }
 static Instance rate(LocalDateTime start,LocalDateTime stop,int targetDays,int warnPercent,LocalDate today){return rate(start,stop,targetDays,warnPercent,today,0,0,false);}
 /** completedPause: banking days of finished pauses (push the due date); ongoingPause: banking days of a pause that is still running (not counted as elapsed). */
 static Instance rate(LocalDateTime start,LocalDateTime stop,int targetDays,int warnPercent,LocalDate today,int completedPause,int ongoingPause,boolean paused){
  LocalDate from=start.toLocalDate(),due=BusinessCalendar.addBusinessDays(from,targetDays+completedPause);
  LocalDate until=stop==null?today:stop.toLocalDate();
  int elapsed=Math.max(0,BusinessCalendar.businessDaysBetween(from,until)-completedPause-ongoingPause);
  State state;
  if(stop!=null)state=until.isAfter(due)?State.DONE_LATE:State.DONE_IN_TIME;
  else if(paused)state=State.PAUSED;
  else if(today.isAfter(due))state=State.BREACHED;
  else state=elapsed*100>=targetDays*warnPercent?State.WARNING:State.ON_TRACK;
  return new Instance(start,stop,due,elapsed,targetDays,state,completedPause+ongoingPause,paused);
 }
 /** The worst state of running clocks (breached, warning, on track), or null when none is running. Paused clocks do not count. */
 public static State worstRunning(List<Instance> instances){
  State worst=null;
  for(Instance i:instances){
   if(i.stoppedAt()!=null||i.state()==State.PAUSED)continue;
   if(worst==null||i.state().ordinal()>worst.ordinal())worst=i.state();
  }
  return worst;
 }
}
