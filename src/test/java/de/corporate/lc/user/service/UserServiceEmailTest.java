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
    @Test void platformIdentityCannotBeChangedOrDeletedByTenantAdministration(){
        var users=mock(AppUserRepository.class);var role=new AppRole();role.setName("Reader");role.setBaseRole(UserRole.VIEWER);
        var target=new AppUser();UUID id=UUID.randomUUID();org.springframework.test.util.ReflectionTestUtils.setField(target,"id",id);target.setUsername("admin");target.setDisplayName("Admin");target.setEmail("admin@example.invalid");target.setAssignedRole(role);target.setPlatformAdministrator(true);
        when(users.findById(id)).thenReturn(Optional.of(target));var encoder=mock(PasswordEncoder.class);var service=new UserService(users,mock(AppRoleRepository.class),encoder,mock(TenantAdministrationLock.class),new IdentityCredentialService(users,encoder));
        assertThatThrownBy(()->service.update(id,new UserRequest("admin","Admin","admin@example.invalid","ChangedPassword123",UUID.randomUUID(),true),"local-admin")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.delete(id,"local-admin")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(users,never()).delete(any());verifyNoInteractions(encoder);
    }
    @Test void newUsersRequireAndPersistContactEmail() {
        var users=mock(AppUserRepository.class);
        var roles=mock(AppRoleRepository.class);
        var encoder=mock(PasswordEncoder.class);
        var service=new UserService(users,roles,encoder,mock(TenantAdministrationLock.class),new IdentityCredentialService(users,encoder));
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
