package de.corporate.lc.user.service;

import de.corporate.lc.user.api.UserRequest;
import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceEmailTest {
    @Test void newUsersRequireAndPersistContactEmail() {
        var users=mock(AppUserRepository.class);
        var roles=mock(AppRoleRepository.class);
        var encoder=mock(PasswordEncoder.class);
        var service=new UserService(users,roles,encoder,mock(TenantAdministrationLock.class));
        UUID roleId=UUID.randomUUID();
        var role=new AppRole();role.setName("Reader");role.setBaseRole(UserRole.VIEWER);
        when(roles.findById(roleId)).thenReturn(Optional.of(role));
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any())).thenAnswer(call->call.getArgument(0));
        assertThatThrownBy(()->service.create(new UserRequest("user","User",null,"Password123",roleId,true)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(users,never()).save(any());
        var saved=service.create(new UserRequest("user","User"," Contact@example.com ","Password123",roleId,true));
        assertThat(saved.email()).isEqualTo("Contact@example.com");
        assertThat(saved.username()).isEqualTo("user");
    }
}
