package de.ostms.lc.user.api;
import de.ostms.lc.user.domain.*;import java.util.*;
public record RoleView(UUID id,String name,UserRole baseRole,boolean systemRole,Set<UserPermission> permissions){public static RoleView of(AppRole role){return new RoleView(role.getId(),role.getName(),role.getBaseRole(),role.isSystemRole(),Set.copyOf(role.getPermissions()));}}
