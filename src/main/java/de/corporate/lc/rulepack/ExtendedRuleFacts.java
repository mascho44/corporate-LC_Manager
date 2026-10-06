package de.corporate.lc.rulepack;
import java.math.BigDecimal;
import java.util.*;
import static de.corporate.lc.rulepack.PackDefinition.Field;
/** Generic reviewed financial context. All values use the LC currency, without FX conversion. */
public final class ExtendedRuleFacts {
 private ExtendedRuleFacts(){}
 public static String insuranceBasis(Map<Field,String> facts){
  try{
   String cif=facts.get(Field.LC_CIF_CIP_VALUE_AMOUNT);
   if(cif!=null&&!cif.isBlank())return positive(cif).toPlainString();
   // A partial fallback must not silently underestimate the required basis.
   return positive(facts.get(Field.LC_CLAIMED_AMOUNT)).max(positive(facts.get(Field.LC_GROSS_GOODS_AMOUNT))).toPlainString();
  }catch(RuntimeException invalid){return null;}
 }
 private static BigDecimal positive(String value){
  if(value==null||!value.matches("[0-9]{1,19}(\\.[0-9]{1,6})?"))throw new IllegalArgumentException();
  var number=new BigDecimal(value);if(number.signum()<=0)throw new IllegalArgumentException();return number;
 }
 public static String label(Field field){return switch(field){
  case DOCUMENT_CARRIER->"Genannter Frachtführer";case DOCUMENT_SIGNER_ROLE->"Rolle des Unterzeichners";case DOCUMENT_SIGNED_FOR->"Vertretener Frachtführer / Versicherer";
  case DOCUMENT_LOADING_PORT,LC_LOADING_PORT->"Verladehafen";case DOCUMENT_DISCHARGE_PORT,LC_DISCHARGE_PORT->"Löschhafen";
  case DOCUMENT_DEPARTURE_AIRPORT,LC_DEPARTURE_AIRPORT->"Abflughafen";case DOCUMENT_DESTINATION_AIRPORT,LC_DESTINATION_AIRPORT->"Zielflughafen";
  case DOCUMENT_VESSEL->"Genanntes Schiff";case DOCUMENT_TRANSPORT_NOTATION->"Geprüfter Transport-/On-board-Vermerk";
  case DOCUMENT_ISSUED_ORIGINAL_COUNT->"Laut Dokument ausgestellte Originale";case DOCUMENT_INSURANCE_TYPE->"Versicherungsdokumentart";
  case DOCUMENT_COVERAGE_FROM,LC_COVERAGE_FROM->"Deckungsstrecke: Beginn";case DOCUMENT_COVERAGE_TO,LC_COVERAGE_TO->"Deckungsstrecke: Ende";
  case DOCUMENT_UNIT_PRICE_AMOUNT,LC_UNIT_PRICE_AMOUNT->"Geprüfter Einheitspreis (Dokument-/LC-Währung)";
  case DOCUMENT_ORIGIN_COUNTRY,LC_ORIGIN_COUNTRY->"Ursprungsland";
  case LC_CIF_CIP_VALUE_AMOUNT->"Geprüfter CIF-/CIP-Wert in LC-Währung (leer: nicht bestimmbar)";
  case LC_CLAIMED_AMOUNT->"Tatsächlich beanspruchter Betrag in LC-Währung";
  case LC_GROSS_GOODS_AMOUNT->"Geprüfter Bruttowarenwert in LC-Währung";
  case LC_QUANTITY->"Gültige LC-Menge";case LC_QUANTITY_UNIT->"Gültige LC-Mengeneinheit";
  default->field.name();
 };}
}
