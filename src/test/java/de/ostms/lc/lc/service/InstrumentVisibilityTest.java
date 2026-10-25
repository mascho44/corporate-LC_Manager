package de.ostms.lc.lc.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentVisibilityTest {
 @Test void usersLoseTheKindsTheyMayNotRead(){
  assertArrayEquals(new boolean[]{false,false},InstrumentVisibility.hiddenFor(List.of("ROLE_EDITOR","PERM_LC_VIEW","PERM_GUARANTEE_VIEW")));
  assertArrayEquals(new boolean[]{false,true},InstrumentVisibility.hiddenFor(List.of("ROLE_EDITOR","PERM_LC_VIEW")));
  assertArrayEquals(new boolean[]{true,false},InstrumentVisibility.hiddenFor(List.of("ROLE_VIEWER","PERM_GUARANTEE_VIEW")));
  assertArrayEquals(new boolean[]{true,true},InstrumentVisibility.hiddenFor(List.of("ROLE_VIEWER")));
 }
 @Test void keysAndTokensAreNotRestricted(){
  assertArrayEquals(new boolean[]{false,false},InstrumentVisibility.hiddenFor(List.of("PERM_API_READ")));
  assertArrayEquals(new boolean[]{false,false},InstrumentVisibility.hiddenFor(List.of("PERM_DOCUMENT_UPLOAD")));
 }
 @Test void holderIsPerRequest(){
  InstrumentVisibility.set(false,true);assertTrue(InstrumentVisibility.hideGuarantees());assertFalse(InstrumentVisibility.hideLetters());assertTrue(InstrumentVisibility.restricted());
  InstrumentVisibility.clear();assertFalse(InstrumentVisibility.restricted());assertFalse(InstrumentVisibility.hideGuarantees());
 }
}
