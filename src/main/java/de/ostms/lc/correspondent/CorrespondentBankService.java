package de.ostms.lc.correspondent;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

/** Master data of the correspondent banks of a tenant (BIC, country, channels, routing). */
@Service
public class CorrespondentBankService {
 public static final List<String> CHANNELS=List.of("SWIFT","EBICS","EMAIL","PORTAL");
 private static final Pattern BIC=Pattern.compile("[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?");
 private static final Pattern MAIL=Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
 public record View(UUID id,String bic,String name,String country,String city,List<String> channels,String preferredChannel,String routingBic,String contactEmail,String contactPhone,String notes,boolean active){}
 public record Request(String bic,String name,String country,String city,List<String> channels,String preferredChannel,String routingBic,String contactEmail,String contactPhone,String notes,Boolean active){}
 private final CorrespondentBankRepository banks;private final AuditService audit;
 public CorrespondentBankService(CorrespondentBankRepository banks,AuditService audit){this.banks=banks;this.audit=audit;}

 @Transactional(readOnly=true) public List<View> list(){return banks.findAll().stream().map(CorrespondentBankService::view).toList();}
 @Transactional public View create(Request r,Authentication auth){
  var clean=validate(r,null);var bank=new CorrespondentBank();apply(bank,clean,auth);
  banks.save(bank);audit.recordInTransaction(auth,"CORRESPONDENT_BANK_CREATED","CORRESPONDENT_BANK",bank.getId(),bank.getBic()+" · "+bank.getName());return view(bank);
 }
 @Transactional public View update(UUID id,Request r,Authentication auth){
  var bank=banks.findById(id).orElseThrow(()->new NoSuchElementException("Korrespondenzbank nicht gefunden."));
  var clean=validate(r,id);apply(bank,clean,auth);
  banks.save(bank);audit.recordInTransaction(auth,"CORRESPONDENT_BANK_UPDATED","CORRESPONDENT_BANK",id,bank.getBic()+" · "+bank.getName()+(bank.isActive()?"":" · inaktiv"));return view(bank);
 }
 @Transactional public void delete(UUID id,Authentication auth){
  var bank=banks.findById(id).orElseThrow(()->new NoSuchElementException("Korrespondenzbank nicht gefunden."));
  banks.delete(bank);audit.recordInTransaction(auth,"CORRESPONDENT_BANK_DELETED","CORRESPONDENT_BANK",id,bank.getBic()+" · "+bank.getName());
 }

 private Request validate(Request r,UUID selfId){
  if(r==null)throw new IllegalArgumentException("Bitte die Angaben zur Korrespondenzbank ausfüllen.");
  String bic=upper(r.bic());
  if(bic==null||!BIC.matcher(bic).matches())throw new IllegalArgumentException("Die BIC ist ungültig (8 oder 11 Zeichen).");
  if(r.name()==null||r.name().isBlank())throw new IllegalArgumentException("Bitte einen Namen angeben.");
  String country=upper(r.country());
  if(country!=null&&!country.matches("[A-Z]{2}"))throw new IllegalArgumentException("Der Ländercode muss aus zwei Buchstaben bestehen.");
  String routing=upper(r.routingBic());
  if(routing!=null&&!BIC.matcher(routing).matches())throw new IllegalArgumentException("Die Weiterleitungs-BIC ist ungültig (8 oder 11 Zeichen).");
  var channels=new LinkedHashSet<String>();
  for(String c:r.channels()==null?List.<String>of():r.channels()){String v=upper(c);if(v==null)continue;if(!CHANNELS.contains(v))throw new IllegalArgumentException("Unbekannter Kommunikationsweg.");channels.add(v);}
  String preferred=upper(r.preferredChannel());
  if(preferred!=null&&!channels.contains(preferred))throw new IllegalArgumentException("Der bevorzugte Kommunikationsweg muss unter den Kommunikationswegen sein.");
  String mail=blankToNull(r.contactEmail());
  if(mail!=null&&!MAIL.matcher(mail).matches())throw new IllegalArgumentException("Die E-Mail-Adresse ist ungültig.");
  banks.byBic(bic).filter(b->!b.getId().equals(selfId)).ifPresent(b->{throw new IllegalArgumentException("Eine Korrespondenzbank mit dieser BIC existiert bereits.");});
  return new Request(bic,r.name().trim(),country,blankToNull(r.city()),List.copyOf(channels),preferred,routing,mail,blankToNull(r.contactPhone()),blankToNull(r.notes()),r.active());
 }
 private static void apply(CorrespondentBank bank,Request c,Authentication auth){
  bank.set(c.bic(),c.name(),c.country(),c.city(),c.channels(),c.preferredChannel(),c.routingBic(),c.contactEmail(),c.contactPhone(),c.notes(),c.active()==null||c.active(),auth==null?null:auth.getName());
 }
 private static String upper(String v){String t=blankToNull(v);return t==null?null:t.toUpperCase(Locale.ROOT);}
 private static String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
 static View view(CorrespondentBank b){return new View(b.getId(),b.getBic(),b.getName(),b.getCountry(),b.getCity(),b.channels(),b.getPreferredChannel(),b.getRoutingBic(),b.getContactEmail(),b.getContactPhone(),b.getNotes(),b.isActive());}
}
