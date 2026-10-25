package de.ostms.lc.customer;

import java.util.*;

/** Suggests customers for the data of a submission letter. Identifiers beat names; a suggestion is "clear" only when exactly one customer matches an identifier. */
public final class CustomerMatcher {
 public record Candidate(UUID customerId,String name,String reason,int rank){}
 public record Result(List<Candidate> candidates,boolean clear){public Candidate best(){return candidates.isEmpty()?null:candidates.get(0);}}
 private CustomerMatcher(){}

 static String alnum(String value){return value==null?"":value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
 static String digits(String value){String d=value==null?"":value.replaceAll("[^0-9]","");return d.replaceFirst("^0+(?=.)","");}
 static String words(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip();}

 public static Result suggest(SubmissionLetterReader.Fields fields,List<Customer> customers){
  List<Candidate> found=new ArrayList<>();
  for(Customer customer:customers){
   String reason=null;int rank=0;
   if(fields.iban()!=null&&customer.getIban()!=null&&fields.iban().equals(SubmissionLetterReader.normalizeIban(customer.getIban()))){reason="IBAN";rank=5;}
   else if(fields.customerNumber()!=null&&!alnum(customer.getCustomerNumber()).isEmpty()&&alnum(fields.customerNumber()).equals(alnum(customer.getCustomerNumber()))){reason="Kundennummer";rank=4;}
   else if(fields.accountNumber()!=null&&!digits(customer.getAccountNumber()).isEmpty()&&digits(fields.accountNumber()).equals(digits(customer.getAccountNumber()))){reason="Kontonummer";rank=3;}
   else if(fields.customerName()!=null){
    String a=words(fields.customerName()),b=words(customer.getName());
    if(!a.isEmpty()&&a.equals(b)){reason="Name";rank=2;}
    else if(a.length()>=5&&b.length()>=5&&(a.contains(b)||b.contains(a))){reason="Name (teilweise)";rank=1;}
   }
   if(reason!=null)found.add(new Candidate(customer.getId(),customer.getName(),reason,rank));
  }
  found.sort(Comparator.comparingInt(Candidate::rank).reversed().thenComparing(Candidate::name));
  boolean clear=!found.isEmpty()&&found.get(0).rank()>=3&&found.stream().filter(c->c.rank()==found.get(0).rank()).count()==1
    &&found.stream().noneMatch(c->c.rank()>=3&&!c.customerId().equals(found.get(0).customerId())&&c.rank()<found.get(0).rank());
  return new Result(List.copyOf(found),clear);
 }
}
