package de.corporate.lc.user.api;
import de.corporate.lc.user.domain.*; import java.time.LocalDateTime; import java.util.UUID;
public record UserView(UUID id,String username,String displayName,UserRole role,boolean active,LocalDateTime createdAt){public static UserView of(AppUser u){return new UserView(u.getId(),u.getUsername(),u.getDisplayName(),u.getRole(),u.isActive(),u.getCreatedAt());}}
