package de.ostms.lc.rulepack;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.springframework.stereotype.Service;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** Proposes LC-level facts from the structured party fields (:50:, :59:) of the SWIFT message. Proposals are stored only after confirmation. */
@Service
public class LcFactSuggester {
 public List<DocumentFactSuggester.Suggestion> suggest(LetterOfCredit lc){
  var current=RuleFacts.read(lc.getRuleFactsJson());var out=new ArrayList<DocumentFactSuggester.Suggestion>();
  party(out,current,lc.getApplicant(),Field.LC_APPLICANT_ADDRESS,Field.LC_APPLICANT_ADDRESS_COUNTRY,"Auftraggeber (:50:)");
  party(out,current,lc.getBeneficiary(),null,Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"Begünstigter (:59:)");
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
