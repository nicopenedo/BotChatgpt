package com.bottrading.saas.config;

import com.bottrading.saas.security.TenantContext;
import jakarta.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class TenantAwareDataSource extends DelegatingDataSource {

  public TenantAwareDataSource(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public Connection getConnection() throws SQLException {
    Connection connection = super.getConnection();
    applyTenant(connection);
    return connection;
  }

  @Override
  public Connection getConnection(@Nonnull String username, @Nonnull String password)
      throws SQLException {
    Connection connection = super.getConnection(username, password);
    applyTenant(connection);
    return connection;
  }

  private void applyTenant(Connection connection) throws SQLException {
    UUID tenantId = TenantContext.getTenantId();
    String tenantValue = tenantId != null ? tenantId.toString() : "";
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
      statement.setString(1, tenantValue);
      statement.execute();
    }
  }
}
