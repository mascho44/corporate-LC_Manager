package de.corporate.lc.user.api;
public record ProfileView(String username, String displayName, String roleName, boolean totpEnabled, String avatarUrl) { }
