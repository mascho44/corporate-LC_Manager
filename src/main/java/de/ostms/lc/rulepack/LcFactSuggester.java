package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.springframework.stereotype.Service;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Proposes LC-level facts from the structured fields of the SWIFT message (parties :50:/:59:, terms via {@link Mt700Facts}). Proposals are stored only after confirmation. */
@Service
public class LcFactSuggester {
 public List<DocumentFactSuggester.Suggestion> suggest(LetterOfCredit lc){return suggest(lc,List.of());}

 /** Like {@link #suggest(LetterOfCredit)}, plus standard values of UCP 600 and amounts taken from the one commercial invoice of the file. */
 public List<DocumentFactSuggester.Suggestion> suggest(LetterOfCredit lc,List<LcDocument> documents){
  var out=suggestFromMessage(lc);
  var current=RuleFacts.read(lc.getRuleFactsJson());
  var known=new EnumMap<Field,String>(Field.class);known.putAll(current);
  Mt700Facts.detect(lc).forEach(f->known.putIfAbsent(f.field(),f.value()));
  standards(out,current,known,lc);
  invoiceAmounts(out,current,known,lc,documents);
  return out;
 }
 /** UCP 600 defaults, only when the message names UCP 600 and says nothing else: 21 days to present (art. 14 c), insurance cover 110 % (art. 28 f ii). */
 static void standards(List<DocumentFactSuggester.Suggestion> out,Map<Field,String> current,Map<Field,String> known,LetterOfCredit lc){
  if(!"UCP600".equals(known.get(Field.LC_RULE_STANDARD)))return;
  if(known.get(Field.LC_PRESENTATION_PERIOD_DAYS)==null)add(out,current,Field.LC_PRESENTATION_PERIOD_DAYS,"21","Standard nach UCP 600 Art. 14 c (Feld :48: nennt keine Frist)");
  boolean insurance=lc.getRequiredDocuments().stream().anyMatch(c->c!=null&&c.toUpperCase(java.util.Locale.ROOT).contains("INSURANCE"));
  if(insurance&&known.get(Field.LC_INSURANCE_MIN_PERCENT)==null)add(out,current,Field.LC_INSURANCE_MIN_PERCENT,"110","Standard nach UCP 600 Art. 28 f ii (Versicherung gefordert, kein Prozentwert genannt)");
 }
 /** With exactly one commercial invoice in the LC currency, its amount is claimed amount and gross goods value; for CIF/CIP it is also the CIF/CIP value (the insurance base is derived from it when the pack is evaluated). */
 static void invoiceAmounts(List<DocumentFactSuggester.Suggestion> out,Map<Field,String> current,Map<Field,String> known,LetterOfCredit lc,List<LcDocument> documents){
  if(documents==null)return;
  var invoices=documents.stream().filter(d->d.getDocumentType()==DocumentType.COMMERCIAL_INVOICE&&d.getAmount()!=null&&d.getAmount().signum()>0
    &&(d.getCurrency()==null||lc.getCurrency()==null||d.getCurrency().equalsIgnoreCase(lc.getCurrency()))).toList();
  if(invoices.size()!=1)return;
  var invoice=invoices.get(0);String amount=invoice.getAmount().setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();
  String source="Betrag der Handelsrechnung "+invoice.getOriginalFilename()+" (einzige Rechnung der Akte)";
  add(out,current,Field.LC_CLAIMED_AMOUNT,amount,source);add(out,current,Field.LC_GROSS_GOODS_AMOUNT,amount,source);
  String term=known.get(Field.LC_INCOTERM);
  if("CIF".equals(term)||"CIP".equals(term)){add(out,current,Field.LC_CIF_CIP_VALUE_AMOUNT,amount,source+", Handelsklausel "+term);}
 }
 private List<DocumentFactSuggester.Suggestion> suggestFromMessage(LetterOfCredit lc){
  var current=RuleFacts.read(lc.getRuleFactsJson());var out=new ArrayList<DocumentFactSuggester.Suggestion>();
  party(out,current,lc.getApplicant(),Field.LC_APPLICANT_ADDRESS,Field.LC_APPLICANT_ADDRESS_COUNTRY,"Auftraggeber (:50:)");
  party(out,current,lc.getBeneficiary(),null,Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"Begünstigter (:59:)");
  Mt700Facts.detect(lc).forEach(f->add(out,current,f.field(),f.value(),f.source()));
  return out;
 }
 private static void party(List<DocumentFactSuggester.Suggestion> out,Map<Field,String> current,String text,Field addressField,Field countryField,String source){
  var parsed=PartyAddress.parse(text);if(parsed==null)return;
  if(addressField!=null&&!parsed.address().isBlank())add(out,current,addressField,parsed.address(),source+": Adresse aus dem SWIFT-Feld");
  if(parsed.country()!=null)add(out,current,countryField,parsed.country(),source+": Land aus der letzten Adresszeile");
 }
 private static void add(List<DocumentFactSuggester.Suggestion> out,Map<Field,String> current,Field f,String value,String source){
  String existing=current.get(f);if(value.equals(existing))return;
  out.add(new DocumentFactSuggester.Suggestion(f,value,source,existing));
 }
}
