package de.ostms.lc.user.service;

import de.ostms.lc.user.api.UserRequest;
import de.ostms.lc.user.api.ProfileController;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class UserEmailTest {
    @Test void addressIsRequiredAndNormalizedWithoutChangingLocalPart() {
        assertThat(UserEmail.required("  Contact@example.com  ")).isEqualTo("Contact@example.com");
        for(String value:new String[]{"", " ", "invalid", "a@", "Name <a@example.com>", "a@example.com,b@example.com", "a\n@example.com"})
            assertThatThrownBy(()->UserEmail.required(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->UserEmail.required(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->UserEmail.required("a".repeat(256)+"@example.com")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void userAndProfileRequestsRejectMissingOrInvalidEmails() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(String email:new String[]{"", "not-an-email"}) {
                assertThat(validator.validate(new UserRequest("user","User",email,"Password123",UUID.randomUUID(),true)))
                    .anyMatch(v->v.getPropertyPath().toString().equals("email"));
                assertThat(validator.validate(new ProfileController.UpdateRequest("User",email)))
                    .anyMatch(v->v.getPropertyPath().toString().equals("email"));
            }
            assertThat(validator.validate(new ProfileController.UpdateRequest("User","user@example.com"))).isEmpty();
        }
    }
}
