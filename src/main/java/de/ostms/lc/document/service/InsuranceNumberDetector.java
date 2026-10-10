package de.ostms.lc.document.service;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.regex.Pattern;

/** Policy or certificate number printed on an insurance document; only explicitly labelled numbers, and only when unambiguous. */
final class InsuranceNumberDetector {
 private static final Pattern LABEL=Pattern.compile("(?im)(?<![\\p{L}\\p{N}])(?:(?:insurance\\s+)?(?:certificate|policy|cover\\s*note)|certificate\\s+of\\s+insurance|policen|zertifikats|versicherungsschein|versicherungszertifikat)[\\t ]*+(?:no\\.?|number|nr\\.?|nummer|-?nr\\.?|#)[\\t ]*+[:#-]?[\\t ]*+(?:\\r?\\n[\\t ]*+)?([A-Z0-9][A-Z0-9./_-]{3,}+)(?![A-Z0-9./_-])");
 private InsuranceNumberDetector(){}
 static Optional<String> detect(String text){
  if(text==null)return Optional.empty();
  var values=new LinkedHashSet<String>();
  var matcher=LABEL.matcher(text.substring(0,Math.min(text.length(),100_000)));
  while(matcher.find()){String value=matcher.group(1);if(value.chars().anyMatch(Character::isDigit))values.add(value);}
  return values.size()==1?Optional.of(values.iterator().next()):Optional.empty();
 }
}
