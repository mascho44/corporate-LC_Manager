package de.ostms.lc.user.api;
import de.ostms.lc.user.domain.UserPermission;
public record PermissionView(UserPermission value,String label){}
