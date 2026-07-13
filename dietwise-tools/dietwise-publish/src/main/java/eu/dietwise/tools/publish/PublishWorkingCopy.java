package eu.dietwise.tools.publish;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import eu.dietwise.tools.publish.model.PublishPlan;
import eu.dietwise.tools.publish.model.Row;

/**
 * Command-line entry point. Reads the database identified by the {@code QUARKUS_DATASOURCE_*} environment variables and
 * writes to standard output a Liquibase changeset that publishes the Working Copy to master and clears it, with a full
 * rollback. Diagnostics go to standard error so standard output carries only the changelog XML.
 *
 * <p>Optional arguments: {@code args[0]} the changeset id (default {@code publish_working_copy_<timestamp>}),
 * {@code args[1]} the changeset author (default {@code publish-tool}).
 */
public final class PublishWorkingCopy {

	private static final String DEFAULT_AUTHOR = "publish-tool";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private PublishWorkingCopy() {
	}

	public static void main(String[] args) {
		String url = System.getenv("QUARKUS_DATASOURCE_JDBC_URL");
		String user = System.getenv("QUARKUS_DATASOURCE_USERNAME");
		String password = System.getenv("QUARKUS_DATASOURCE_PASSWORD");
		if (url == null || user == null || password == null) {
			System.err.println("Set QUARKUS_DATASOURCE_JDBC_URL, QUARKUS_DATASOURCE_USERNAME and QUARKUS_DATASOURCE_PASSWORD to point at the database.");
			System.exit(2);
			return;
		}
		String changeSetId = args.length > 0 ? args[0] : defaultChangeSetId();
		String author = args.length > 1 ? args[1] : DEFAULT_AUTHOR;

		try (Connection connection = DriverManager.getConnection(url, user, password)) {
			String changelog = generate(connection, changeSetId, author);
			if (changelog == null) {
				System.err.println("The Working Copy is empty; nothing to publish.");
				return;
			}
			System.out.print(changelog);
		} catch (SQLException e) {
			System.err.println("Failed to generate the publish changeset: " + e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Reads the database and returns the publish changelog, or {@code null} if the Working Copy is empty (nothing to
	 * publish). Package-private so tests can drive the whole pipeline against a real connection.
	 */
	static String generate(Connection connection, String changeSetId, String author) throws SQLException {
		DatabaseReader reader = new DatabaseReader();
		Map<String, List<Row>> master = reader.readMaster(connection);
		Map<String, List<Row>> workingCopy = reader.readWorkingCopy(connection);
		PublishPlan plan = new PublishPlanner().plan(master, workingCopy);
		if (plan.isEmpty()) {
			return null;
		}
		return new ChangelogRenderer().render(plan, changeSetId, author);
	}

	private static String defaultChangeSetId() {
		return "publish_working_copy_" + LocalDateTime.now().format(TIMESTAMP);
	}
}
