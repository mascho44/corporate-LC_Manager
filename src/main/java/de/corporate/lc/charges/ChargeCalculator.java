package de.corporate.lc.charges;
import java.math.*;
import java.util.*;
public final class ChargeCalculator {
 private ChargeCalculator(){}
 public record Line(ChargeType type,int units,BigDecimal perUnit,BigDecimal amount){}
 public record Result(String currency,BigDecimal basis,List<Line> lines,BigDecimal total,String method){}
 public static Result calculate(BigDecimal basis,String currency,List<ChargeRule> rules,Map<ChargeType,Integer> units){
  if(basis==null||basis.signum()<0)throw new IllegalArgumentException("LC-Betrag fehlt oder ist ungültig.");
  int scale=Currency.getInstance(currency).getDefaultFractionDigits();if(scale<0)throw new IllegalArgumentException("Währung hat keine definierte Rundung.");
  var tariffs=new EnumMap<ChargeType,ChargeRule>(ChargeType.class);
  for(var rule:rules){if(tariffs.put(rule.type(),rule)!=null)throw new IllegalArgumentException("Gebührentyp doppelt im Profil.");if(rule.maximum()!=null&&rule.maximum().compareTo(rule.minimum())<0)throw new IllegalArgumentException("Höchstgebühr liegt unter Mindestgebühr.");}
  var lines=new ArrayList<Line>();BigDecimal total=BigDecimal.ZERO.setScale(scale);
  for(var type:ChargeType.values()){
   int count=units.getOrDefault(type,0);if(count<0||count>10000)throw new IllegalArgumentException("Ungültige Anzahl Gebühreneinheiten.");if(count==0)continue;
   var rule=tariffs.get(type);if(rule==null)throw new IllegalArgumentException("Kein Tarif für "+type+" im Profil.");
   var perUnit=basis.multiply(rule.percent().movePointLeft(2)).add(rule.fixed()).max(rule.minimum());
   if(rule.maximum()!=null)perUnit=perUnit.min(rule.maximum());
   var amount=perUnit.multiply(BigDecimal.valueOf(count)).setScale(scale,RoundingMode.HALF_UP);
   lines.add(new Line(type,count,perUnit,amount));total=total.add(amount);
  }
  if(lines.isEmpty())throw new IllegalArgumentException("Mindestens eine Gebühreneinheit angeben.");
  return new Result(currency,basis,List.copyOf(lines),total,"LC-Betrag × Prozentsatz + Festbetrag; Minimum/Maximum je Einheit, danach Anzahl und Währungsrundung HALF_UP. Schätzung ohne Steuern, FX oder automatische Laufzeitannahmen.");
 }
}
