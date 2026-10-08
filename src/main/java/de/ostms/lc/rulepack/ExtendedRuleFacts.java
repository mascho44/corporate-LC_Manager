package de.ostms.lc.rulepack;
import java.math.BigDecimal;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
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
  case DOCUMENT_PRESENTATION_DATE->"Geprüftes Vorlagedatum";
  case DOCUMENT_TRANSSHIPMENT_INDICATED->"Umladung angezeigt?";
  case LC_TRANSSHIPMENT_ALLOWED->"Umladung erlaubt?";
  case LC_PARTIAL_SHIPMENT_ALLOWED->"Teilverladung erlaubt?";
  case DOCUMENT_SHIPMENT_COUNT->"Geprüfte Anzahl Sendungen";
  case DOCUMENT_ON_BOARD_NOTATION_PRESENT->"Bordvermerk vorhanden?";
  case DOCUMENT_ON_BOARD_DATE->"Geprüftes Borddatum";
  case DOCUMENT_INTENDED_VESSEL_INDICATED->"Intended vessel angezeigt?";
  case LC_ON_BOARD_NOTATION_REQUIRED->"Bordvermerk gefordert?";
  case DOCUMENT_CONSIGNEE->"Empfänger / Consignee";
  case DOCUMENT_NOTIFY_PARTY->"Meldeadresse / Notify party";
  case DOCUMENT_APPLICANT_ADDRESS_COUNTRY->"Land der Auftraggeberadresse";
  case DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY->"Land der Begünstigtenadresse";
  case LC_APPLICANT_ADDRESS_COUNTRY->"LC: Land der Auftraggeberadresse";
  case LC_BENEFICIARY_ADDRESS_COUNTRY->"LC: Land der Begünstigtenadresse";
  case LC_CONSIGNEE->"Geforderter Empfänger";
  case LC_NOTIFY_PARTY->"Geforderte Meldeadresse";
  case DOCUMENT_FREIGHT_PREPAID->"Fracht vorausbezahlt?";
  case DOCUMENT_FREIGHT_TERMS->"Geprüfte Frachtbedingung";
  case LC_FREIGHT_PREPAID_REQUIRED->"Frachtvorauszahlung gefordert?";
  case LC_FREIGHT_TERMS->"Geforderte Frachtbedingung";
  case DOCUMENT_INCOTERM->"Handelsklausel im Dokument";
  case DOCUMENT_INCOTERM_SOURCE->"Fundstelle der Handelsklausel";
  case LC_INCOTERM->"Vereinbarte Handelsklausel";
  case LC_INCOTERM_SOURCE->"Quelle der vereinbarten Handelsklausel";
  case LC_REQUIRED_ISSUER->"Geforderter Aussteller für diesen Dokumenttyp";
  case DOCUMENT_INVOICE_REFERENCE->"Rechnungsnummer-Bezug";
  case PEER_DOCUMENT_NUMBER->"Dokumentnummer des Vergleichsdokuments";
  case DOCUMENT_FRANCHISE_PRESENT->"Franchise / Selbstbehalt vorhanden?";
  case DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE->"Irrespective of percentage angegeben?";
  case LC_FRANCHISE_ALLOWED->"Franchise / Selbstbehalt erlaubt?";
  case LC_IRRESPECTIVE_OF_PERCENTAGE_REQUIRED->"Irrespective of percentage gefordert?";
  case DOCUMENT_INSURED_PARTY->"Versicherter";
  case LC_INSURED_PARTY->"Geforderter Versicherter";
  case DOCUMENT_ENDORSEMENT_PRESENT->"Indossament vorhanden?";
  case DOCUMENT_ENDORSEMENT_TO->"Indossiert an";
  case LC_ENDORSEMENT_REQUIRED->"Indossament gefordert?";
  case LC_ENDORSEMENT_TO->"Geforderter Indossamentempfänger";
  case DOCUMENT_DRAWEE->"Bezogener der Tratte";
  case LC_DRAWEE->"Vereinbarter Bezogener";
  case DOCUMENT_DRAFT_TENOR_DAYS->"Trattenlaufzeit in Tagen";
  case LC_DRAFT_TENOR_DAYS->"Vereinbarte Trattenlaufzeit in Tagen";
  case DOCUMENT_DRAFT_TENOR_BASIS->"Bezugsereignis der Trattenlaufzeit";
  case LC_DRAFT_TENOR_BASIS->"Vereinbartes Bezugsereignis der Laufzeit";
  case DOCUMENT_PACKAGE_COUNT->"Packstückzahl";
  case PEER_PACKAGE_COUNT->"Packstückzahl des Vergleichsdokuments";
  case DOCUMENT_SHIPPING_MARKS->"Markierungen / Shipping marks";
  case PEER_SHIPPING_MARKS->"Markierungen des Vergleichsdokuments";
  case DOCUMENT_SIGNER_NAME->"Name des Unterzeichners";
  case DOCUMENT_CONSIGNEE_ADDRESS_COUNTRY->"Land der Empfängeradresse";
  case DOCUMENT_NOTIFY_ADDRESS_COUNTRY->"Land der Meldeadresse";
  case LC_CONSIGNEE_ADDRESS_COUNTRY->"LC: Land der Empfängeradresse";
  case LC_NOTIFY_ADDRESS_COUNTRY->"LC: Land der Meldeadresse";
  case DOCUMENT_PARTIAL_SHIPMENT_INDICATED->"Teilverladung fachlich festgestellt?";
  case DOCUMENT_NUMBER->"Erfasste Dokumentnummer";
  default->field.name();
 };}
}
