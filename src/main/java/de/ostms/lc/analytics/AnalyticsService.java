package de.ostms.lc.analytics;

import de.ostms.lc.guarantee.GuaranteeService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.sla.SlaService;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Numbers over time for the cockpit charts: open files and volume, arrivals and closings, the load of deadlines ahead and, for banks, discrepancies and service levels. */
@Service
public class AnalyticsService {
 public record Point(LocalDate start,LocalDate end,int received,int closed,int open,Map<String,BigDecimal> volume){}
 public record Load(LocalDate start,LocalDate end,int expiry,int shipment,int followUp,int guaranteeExpiry,int guaranteeDecision){}
 public record BankPoint(LocalDate start,LocalDate end,int discrepancies,int reports,int slaInTime,int slaLate,Integer slaQuota){}
 public record Totals(int open,Map<String,BigDecimal> volume,int received,int closed,int previousReceived,int previousClosed,int overdue){}
 public record Response(int days,String granularity,List<String> currencies,List<Point> trend,List<Load> load,List<BankPoint> bank,Totals totals){}
 private static final int LOAD_WEEKS=12;
 private final LetterOfCreditRepository lcs;private final GuaranteeService guarantees;private final SlaService sla;private final TenantRepository tenants;private final JdbcTemplate jdbc;
 public AnalyticsService(LetterOfCreditRepository lcs,GuaranteeService guarantees,SlaService sla,TenantRepository tenants,JdbcTemplate jdbc){this.lcs=lcs;this.guarantees=guarantees;this.sla=sla;this.tenants=tenants;this.jdbc=jdbc;}

 static boolean finished(LetterOfCredit lc){return lc.getStatus()==LetterOfCreditStatus.CLOSED||lc.getStatus()==LetterOfCreditStatus.EXPIRED;}
 static boolean letter(LetterOfCredit lc){return !"GUARANTEE".equals(lc.getInstrumentType());}
 /** Open at the end of a day: came in on or before it and not finished before the end of it. */
 static boolean openAt(LetterOfCredit lc,LocalDate day){
  var received=lc.getReceivedAt();var closed=lc.getClosedAt();
  return received!=null&&!received.toLocalDate().isAfter(day)&&(closed==null||closed.toLocalDate().isAfter(day));
 }

 @Transactional(readOnly=true) public Response trends(int days,LocalDate today){
  if(days<7||days>730)throw new IllegalArgumentException("Der Zeitraum muss zwischen 7 und 730 Tagen liegen.");
  var all=lcs.findAll().stream().filter(AnalyticsService::letter).toList();
  var past=AnalyticsBuckets.past(today,days);
  var currencies=topCurrencies(all.stream().filter(lc->!finished(lc)).toList());
  var trend=new ArrayList<Point>();
  for(var b:past){
   int received=0,closed=0,open=0;var volume=new LinkedHashMap<String,BigDecimal>();currencies.forEach(c->volume.put(c,BigDecimal.ZERO));
   var at=b.end().isAfter(today)?today:b.end();
   for(var lc:all){
    if(lc.getReceivedAt()!=null&&b.contains(lc.getReceivedAt().toLocalDate()))received++;
    if(lc.getClosedAt()!=null&&b.contains(lc.getClosedAt().toLocalDate()))closed++;
    if(openAt(lc,at)){open++;var c=lc.getCurrency();if(c!=null&&lc.getAmount()!=null&&volume.containsKey(c))volume.merge(c,lc.getAmount(),BigDecimal::add);}
   }
   trend.add(new Point(b.start(),b.end(),received,closed,open,volume));
  }
  var load=load(all,today);
  var tenant=tenants.findById(TenantContext.currentId()).orElse(null);
  List<BankPoint> bank=tenant!=null&&tenant.isBankEnabled()?bank(all,past,today):null;
  return new Response(days,AnalyticsBuckets.weekly(days)?"WEEK":"MONTH",currencies,trend,load,bank,totals(all,days,today,currencies));
 }

 private static List<String> topCurrencies(List<LetterOfCredit> open){
  var sum=new HashMap<String,BigDecimal>();
  open.forEach(lc->{if(lc.getCurrency()!=null&&lc.getAmount()!=null)sum.merge(lc.getCurrency(),lc.getAmount(),BigDecimal::add);});
  return sum.entrySet().stream().sorted((a,b)->b.getValue().compareTo(a.getValue())).limit(3).map(Map.Entry::getKey).toList();
 }
 private Totals totals(List<LetterOfCredit> all,int days,LocalDate today,List<String> currencies){
  int open=0,received=0,closed=0,pr=0,pc=0,overdue=0;var volume=new LinkedHashMap<String,BigDecimal>();currencies.forEach(c->volume.put(c,BigDecimal.ZERO));
  var from=today.minusDays(days-1L);var previousFrom=from.minusDays(days);
  for(var lc:all){
   if(!finished(lc)){open++;if(lc.getCurrency()!=null&&lc.getAmount()!=null&&volume.containsKey(lc.getCurrency()))volume.merge(lc.getCurrency(),lc.getAmount(),BigDecimal::add);if(lc.getExpiryDate()!=null&&lc.getExpiryDate().isBefore(today))overdue++;}
   var r=lc.getReceivedAt()==null?null:lc.getReceivedAt().toLocalDate();var c=lc.getClosedAt()==null?null:lc.getClosedAt().toLocalDate();
   if(r!=null&&!r.isBefore(from)&&!r.isAfter(today))received++;else if(r!=null&&!r.isBefore(previousFrom)&&r.isBefore(from))pr++;
   if(c!=null&&!c.isBefore(from)&&!c.isAfter(today))closed++;else if(c!=null&&!c.isBefore(previousFrom)&&c.isBefore(from))pc++;
  }
  return new Totals(open,volume,received,closed,pr,pc,overdue);
 }
 /** Deadlines of the next weeks by kind; guarantees only for those who may read them (the file list already respects that). */
 private List<Load> load(List<LetterOfCredit> letters,LocalDate today){
  var buckets=AnalyticsBuckets.ahead(today,LOAD_WEEKS);int n=buckets.size();
  int[] expiry=new int[n],shipment=new int[n],followUp=new int[n],gExpiry=new int[n],gDecision=new int[n];
  for(var lc:letters){
   if(finished(lc))continue;
   bump(expiry,buckets,lc.getExpiryDate());
   if(lc.isExporterSide())bump(shipment,buckets,lc.getLatestShipmentDate());
   bump(followUp,buckets,lc.getFollowUpDate());
  }
  for(var g:guarantees.list(today)){if(!g.open())continue;bump(gExpiry,buckets,g.expiryDate());bump(gDecision,buckets,g.decisionDate());}
  var out=new ArrayList<Load>();
  for(int i=0;i<n;i++)out.add(new Load(buckets.get(i).start(),buckets.get(i).end(),expiry[i],shipment[i],followUp[i],gExpiry[i],gDecision[i]));
  return out;
 }
 private static void bump(int[] counts,List<AnalyticsBuckets.Bucket> buckets,LocalDate day){
  if(day==null)return;int i=AnalyticsBuckets.indexOf(buckets,day);
  if(i>=0)counts[i]++;else if(day.isBefore(buckets.get(0).start()))counts[0]++; // overdue dates count in the first week
 }

 private List<BankPoint> bank(List<LetterOfCredit> letters,List<AnalyticsBuckets.Bucket> past,LocalDate today){
  var ids=new HashSet<String>();letters.forEach(lc->ids.add(lc.getId().toString()));
  int n=past.size();int[] discrepancies=new int[n],reports=new int[n],inTime=new int[n],late=new int[n];
  var since=java.sql.Timestamp.valueOf(past.get(0).start().atStartOfDay());
  jdbc.query("select entity_id, action, occurred_at from audit_event where tenant_id=? and successful and occurred_at >= ? and action in ('DOCUMENT_DISCREPANCY_CONFIRMED','DOCUMENT_CHECK_REPORT_CREATED')",rs->{
   if(!ids.contains(rs.getString("entity_id")))return;
   int i=AnalyticsBuckets.indexOf(past,rs.getTimestamp("occurred_at").toLocalDateTime());if(i<0)return;
   if("DOCUMENT_DISCREPANCY_CONFIRMED".equals(rs.getString("action")))discrepancies[i]++;else reports[i]++;
  },TenantContext.currentId(),since);
  for(var status:sla.statusOfAll(today).values())for(var instance:status.instances()){
   LocalDateTime stopped=instance.clock().stoppedAt();int i=AnalyticsBuckets.indexOf(past,stopped);if(i<0)continue;
   if(instance.clock().state()==de.ostms.lc.sla.SlaEvaluator.State.DONE_LATE)late[i]++;else inTime[i]++;
  }
  var out=new ArrayList<BankPoint>();
  for(int i=0;i<n;i++)out.add(new BankPoint(past.get(i).start(),past.get(i).end(),discrepancies[i],reports[i],inTime[i],late[i],inTime[i]+late[i]==0?null:Math.round(inTime[i]*100f/(inTime[i]+late[i]))));
  return out;
 }
}
