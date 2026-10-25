package de.ostms.lc.customer;

import de.ostms.lc.lc.domain.LetterOfCredit;

/** Whether an LC file belongs to a customer: the customer's name is (part of) the beneficiary or the applicant of the credit. Names only count from five letters on. */
public final class LcCustomerMatcher {
 private LcCustomerMatcher(){}
 static boolean same(String a,String b){
  if(a.length()<5||b.length()<5)return false;
  return a.equals(b)||a.contains(b)||b.contains(a);
 }
 public static boolean matches(Customer customer,LetterOfCredit lc){
  String name=CustomerMatcher.words(customer.getName());
  return same(name,CustomerMatcher.words(lc.getBeneficiary()))||same(name,CustomerMatcher.words(lc.getApplicant()));
 }
}
