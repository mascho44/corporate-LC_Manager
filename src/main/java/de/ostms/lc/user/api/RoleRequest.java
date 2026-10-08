package de.ostms.lc.user.api;
import de.ostms.lc.user.domain.*;import jakarta.validation.constraints.*;import java.util.Set;
public record RoleRequest(@NotBlank @Size(max=100) String name,@NotNull UserRole baseRole,@NotNull Set<UserPermission> permissions){}
