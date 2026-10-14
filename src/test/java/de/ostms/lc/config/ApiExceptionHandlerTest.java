package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
 @Test void theReasonOfAnExplicitStatusIsReturnedAsErrorText(){
  var response=new ApiExceptionHandler().status(new ResponseStatusException(HttpStatus.CONFLICT,"Befund nicht mehr aktuell. Bitte Prüfung neu laden."));
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(response.getBody()).containsEntry("error","Befund nicht mehr aktuell. Bitte Prüfung neu laden.");
  var without=new ApiExceptionHandler().status(new ResponseStatusException(HttpStatus.FORBIDDEN));
  assertThat(without.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);assertThat(without.getBody().get("error")).contains("403");
 }
}
