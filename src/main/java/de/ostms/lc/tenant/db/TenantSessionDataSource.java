package de.ostms.lc.tenant.db;

import de.ostms.lc.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Tells PostgreSQL which tenant a pooled connection currently serves, so row-level security policies can
 * read it with {@code current_setting('app.tenant_id')}. Set on every checkout; other databases are left alone.
 */
public final class TenantSessionDataSource extends DelegatingDataSource {
    public static final String SETTING = "app.tenant_id";
    public static final String PLATFORM_SETTING = "app.platform";
    private volatile Boolean postgres;
    /** Connections handed out to the current thread and not yet closed, so a tenant switch inside a transaction reaches them. */
    private final ThreadLocal<java.util.List<Connection>> active = ThreadLocal.withInitial(java.util.ArrayList::new);

    public TenantSessionDataSource(DataSource target) {
        super(target);
        TenantContext.onChange(this::rebind);
    }

    @Override public Connection getConnection() throws SQLException { return bind(super.getConnection()); }

    @Override public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        try {
            if (postgres == null) postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
            if (!postgres) return connection;
            apply(connection);
            return track(connection);
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
    }

    private static void apply(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('" + SETTING + "', ?, false), set_config('" + PLATFORM_SETTING + "', ?, false)")) {
            statement.setString(1, TenantContext.currentId().toString());
            statement.setString(2, TenantContext.platform() ? "on" : "off");
            statement.execute();
        }
    }

    private void rebind() {
        if (!Boolean.TRUE.equals(postgres)) return;
        for (Connection connection : active.get()) {
            try {
                apply(connection);
            } catch (SQLException failure) {
                throw new IllegalStateException("Mandant konnte in der Datenbanksitzung nicht gesetzt werden.", failure);
            }
        }
    }

    private Connection track(Connection connection) {
        var open = active.get();
        open.add(connection);
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        open.remove(connection);
                        if (open.isEmpty()) active.remove();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }
}
