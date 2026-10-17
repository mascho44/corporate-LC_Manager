package de.ostms.lc.tenant.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TenantOcrToleranceTest {
 @Test void defaultsToNothingAndAcceptsTheAllowedRange() {
  var tenant=new Tenant("t1","T1","de",true,true);
  assertThat(tenant.getOcrConfidenceThreshold()).isNull();
  tenant.setOcrConfidenceThreshold(0.7);assertThat(tenant.getOcrConfidenceThreshold()).isEqualTo(0.7);
  tenant.setOcrConfidenceThreshold(0.5);tenant.setOcrConfidenceThreshold(0.95);
  tenant.setOcrConfidenceThreshold(null);assertThat(tenant.getOcrConfidenceThreshold()).isNull();
 }
 @Test void rejectsValuesOutsideTheRange() {
  var tenant=new Tenant("t1","T1","de",true,true);
  for(double bad:new double[]{0.49,0.96,0,1,-1,Double.NaN,Double.POSITIVE_INFINITY})assertThatThrownBy(()->tenant.setOcrConfidenceThreshold(bad)).isInstanceOf(IllegalArgumentException.class);
 }
}
