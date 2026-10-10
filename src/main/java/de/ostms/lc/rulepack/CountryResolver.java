package de.ostms.lc.rulepack;
import java.util.*;

/** Resolves a country written in an address (English or German name, ISO code, common abbreviation) to its English name. */
public final class CountryResolver {
 private static final Map<String,String> KEYS=new HashMap<>();
 private static final Map<String,String> ALIASES=Map.ofEntries(
  Map.entry("PRC","CN"),Map.entry("P R C","CN"),Map.entry("P R CHINA","CN"),Map.entry("PR CHINA","CN"),Map.entry("PEOPLES REPUBLIC OF CHINA","CN"),Map.entry("PEOPLE S REPUBLIC OF CHINA","CN"),Map.entry("VR CHINA","CN"),Map.entry("VOLKSREPUBLIK CHINA","CN"),
  Map.entry("USA","US"),Map.entry("U S A","US"),Map.entry("U S","US"),Map.entry("UNITED STATES OF AMERICA","US"),Map.entry("VEREINIGTE STAATEN","US"),
  Map.entry("UK","GB"),Map.entry("U K","GB"),Map.entry("GREAT BRITAIN","GB"),Map.entry("ENGLAND","GB"),Map.entry("GROSSBRITANNIEN","GB"),Map.entry("VEREINIGTES KOENIGREICH","GB"),
  Map.entry("UAE","AE"),Map.entry("U A E","AE"),Map.entry("TURKEY","TR"),Map.entry("TUERKEI","TR"),Map.entry("RUSSIA","RU"),Map.entry("CZECH REPUBLIC","CZ"),Map.entry("VIET NAM","VN"),
  Map.entry("SOUTH KOREA","KR"),Map.entry("REPUBLIC OF KOREA","KR"),Map.entry("KOREA REPUBLIC OF","KR"),Map.entry("HONG KONG","HK"),Map.entry("HONGKONG","HK"),Map.entry("DEUTSCHLAND","DE"),Map.entry("OESTERREICH","AT"),Map.entry("SCHWEIZ","CH"),Map.entry("NIEDERLANDE","NL"),Map.entry("HOLLAND","NL"));
 static{
  for(String code:Locale.getISOCountries()){
   var locale=new Locale("",code);String english=locale.getDisplayCountry(Locale.ENGLISH);
   if(english==null||english.isBlank())continue;
   KEYS.putIfAbsent(code,code);
   try{KEYS.putIfAbsent(locale.getISO3Country(),code);}catch(MissingResourceException ignored){}
   KEYS.putIfAbsent(key(english),code);
   KEYS.putIfAbsent(key(locale.getDisplayCountry(Locale.GERMAN)),code);
  }
 }
 private CountryResolver(){}
 static String key(String value){
  if(value==null)return "";
  String t=java.text.Normalizer.normalize(value.toUpperCase(Locale.ROOT).replace("Ä","AE").replace("Ö","OE").replace("Ü","UE").replace("ß","SS"),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");
  return t.replaceAll("[^A-Z]+"," ").trim();
 }
 /** English country name, or empty when the text is not clearly a country. Codes (2/3 letters) are only accepted in capitals. */
 public static Optional<String> resolve(String raw){
  if(raw==null)return Optional.empty();
  String trimmed=raw.strip().replaceAll("^[,;:\\-–\\s]+|[,;:\\-–.\\s]+$","");
  if(trimmed.isEmpty()||trimmed.length()>60||trimmed.chars().anyMatch(Character::isDigit))return Optional.empty();
  String k=key(trimmed);if(k.isEmpty())return Optional.empty();
  boolean shortCode=k.replace(" ","").length()<=3;
  if(shortCode&&!trimmed.equals(trimmed.toUpperCase(Locale.ROOT)))return Optional.empty();
  String code=ALIASES.get(k);if(code==null)code=KEYS.get(k);if(code==null&&shortCode)code=KEYS.get(k.replace(" ",""));
  if(code==null)return Optional.empty();
  return Optional.of(new Locale("",code).getDisplayCountry(Locale.ENGLISH));
 }
}
