package de.ostms.lc.swift;
import de.ostms.lc.lc.domain.*;
import java.util.*;
import java.util.regex.Pattern;
/** Compatibility adapter for existing MT sources; format identifiers never belong to the domain enum. */
public final class SwiftConditionAdapter {
 private SwiftConditionAdapter(){}
 private static final Map<LcCondition,String> TAGS=Map.ofEntries(
  Map.entry(LcCondition.GOODS_DESCRIPTION,"45A"),Map.entry(LcCondition.ADDITIONAL_CONDITIONS,"47A"),Map.entry(LcCondition.AMOUNT_TOLERANCE,"39A"),
  Map.entry(LcCondition.PRESENTATION_PERIOD,"48"),Map.entry(LcCondition.PARTIAL_SHIPMENTS,"43P"),Map.entry(LcCondition.TRANSSHIPMENT,"43T"),
  Map.entry(LcCondition.PLACE_OF_RECEIPT,"44A"),Map.entry(LcCondition.PORT_OF_LOADING,"44E"),Map.entry(LcCondition.PORT_OF_DISCHARGE,"44F"),Map.entry(LcCondition.FINAL_DESTINATION,"44B"),
  Map.entry(LcCondition.APPLICABLE_RULES,"40E"));
 public static Optional<String> read(LetterOfCredit lc,LcCondition key){
  String tag=TAGS.get(key);
  var corrected=lc.getAdditionalFields().entrySet().stream()
   .filter(e->e.getKey()!=null&&Pattern.compile("(?i)^\\s*:?"+tag+"\\b").matcher(e.getKey()).find())
   .map(Map.Entry::getValue).filter(v->v!=null&&!v.isBlank()).findFirst();
  if(corrected.isPresent()||lc.getRawMessage()==null)return corrected;
  var match=Pattern.compile("(?ms)^:"+tag+":\\s*(.*?)(?=^:[0-9]{2}[A-Z]?:|\\z)").matcher(lc.getRawMessage());
  return match.find()?Optional.of(match.group(1).trim()):Optional.empty();
 }
}
