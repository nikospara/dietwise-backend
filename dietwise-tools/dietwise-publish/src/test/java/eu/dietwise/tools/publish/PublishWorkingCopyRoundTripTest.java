package eu.dietwise.tools.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.test.testcontainers.DockerImageNames;
import eu.dietwise.tools.publish.schema.MirrorSpec;
import eu.dietwise.tools.publish.schema.Tables;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end: on a real PostgreSQL, apply the application schema, seed master data and a Working Copy, generate the
 * publish changeset with the tool, apply it with Liquibase and assert the publish took effect and the Working Copy was
 * cleared; then roll it back with Liquibase and assert both the master data and the Working Copy are fully restored.
 */
@Testcontainers
class PublishWorkingCopyRoundTripTest {

	private static final String REC1 = "00000000-0000-0000-0000-000000000001";
	private static final String TI1 = "00000000-0000-0000-0000-000000000011";
	private static final String TI2 = "00000000-0000-0000-0000-000000000012";
	private static final String ALT1 = "00000000-0000-0000-0000-0000000000a1";
	private static final String RULE1 = "00000000-0000-0000-0000-0000000000f1";
	private static final String TEMPLATE_ORPHAN = "00000000-0000-0000-0000-0000000000b1";
	private static final String MISSING_RULE = "00000000-0000-0000-0000-0000000000ff";

	private static final String CHANGELOG_FILE = "publish.xml";

	@Container
	@SuppressWarnings("rawtypes")
	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageNames.POSTGRES_IMAGE);

	@Test
	void publishesTheWorkingCopyAndRollsBackToTheOriginalState(@TempDir Path changelogDir) throws Exception {
		LiquibaseExtension.executeUpdate(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), "changelog.xml", null);

		try (Connection connection = newConnection()) {
			seedMasterAndWorkingCopy(connection);

			String changelog = PublishWorkingCopy.generate(connection, "publish_working_copy_it", "test");
			assertThat(changelog).isNotNull();
			Path changelogFile = changelogDir.resolve(CHANGELOG_FILE);
			Files.writeString(changelogFile, changelog, StandardCharsets.UTF_8);

			liquibaseUpdate(changelogDir);

			assertPublished(connection);

			liquibaseRollback(changelogDir);

			assertRestored(connection);
		}
	}

	private void seedMasterAndWorkingCopy(Connection connection) throws Exception {
		// clearly-unique names so this test coexists with the production seed data loaded by changelog.xml
		exec(connection, "INSERT INTO dw_recommendation(id,name,component_for_scoring,type_of_recommendation,explanation_for_llm,"
				+ "human_friendly_display) VALUES ('" + REC1 + "','ZZ Test Reduce beef','zz-test-beef','LIMITED','expl','Beef')");
		exec(connection, "INSERT INTO dw_trigger_ingredient(id,name,explanation_for_llm) VALUES ('" + TI1
				+ "','ZZ Test Beef','red meat')");
		exec(connection, "INSERT INTO dw_alternative_ingredient(id,name,explanation_for_llm) VALUES ('" + ALT1
				+ "','ZZ Test Lentils','legume')");
		exec(connection, "INSERT INTO dw_rule(id,recommendation_id,trigger_ingredient_id,role_or_technique_id,cuisine,"
				+ "rationale,active) VALUES ('" + RULE1 + "','" + REC1 + "','" + TI1 + "',NULL,NULL,'old rationale',true)");
		exec(connection, "INSERT INTO dw_alternative_ingredient_seasonality(alternative_ingredient_id,country,month_from,"
				+ "month_to) VALUES ('" + ALT1 + "','GR',3,8)");

		// staged: a new trigger ingredient, a changed rule rationale, a new component-for-scoring link, a removed seasonality
		exec(connection, "INSERT INTO dw_trigger_ingredient_wc(id,name,explanation_for_llm,version) VALUES ('" + TI2
				+ "','ZZ Test Butter',NULL,1)");
		exec(connection, "INSERT INTO dw_rule_wc(id,recommendation_id,trigger_ingredient_id,role_or_technique_id,cuisine,"
				+ "rationale,active,version) VALUES ('" + RULE1 + "','" + REC1 + "','" + TI1
				+ "',NULL,NULL,'new rationale',true,2)");
		exec(connection, "INSERT INTO dw_alternative_ingredient_components_for_scoring_wc(alternative_ingredient_id,"
				+ "recommendation_id,present) VALUES ('" + ALT1 + "','" + REC1 + "',true)");
		exec(connection, "INSERT INTO dw_alternative_ingredient_seasonality_wc(alternative_ingredient_id,country,"
				+ "month_from,month_to,version) VALUES ('" + ALT1 + "','GR',NULL,NULL,2)");

		// staged: a suggestion template whose Rule exists nowhere (an orphan, as found in real data); it must be
		// dropped - never published to master and never restored on rollback
		exec(connection, "INSERT INTO dw_suggestion_template_wc(id,rule_id,alternative_ingredient_id,alternative_order,"
				+ "restriction,equivalence,technique_notes,version) VALUES ('" + TEMPLATE_ORPHAN + "','" + MISSING_RULE
				+ "','" + ALT1 + "',0,NULL,NULL,NULL,1)");

		// staged: a translation whose backoffice user forgot the required name; publishing it would insert null into
		// the NOT NULL master column, so it must be dropped - never published to master and never restored on rollback
		exec(connection, "INSERT INTO dw_trigger_ingredient_translation_wc(trigger_ingredient_id,lang,name,"
				+ "explanation_for_llm,version) VALUES ('" + TI1 + "','EL',NULL,'red meat EL',1)");
	}

	private void assertPublished(Connection connection) throws Exception {
		assertThat(count(connection, "SELECT count(*) FROM dw_trigger_ingredient WHERE id='" + TI2 + "'")).isEqualTo(1);
		assertThat(scalar(connection, "SELECT rationale FROM dw_rule WHERE id='" + RULE1 + "'"))
				.isEqualTo("new rationale");
		assertThat(count(connection, "SELECT count(*) FROM dw_alternative_ingredient_components_for_scoring WHERE "
				+ "alternative_ingredient_id='" + ALT1 + "' AND recommendation_id='" + REC1 + "'")).isEqualTo(1);
		assertThat(count(connection, "SELECT count(*) FROM dw_alternative_ingredient_seasonality WHERE "
				+ "alternative_ingredient_id='" + ALT1 + "' AND country='GR'")).isEqualTo(0);
		// the orphan template was dropped: not published to master
		assertThat(count(connection, "SELECT count(*) FROM dw_suggestion_template WHERE id='" + TEMPLATE_ORPHAN + "'"))
				.isZero();
		// the translation missing its required name was dropped: not published to master
		assertThat(count(connection, "SELECT count(*) FROM dw_trigger_ingredient_translation WHERE "
				+ "trigger_ingredient_id='" + TI1 + "' AND lang='EL'")).isZero();
		assertThat(totalWorkingCopyRows(connection)).isZero();
	}

	private void assertRestored(Connection connection) throws Exception {
		assertThat(count(connection, "SELECT count(*) FROM dw_trigger_ingredient WHERE id='" + TI2 + "'")).isEqualTo(0);
		assertThat(scalar(connection, "SELECT rationale FROM dw_rule WHERE id='" + RULE1 + "'"))
				.isEqualTo("old rationale");
		assertThat(count(connection, "SELECT count(*) FROM dw_alternative_ingredient_components_for_scoring WHERE "
				+ "alternative_ingredient_id='" + ALT1 + "' AND recommendation_id='" + REC1 + "'")).isEqualTo(0);
		assertThat(scalar(connection, "SELECT month_from FROM dw_alternative_ingredient_seasonality WHERE "
				+ "alternative_ingredient_id='" + ALT1 + "' AND country='GR'")).isEqualTo("3");
		// the Working Copy is restored to its four publishable staged rows; the dropped rows are not brought back
		assertThat(totalWorkingCopyRows(connection)).isEqualTo(4);
		assertThat(count(connection, "SELECT count(*) FROM dw_trigger_ingredient_wc WHERE id='" + TI2 + "'")).isEqualTo(1);
		assertThat(scalar(connection, "SELECT month_from FROM dw_alternative_ingredient_seasonality_wc WHERE "
				+ "alternative_ingredient_id='" + ALT1 + "' AND country='GR'")).isNull();
		assertThat(count(connection, "SELECT count(*) FROM dw_suggestion_template_wc WHERE id='" + TEMPLATE_ORPHAN + "'"))
				.isZero();
		assertThat(count(connection, "SELECT count(*) FROM dw_trigger_ingredient_translation_wc WHERE "
				+ "trigger_ingredient_id='" + TI1 + "' AND lang='EL'")).isZero();
	}

	private long totalWorkingCopyRows(Connection connection) throws Exception {
		long total = 0;
		for (MirrorSpec mirror : Tables.all()) {
			total += count(connection, "SELECT count(*) FROM " + mirror.wc().name());
		}
		return total;
	}

	private void liquibaseUpdate(Path changelogDir) throws Exception {
		try (Connection connection = newConnection();
				DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(changelogDir)) {
			Database database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(new JdbcConnection(connection));
			new Liquibase(CHANGELOG_FILE, accessor, database).update(new Contexts());
		}
	}

	private void liquibaseRollback(Path changelogDir) throws Exception {
		try (Connection connection = newConnection();
				DirectoryResourceAccessor accessor = new DirectoryResourceAccessor(changelogDir)) {
			Database database = DatabaseFactory.getInstance()
					.findCorrectDatabaseImplementation(new JdbcConnection(connection));
			new Liquibase(CHANGELOG_FILE, accessor, database).rollback(1, new Contexts(), new LabelExpression());
		}
	}

	private Connection newConnection() throws Exception {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private static void exec(Connection connection, String sql) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

	private static long count(Connection connection, String sql) throws Exception {
		return Long.parseLong(scalar(connection, sql));
	}

	private static String scalar(Connection connection, String sql) throws Exception {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getString(1);
		}
	}
}
