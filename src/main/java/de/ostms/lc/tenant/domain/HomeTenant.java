package de.ostms.lc.tenant.domain;

import java.util.UUID;

/**
 * The tenant that holds the global user accounts and the platform sign-in. It is read from {@code platform_home_tenant}
 * at startup; until then (and in tests without that table) it is the legacy default tenant.
 */
public final class HomeTenant {
    private static volatile UUID id = Tenant.DEFAULT_ID;
    private HomeTenant() {}
    public static UUID id() { return id; }
    static void set(UUID tenant) { id = java.util.Objects.requireNonNull(tenant); }
}
