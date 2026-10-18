package de.ostms.lc.tenant.db;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import de.ostms.lc.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class TenantSessionDataSourceTest {
    private Connection connection(String product, PreparedStatement statement) throws Exception {
        var connection = mock(Connection.class);
        var meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn(product);
        when(connection.getMetaData()).thenReturn(meta);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        return connection;
    }

    @Test
    void postgresConnectionGetsTheCurrentTenant() throws Exception {
        var statement = mock(PreparedStatement.class);
        var target = mock(DataSource.class);
        var postgres = connection("PostgreSQL", statement);
        when(target.getConnection()).thenReturn(postgres);
        var tenant = UUID.randomUUID();
        try (var scope = TenantContext.open(tenant)) {
            new TenantSessionDataSource(target).getConnection();
        }
        verify(statement).setString(1, tenant.toString());
        verify(statement).execute();
    }

    @Test
    void otherDatabasesAreLeftAlone() throws Exception {
        var statement = mock(PreparedStatement.class);
        var target = mock(DataSource.class);
        var h2 = connection("H2", statement);
        when(target.getConnection()).thenReturn(h2);
        new TenantSessionDataSource(target).getConnection();
        verifyNoInteractions(statement);
    }

    @Test
    void failedBindingClosesTheConnection() throws Exception {
        var statement = mock(PreparedStatement.class);
        doThrow(new java.sql.SQLException("boom")).when(statement).execute();
        var connection = connection("PostgreSQL", statement);
        var target = mock(DataSource.class);
        when(target.getConnection()).thenReturn(connection);
        try { new TenantSessionDataSource(target).getConnection(); } catch (java.sql.SQLException expected) { }
        verify(connection).close();
    }
}
