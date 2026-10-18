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
    private volatile Boolean postgres;

    public TenantSessionDataSource(DataSource target) { super(target); }

    @Override public Connection getConnection() throws SQLException { return bind(super.getConnection()); }

    @Override public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        try {
            if (postgres == null) postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
            if (postgres) {
                try (PreparedStatement statement = connection.prepareStatement("select set_config('" + SETTING + "', ?, false)")) {
                    statement.setString(1, TenantContext.currentId().toString());
                    statement.execute();
                }
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
    }
}
