package de.ostms.lc.tenant.domain;

import java.util.UUID;

/** Single writer for {@link HomeTenant}; only the startup loader may set it. */
public final class HomeTenantAccess {
    private HomeTenantAccess() {}
    public static void set(UUID tenant) { HomeTenant.set(tenant); }
}
