package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
 @Test void theReasonOfAnExplicitStatusIsReturnedAsErrorText(){
  var response=new ApiExceptionHandler().status(new ResponseStatusException(HttpStatus.CONFLICT,"Befund nicht mehr aktuell. Bitte Prüfung neu laden."),new org.springframework.mock.web.MockHttpServletRequest());
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(response.getBody()).containsEntry("error","Befund nicht mehr aktuell. Bitte Prüfung neu laden.");
  var without=new ApiExceptionHandler().status(new ResponseStatusException(HttpStatus.FORBIDDEN),new org.springframework.mock.web.MockHttpServletRequest());
  assertThat(without.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(without.getBody().get("error")).contains("403");
 }
 @Test void theMessageFollowsTheLanguageHeaderOfTheRequest(){
  var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("X-LC-Language","en");
  var english=new ApiExceptionHandler().badRequest(new IllegalArgumentException("Bitte einen Kunden auswählen."),request);
  assertThat(english.getBody()).containsEntry("error","Please choose a customer.");
  var german=new ApiExceptionHandler().badRequest(new IllegalArgumentException("Bitte einen Kunden auswählen."),new org.springframework.mock.web.MockHttpServletRequest());
  assertThat(german.getBody()).containsEntry("error","Bitte einen Kunden auswählen.");
 }
}
