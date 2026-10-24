package de.ostms.lc.creditline;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.guarantee.GuaranteeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Lines (guarantee lines) of the company and their utilization. Every open guarantee (requested or active, in the currency of the line) counts towards the line of its bank:
 * a line for the type of the guarantee first, otherwise a line for all types. Returned, claimed and expired guarantees free the line. The bank is matched by the first
 * line of its name (ignoring case and punctuation) or by the BIC of the line appearing in the bank text of the guarantee.
 */
@Service
public class CreditLineService {
 private static final Pattern BIC=Pattern.compile("[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?");
 public record Request(String name,String bank,String bic,String currency,BigDecimal amount,String guaranteeType,LocalDate validUntil,Integer warnPercent,Boolean active,String note){}
 /** state: OK, WARNING (from the warning share), EXCEEDED (more than the limit). */
 public record View(UUID id,String name,String bank,String bic,String currency,BigDecimal amount,String guaranteeType,LocalDate validUntil,int warnPercent,boolean active,String note,BigDecimal used,BigDecimal free,int percent,String state,boolean expired,int guaranteeCount){}
 public record Overview(List<View> lines,List<GuaranteeService.View> withoutLine){}
 /** found: a line applies; fits: the amount stays within it; freeAfter can be negative. */
 public record Check(boolean found,boolean fits,String lineName,String currency,BigDecimal limit,BigDecimal free,BigDecimal freeAfter,String state){}
 private final CreditLineRepository lines;private final GuaranteeService guarantees;private final AuditService audit;
 public CreditLineService(CreditLineRepository lines,GuaranteeService guarantees,AuditService audit){this.lines=lines;this.guarantees=guarantees;this.audit=audit;}

 static String norm(String bank){
  if(bank==null)return "";
  for(String line:bank.split("\\R")){String t=line.strip();if(!t.isEmpty())return t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
  return "";
 }
 static boolean matchesBank(CreditLine line,String guaranteeBank){
  if(guaranteeBank==null||guaranteeBank.isBlank())return false;
  if(!norm(line.getBank()).isEmpty()&&norm(line.getBank()).equals(norm(guaranteeBank)))return true;
  return line.getBic()!=null&&guaranteeBank.toUpperCase(Locale.ROOT).replaceAll("\\s","").contains(line.getBic());
 }
 /** The line a guarantee counts towards: a line of its type first, else one for all types; among equals the first by bank and name. */
 static Optional<CreditLine> lineFor(List<CreditLine> all,String bank,String currency,String type){
  var candidates=all.stream().filter(CreditLine::isActive).filter(l->l.getCurrency().equalsIgnoreCase(currency==null?"":currency)).filter(l->matchesBank(l,bank)).toList();
  var typed=candidates.stream().filter(l->type!=null&&type.equals(l.getGuaranteeType())).findFirst();
  return typed.isPresent()?typed:candidates.stream().filter(l->l.getGuaranteeType()==null).findFirst();
 }

 @Transactional(readOnly=true) public Overview overview(LocalDate today){
  var all=lines.findAll();var used=new HashMap<UUID,BigDecimal>();var counts=new HashMap<UUID,Integer>();var without=new ArrayList<GuaranteeService.View>();
  for(var g:guarantees.list(today)){
   if(!g.open())continue;
   var line=lineFor(all,g.bank(),g.currency(),g.guaranteeType());
   if(line.isEmpty()){without.add(g);continue;}
   used.merge(line.get().getId(),g.amount(),BigDecimal::add);counts.merge(line.get().getId(),1,Integer::sum);
  }
  return new Overview(all.stream().map(l->view(l,used.getOrDefault(l.getId(),BigDecimal.ZERO),counts.getOrDefault(l.getId(),0),today)).toList(),without);
 }
 /** The open guarantees that are booked against this line (the same rule as the utilisation). */
 @Transactional(readOnly=true) public List<GuaranteeService.View> guaranteesOf(UUID id,LocalDate today){
  var all=lines.findAll();if(all.stream().noneMatch(l->l.getId().equals(id)))throw new NoSuchElementException("Linie nicht gefunden.");
  return guarantees.list(today).stream().filter(GuaranteeService.View::open)
    .filter(g->lineFor(all,g.bank(),g.currency(),g.guaranteeType()).map(l->l.getId().equals(id)).orElse(false)).toList();
 }
 /** Does a guarantee of this bank, currency, type and amount fit into its line? excludeLcId leaves out the guarantee that is being changed. */
 @Transactional(readOnly=true) public Check check(String bank,String currency,String type,BigDecimal amount,UUID excludeLcId,LocalDate today){
  var all=lines.findAll();var line=lineFor(all,bank,currency==null?null:currency.trim().toUpperCase(Locale.ROOT),type==null||type.isBlank()?null:type.trim().toUpperCase(Locale.ROOT));
  if(line.isEmpty())return new Check(false,true,null,currency,null,null,null,null);
  BigDecimal used=BigDecimal.ZERO;
  for(var g:guarantees.list(today)){
   if(!g.open()||(excludeLcId!=null&&excludeLcId.equals(g.id())))continue;
   var l=lineFor(all,g.bank(),g.currency(),g.guaranteeType());
   if(l.isPresent()&&l.get().getId().equals(line.get().getId()))used=used.add(g.amount());
  }
  BigDecimal free=line.get().getAmount().subtract(used),after=free.subtract(amount==null?BigDecimal.ZERO:amount);
  var v=view(line.get(),used.add(amount==null?BigDecimal.ZERO:amount),0,today);
  return new Check(true,after.signum()>=0,line.get().getName(),line.get().getCurrency(),line.get().getAmount(),free,after,v.state());
 }

 @Transactional public View create(Request r,Authentication auth){
  var c=validate(r);if(lines.byName(c.name()).isPresent())throw new IllegalArgumentException("Es gibt schon eine Linie mit diesem Namen.");
  var line=new CreditLine();apply(line,c,auth);lines.save(line);
  audit.recordInTransaction(auth,"CREDIT_LINE_CREATED","CREDIT_LINE",line.getId(),line.getName()+" · "+line.getCurrency()+" "+line.getAmount().toPlainString());
  return view(line,BigDecimal.ZERO,0,LocalDate.now());
 }
 @Transactional public View update(UUID id,Request r,Authentication auth){
  var line=lines.findById(id).orElseThrow(()->new NoSuchElementException("Linie nicht gefunden."));var c=validate(r);
  lines.byName(c.name()).filter(o->!o.getId().equals(id)).ifPresent(o->{throw new IllegalArgumentException("Es gibt schon eine Linie mit diesem Namen.");});
  apply(line,c,auth);lines.save(line);
  audit.recordInTransaction(auth,"CREDIT_LINE_UPDATED","CREDIT_LINE",id,line.getName()+" · "+line.getCurrency()+" "+line.getAmount().toPlainString()+(line.isActive()?"":" · inaktiv"));
  return overview(LocalDate.now()).lines().stream().filter(v->v.id().equals(id)).findFirst().orElseGet(()->view(line,BigDecimal.ZERO,0,LocalDate.now()));
 }
 @Transactional public void delete(UUID id,Authentication auth){
  var line=lines.findById(id).orElseThrow(()->new NoSuchElementException("Linie nicht gefunden."));
  lines.delete(line);audit.recordInTransaction(auth,"CREDIT_LINE_DELETED","CREDIT_LINE",id,line.getName());
 }

 static View view(CreditLine l,BigDecimal used,int count,LocalDate today){
  BigDecimal free=l.getAmount().subtract(used);
  int percent=used.multiply(BigDecimal.valueOf(100)).divide(l.getAmount(),0,RoundingMode.HALF_UP).intValue();
  String state=used.compareTo(l.getAmount())>0?"EXCEEDED":used.multiply(BigDecimal.valueOf(100)).compareTo(l.getAmount().multiply(BigDecimal.valueOf(l.getWarnPercent())))>=0?"WARNING":"OK";
  return new View(l.getId(),l.getName(),l.getBank(),l.getBic(),l.getCurrency(),l.getAmount(),l.getGuaranteeType(),l.getValidUntil(),l.getWarnPercent(),l.isActive(),l.getNote(),used,free,percent,state,l.getValidUntil()!=null&&l.getValidUntil().isBefore(today),count);
 }
 static Request validate(Request r){
  if(r==null)throw new IllegalArgumentException("Bitte die Angaben zur Linie ausfüllen.");
  String name=clip(r.name(),120);if(name==null)throw new IllegalArgumentException("Bitte einen Namen angeben.");
  String bank=clip(r.bank(),200);if(bank==null)throw new IllegalArgumentException("Bitte die Bank angeben.");
  String bic=r.bic()==null||r.bic().isBlank()?null:r.bic().trim().toUpperCase(Locale.ROOT);
  if(bic!=null&&!BIC.matcher(bic).matches())throw new IllegalArgumentException("Die BIC ist ungültig (8 oder 11 Zeichen).");
  String currency=r.currency()==null?"":r.currency().trim().toUpperCase(Locale.ROOT);if(!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Bitte eine Währung mit drei Buchstaben angeben.");
  if(r.amount()==null||r.amount().signum()<=0)throw new IllegalArgumentException("Bitte einen Betrag größer als 0 angeben.");
  String type=r.guaranteeType()==null||r.guaranteeType().isBlank()?null:r.guaranteeType().trim().toUpperCase(Locale.ROOT);
  if(type!=null&&!GuaranteeService.TYPES.contains(type))throw new IllegalArgumentException("Unbekannte Garantieart.");
  int warn=r.warnPercent()==null?80:r.warnPercent();if(warn<10||warn>99)throw new IllegalArgumentException("Die Warnschwelle muss zwischen 10 und 99 Prozent liegen.");
  return new Request(name,bank,bic,currency,r.amount().setScale(2,RoundingMode.HALF_UP),type,r.validUntil(),warn,r.active(),clip(r.note(),500));
 }
 private static void apply(CreditLine l,Request c,Authentication auth){l.set(c.name(),c.bank(),c.bic(),c.currency(),c.amount(),c.guaranteeType(),c.validUntil(),c.warnPercent(),c.active()==null||c.active(),c.note(),auth==null?null:auth.getName());}
 private static String clip(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();return t.length()<=max?t:t.substring(0,max);}
}
