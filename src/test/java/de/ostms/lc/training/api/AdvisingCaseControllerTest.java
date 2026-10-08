package de.ostms.lc.training.api;
import de.ostms.lc.training.service.AdvisingCaseService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Validation;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdvisingCaseControllerTest {
 @Test void everyPermissionIsRequiredForPreviewAndWrite(){
  var service=mock(AdvisingCaseService.class);var controller=new AdvisingCaseController(service);UUID id=UUID.randomUUID();
  List<String> permissions=List.of("PERM_TRAINING_MANAGE","PERM_LC_EDIT","PERM_DOCUMENT_UPLOAD");
  for(String missing:permissions){
   var auth=new UsernamePasswordAuthenticationToken("user","unused",permissions.stream().filter(p->!p.equals(missing)).map(SimpleGrantedAuthority::new).toList());
   assertThatThrownBy(()->controller.preview(id,auth)).isInstanceOf(AccessDeniedException.class);
   assertThatThrownBy(()->controller.create(id,null,auth)).isInstanceOf(AccessDeniedException.class);
  }
  verifyNoInteractions(service);
 }
 @Test void missingCaseDataIsRejectedByBeanValidation(){
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var invalid=new AdvisingNewCaseRequest(" ",null,null," ",null,null,"eur",null,null,null);
   assertThat(factory.getValidator().validate(invalid).stream().map(v->v.getPropertyPath().toString())).contains("reference","applicant","beneficiary","amount","currency","expiryDate");
  }
 }
}
