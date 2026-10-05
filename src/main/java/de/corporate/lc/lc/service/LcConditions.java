package de.corporate.lc.lc.service;
import de.corporate.lc.lc.domain.*;
import de.corporate.lc.swift.SwiftConditionAdapter;
import java.util.Optional;
/** Canonical values take precedence; absent values are supplied by the legacy import adapter. */
public final class LcConditions {
 private LcConditions(){}
 public static Optional<String> value(LetterOfCredit lc,LcCondition key){
  if(lc.getConditions().containsKey(key))return Optional.ofNullable(lc.getConditions().get(key)).filter(v->!v.isBlank());
  return SwiftConditionAdapter.read(lc,key);
 }
}
