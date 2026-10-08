package de.ostms.lc.user.api;
public record ProfileView(String username, String displayName, String roleName, boolean totpEnabled, String avatarUrl, String email) { }
