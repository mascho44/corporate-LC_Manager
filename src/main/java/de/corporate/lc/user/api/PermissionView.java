package de.corporate.lc.user.api;
import de.corporate.lc.user.domain.UserPermission;
public record PermissionView(UserPermission value,String label){}
