package de.ostms.lc.user.api;
import jakarta.validation.constraints.*;import java.util.UUID;
public record UserRequest(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=255) String displayName,@NotBlank @Email @Size(max=255) String email,@Size(min=10,max=200) String password,@NotNull UUID roleId,boolean active) {}
