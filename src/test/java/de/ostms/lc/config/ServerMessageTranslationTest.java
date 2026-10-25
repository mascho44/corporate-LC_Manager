package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import static org.junit.jupiter.api.Assertions.*;

class ServerMessageTranslationTest {
 @Test void bothJsonConvertersAreTranslatedButNotOtherOnes(){
  var advice=new ServerMessageTranslation();
  assertTrue(advice.supports(null,JacksonJsonHttpMessageConverter.class));
  assertTrue(advice.supports(null,MappingJackson2HttpMessageConverter.class));
  assertFalse(advice.supports(null,StringHttpMessageConverter.class));
 }
}
