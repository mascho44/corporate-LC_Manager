package de.corporate.lc.user.service;

import de.corporate.lc.user.domain.*;
import de.corporate.lc.user.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ProfileServiceTest {
    final AppUserRepository users=mock(AppUserRepository.class);
    final UserAvatarRepository avatars=mock(UserAvatarRepository.class);
    final ProfileService service=new ProfileService(users,avatars);
    final AppUser user=new AppUser();
    final UUID userId=UUID.randomUUID();
    final AtomicReference<UserAvatar> saved=new AtomicReference<>();

    @BeforeEach void setup(){
        ReflectionTestUtils.setField(user,"id",userId);user.setUsername("viewer");user.setDisplayName("Viewer");user.setRole(UserRole.VIEWER);user.setPasswordHash("unchanged");
        when(users.findByUsernameIgnoreCase("viewer")).thenReturn(Optional.of(user));
        when(avatars.findById(userId)).thenAnswer(call->Optional.ofNullable(saved.get()));
        when(avatars.save(any())).thenAnswer(call->{saved.set(call.getArgument(0));return saved.get();});
    }

    @Test void ownNameCanChangeWithoutChangingRoleOrPassword(){
        var profile=service.updateName("viewer","  New Name  ");
        assertThat(profile.displayName()).isEqualTo("New Name");assertThat(user.getRole()).isEqualTo(UserRole.VIEWER);assertThat(user.getPasswordHash()).isEqualTo("unchanged");
        assertThatThrownBy(()->service.updateName("viewer"," ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void realImageIsCroppedResizedAndStoredForOwnAccount() throws Exception {
        var profile=service.uploadAvatar("viewer",new MockMultipartFile("file","photo.png","image/png",image(800,600)));
        BufferedImage result=ImageIO.read(new ByteArrayInputStream(service.avatar("viewer")));
        assertThat(result.getWidth()).isEqualTo(512);assertThat(result.getHeight()).isEqualTo(512);
        assertThat(saved.get().getUserId()).isEqualTo(userId);assertThat(profile.avatarUrl()).startsWith("/api/profile/avatar?v=");
        assertThat(user.getRole()).isEqualTo(UserRole.VIEWER);
    }

    @Test void spoofedImageAndExcessiveDimensionsAreRejected() throws Exception {
        assertThatThrownBy(()->service.uploadAvatar("viewer",new MockMultipartFile("file","photo.png","image/png","<svg/>".getBytes()))).isInstanceOf(IllegalArgumentException.class);
        var wide=image(9000,1);
        assertThatThrownBy(()->service.uploadAvatar("viewer",new MockMultipartFile("file","wide.png","image/png",wide))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.uploadAvatar("viewer",new MockMultipartFile("file","large.jpg","image/jpeg",new byte[5*1024*1024+1]))).isInstanceOf(IllegalArgumentException.class);
        verify(avatars,never()).save(any());
    }

    @Test void removingAvatarReturnsInitialsState() throws Exception {
        service.uploadAvatar("viewer",new MockMultipartFile("file","photo.png","image/png",image(16,16)));
        var result=service.deleteAvatar("viewer");assertThat(result.avatarUrl()).isNull();verify(avatars).delete(saved.get());
    }

    private byte[] image(int width,int height) throws IOException {
        try(var output=new ByteArrayOutputStream()){ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();}
    }
}
