package de.ostms.lc.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** The customer master of the tenant (first source: entered by hand). */
@Service
public class CustomerService {
 public record Request(String name,String customerNumber,String accountNumber,String iban,String bic){}
 public record View(UUID id,String name,String customerNumber,String accountNumber,String iban,String bic,String source){}
 private final CustomerRepository customers;
 public CustomerService(CustomerRepository customers){this.customers=customers;}

 static View view(Customer c){return new View(c.getId(),c.getName(),c.getCustomerNumber(),c.getAccountNumber(),c.getIban(),c.getBic(),c.getSource());}
 private static String clean(String value,int max,String label){
  if(value==null||value.isBlank())return null;
  String v=value.strip();if(v.length()>max)throw new IllegalArgumentException(label+" ist zu lang (höchstens "+max+" Zeichen).");return v;
 }

 @Transactional(readOnly=true) public List<View> list(){return customers.findAll().stream().sorted(Comparator.comparing(c->c.getName().toLowerCase(Locale.ROOT))).map(CustomerService::view).toList();}

 @Transactional public View create(Request request,String user){var c=new Customer();apply(c,request,user);return view(customers.save(c));}
 @Transactional public View update(UUID id,Request request,String user){var c=customers.findById(id).orElseThrow(()->new NoSuchElementException("Kunde nicht gefunden."));apply(c,request,user);return view(customers.save(c));}
 @Transactional public String delete(UUID id){var c=customers.findById(id).orElseThrow(()->new NoSuchElementException("Kunde nicht gefunden."));customers.delete(c);return c.getName();}

 private void apply(Customer c,Request r,String user){
  if(r==null)throw new IllegalArgumentException("Angaben fehlen.");
  String name=clean(r.name(),255,"Der Name");if(name==null)throw new IllegalArgumentException("Bitte einen Namen angeben.");
  String number=clean(r.customerNumber(),60,"Die Kundennummer"),account=clean(r.accountNumber(),60,"Die Kontonummer"),bic=clean(r.bic(),11,"Die BIC");
  String iban=SubmissionLetterReader.normalizeIban(clean(r.iban(),42,"Die IBAN"));
  if(iban!=null&&!SubmissionLetterReader.validIban(iban))throw new IllegalArgumentException("Die IBAN ist ungültig (Prüfziffer oder Länge stimmen nicht).");
  if(bic!=null&&!bic.toUpperCase(Locale.ROOT).matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?"))throw new IllegalArgumentException("Die BIC ist ungültig (8 oder 11 Zeichen).");
  if(number!=null)customers.byNumber(number).filter(other->!other.getId().equals(c.getId())).ifPresent(other->{throw new IllegalArgumentException("Die Kundennummer ist schon vergeben (Kunde „"+other.getName()+"“).");});
  c.set(name,number,account,iban,bic==null?null:bic.toUpperCase(Locale.ROOT),user);
 }
}
