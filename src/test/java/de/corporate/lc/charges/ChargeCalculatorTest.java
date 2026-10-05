package de.corporate.lc.charges;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ChargeCalculatorTest {
 private BigDecimal n(String value){return new BigDecimal(value);}
 @Test void appliesPercentageFixedMinimumAndUnits(){
  var rule=new ChargeRule(ChargeType.OPENING,n("0.1"),n("5"),n("50"),null);
  var result=ChargeCalculator.calculate(n("10000"),"EUR",List.of(rule),Map.of(ChargeType.OPENING,2));
  assertThat(result.total()).isEqualByComparingTo("100.00");assertThat(result.lines().get(0).perUnit()).isEqualByComparingTo("50");
 }
 @Test void maximumIsAppliedBeforeMultiplyingUnits(){var rule=new ChargeRule(ChargeType.CONFIRMATION,n("1"),n("5"),n("0"),n("60"));var result=ChargeCalculator.calculate(n("10000"),"EUR",List.of(rule),Map.of(ChargeType.CONFIRMATION,3));assertThat(result.total()).isEqualByComparingTo("180.00");}
 @Test void roundsLineTotalUsingCurrencyNotBinaryFloatingPoint(){var rule=new ChargeRule(ChargeType.ADVISING,n("0"),n("1.005"),n("0"),null);assertThat(ChargeCalculator.calculate(n("100"),"EUR",List.of(rule),Map.of(ChargeType.ADVISING,1)).total()).isEqualByComparingTo("1.01");assertThat(ChargeCalculator.calculate(n("100"),"JPY",List.of(rule),Map.of(ChargeType.ADVISING,1)).total().scale()).isZero();}
 @Test void rejectsDuplicatesInvalidCapsMissingTariffsAndEmptyUnits(){
  var rule=new ChargeRule(ChargeType.OPENING,n("0"),n("1"),n("0"),null);
  assertThatThrownBy(()->ChargeCalculator.calculate(n("100"),"EUR",List.of(rule,rule),Map.of(ChargeType.OPENING,1))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->ChargeCalculator.calculate(n("100"),"EUR",List.of(new ChargeRule(ChargeType.OPENING,n("0"),n("1"),n("2"),n("1"))),Map.of(ChargeType.OPENING,1))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->ChargeCalculator.calculate(n("100"),"EUR",List.of(rule),Map.of(ChargeType.EXAMINATION,1))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->ChargeCalculator.calculate(n("100"),"EUR",List.of(rule),Map.of())).isInstanceOf(IllegalArgumentException.class);
 }
}
