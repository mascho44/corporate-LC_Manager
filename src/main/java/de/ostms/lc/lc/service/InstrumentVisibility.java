package de.ostms.lc.lc.service;

/** Which kinds of files the signed-in user of this request may not read. Set per request by InstrumentVisibilityFilter, read by the file queries (SpEL); background jobs run without it and see everything. */
public final class InstrumentVisibility {
 private static final ThreadLocal<boolean[]> HIDDEN=new ThreadLocal<>();
 private InstrumentVisibility(){}
 public static void set(boolean hideLetters,boolean hideGuarantees){if(hideLetters||hideGuarantees)HIDDEN.set(new boolean[]{hideLetters,hideGuarantees});else HIDDEN.remove();}
 public static void clear(){HIDDEN.remove();}
 public static boolean hideLetters(){var h=HIDDEN.get();return h!=null&&h[0];}
 public static boolean hideGuarantees(){var h=HIDDEN.get();return h!=null&&h[1];}
 public static boolean restricted(){return HIDDEN.get()!=null;}
 /** JPQL condition for the alias lc (LetterOfCredit). */
 public static final String LC="(:#{T(de.ostms.lc.lc.service.InstrumentVisibility).hideGuarantees()}=false or lc.instrumentType<>'GUARANTEE') and (:#{T(de.ostms.lc.lc.service.InstrumentVisibility).hideLetters()}=false or lc.instrumentType='GUARANTEE')";
 /** Rule used by the filter: interactive users (with a role authority) lose the kinds whose view right they do not hold. */
 public static boolean[] hiddenFor(java.util.Collection<String> authorities){
  boolean interactive=authorities.stream().anyMatch(a->a.startsWith("ROLE_"));
  if(!interactive)return new boolean[]{false,false};
  return new boolean[]{!authorities.contains("PERM_LC_VIEW"),!authorities.contains("PERM_GUARANTEE_VIEW")};
 }
}
