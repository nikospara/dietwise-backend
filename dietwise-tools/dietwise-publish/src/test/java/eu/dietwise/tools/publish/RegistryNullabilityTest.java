package eu.dietwise.tools.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.TreeSet;

import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.test.testcontainers.DockerImageNames;
import eu.dietwise.tools.publish.schema.ColumnSpec;
import eu.dietwise.tools.publish.schema.MirrorSpec;
import eu.dietwise.tools.publish.schema.TableSpec;
import eu.dietwise.tools.publish.schema.Tables;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Guards the {@code nullable} flags of the master columns in the {@link Tables} registry against the real schema: the
 * null-guard in {@link PublishPlanner} drops Working Copy rows based on these flags, so a wrong flag would silently drop
 * valid data or let a NOT NULL violation through. On a real PostgreSQL with the application schema applied, this asserts
 * that a registry master column is marked non-nullable exactly when the database column is NOT NULL.
 */
@Testcontainers
class RegistryNullabilityTest {

	@Container
	@SuppressWarnings("rawtypes")
	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageNames.POSTGRES_IMAGE);

	@Test
	void masterColumnNullabilityMatchesTheDatabase() throws Exception {
		LiquibaseExtension.executeUpdate(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), "changelog.xml", null);

		try (Connection connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
			TreeSet<String> registryNotNull = new TreeSet<>();
			TreeSet<String> databaseNotNull = new TreeSet<>();
			for (MirrorSpec mirror : Tables.all()) {
				TableSpec master = mirror.master();
				for (ColumnSpec column : master.columns()) {
					String qualified = master.name() + "." + column.name();
					if (!column.nullable()) {
						registryNotNull.add(qualified);
					}
					if (!isNullableInDatabase(connection, master.name(), column.name())) {
						databaseNotNull.add(qualified);
					}
				}
			}
			assertThat(registryNotNull).isEqualTo(databaseNotNull);
		}
	}

	private boolean isNullableInDatabase(Connection connection, String table, String column) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
			statement.setString(1, table.toLowerCase());
			statement.setString(2, column.toLowerCase());
			try (ResultSet resultSet = statement.executeQuery()) {
				assertThat(resultSet.next())
						.as("column %s.%s exists in the database", table, column)
						.isTrue();
				return "YES".equals(resultSet.getString(1));
			}
		}
	}
}
