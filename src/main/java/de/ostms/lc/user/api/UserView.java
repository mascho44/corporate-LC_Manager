package de.ostms.lc.user.api;
import de.ostms.lc.user.domain.*; import java.time.LocalDateTime; import java.util.*;
public record UserView(UUID id,String username,String displayName,UUID roleId,String roleName,UserRole role,Set<UserPermission> permissions,boolean active,LocalDateTime createdAt,String email){public static UserView of(AppUser u){AppRole r=u.getAssignedRole();return new UserView(u.getId(),u.getUsername(),u.getDisplayName(),r==null?null:r.getId(),r==null?u.getRole().name():r.getName(),u.getRole(),u.effectivePermissions(),u.isActive(),u.getCreatedAt(),u.getEmail());}}
