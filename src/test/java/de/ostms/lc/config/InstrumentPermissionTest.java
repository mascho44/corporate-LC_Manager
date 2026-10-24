package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentPermissionTest {
 @Test void guaranteeNeedsItsOwnRight(){
  assertTrue(InstrumentPermissionConfiguration.refused(true,true,false),"letter-of-credit editor must not edit guarantees");
  assertFalse(InstrumentPermissionConfiguration.refused(true,false,true));
  assertFalse(InstrumentPermissionConfiguration.refused(true,true,true));
 }
 @Test void letterOfCreditNeedsItsOwnRight(){
  assertTrue(InstrumentPermissionConfiguration.refused(false,false,true),"guarantee editor must not edit letters of credit");
  assertFalse(InstrumentPermissionConfiguration.refused(false,true,false));
  assertFalse(InstrumentPermissionConfiguration.refused(false,true,true));
 }
}
