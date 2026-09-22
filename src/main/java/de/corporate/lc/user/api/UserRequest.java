package de.corporate.lc.user.api;
import de.corporate.lc.user.domain.UserRole; import jakarta.validation.constraints.*;
public record UserRequest(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=255) String displayName,@Size(min=10,max=200) String password,@NotNull UserRole role,boolean active) {}
