package eu.dietwise.dao.impl.statistics;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.dao.jpa.statistics.UserRecipeStatsEntity;
import eu.dietwise.dao.jpa.statistics.UserRecipeStatsId;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class UserRecipeStatsEntityDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("f6f81cb8-f42c-48f6-ae60-2a31b33112a3");
	private static final UUID USER_IDM_ID = UUID.fromString("10c3e6dd-32e2-481c-b06f-bb51f01b3ae2");

	private static final String APPLICATION_ID = "recipewatch";
	private static final String RECIPE_URL_1 = "https://example.test/recipe-1";
	private static final String RECIPE_URL_2 = "https://example.test/recipe-2";
	private static final String RECIPE_NAME_1 = "Simple Pasta";
	private static final String RECIPE_NAME_2 = "Better Pasta";
	private static final String RECIPE_NAME_3 = "Simple Salad";

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE);

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final LiquibaseExtension liquibaseExtension =
			new LiquibaseExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@RegisterExtension
	@SuppressWarnings("unused")
	private static final HibernateReactiveExtension hibernateReactiveExtension =
			new HibernateReactiveExtension(postgres::getJdbcUrl, postgres.getUsername(), postgres.getPassword());

	@Test
	@Order(1)
	void testIncreaseTimesAssessedCreatesAndUpdatesStats(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserRecipeStatsEntityDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var user = new UserEntity();
			user.setId(USER_ID);
			user.setIdmId(USER_IDM_ID.toString());
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		LocalDateTime lastAssessed1 = LocalDateTime.of(2026, 4, 9, 20, 0, 0);
		Integer timesAssessed = factory.withTransaction(tx ->
						sut.increaseTimesAssessed(tx, APPLICATION_ID, USER_ID, RECIPE_URL_1, RECIPE_NAME_1, lastAssessed1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(timesAssessed).isEqualTo(1);

		LocalDateTime lastAssessed2 = LocalDateTime.of(2026, 4, 9, 20, 5, 0);
		timesAssessed = factory.withTransaction(tx ->
						sut.increaseTimesAssessed(tx, APPLICATION_ID, USER_ID, RECIPE_URL_1, RECIPE_NAME_2, lastAssessed2))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(timesAssessed).isEqualTo(2);

		LocalDateTime lastAssessed3 = LocalDateTime.of(2026, 4, 9, 20, 10, 0);
		timesAssessed = factory.withTransaction(tx ->
						sut.increaseTimesAssessed(tx, APPLICATION_ID, USER_ID, RECIPE_URL_2, RECIPE_NAME_3, lastAssessed3))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(timesAssessed).isEqualTo(1);

		UserRecipeStatsEntity recipeStatsEntity1 = factory.withTransaction(tx ->
						tx.find(UserRecipeStatsEntity.class, new UserRecipeStatsId(USER_ID, APPLICATION_ID, RECIPE_URL_1)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(recipeStatsEntity1).isNotNull();
		assertThat(recipeStatsEntity1.getRecipeName()).isEqualTo(RECIPE_NAME_2);
		assertThat(recipeStatsEntity1.getTimesAssessed()).isEqualTo(2);
		assertThat(recipeStatsEntity1.getLastAssessed()).isEqualTo(lastAssessed2);

		UserRecipeStatsEntity recipeStatsEntity2 = factory.withTransaction(tx ->
						tx.find(UserRecipeStatsEntity.class, new UserRecipeStatsId(USER_ID, APPLICATION_ID, RECIPE_URL_2)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(recipeStatsEntity2).isNotNull();
		assertThat(recipeStatsEntity2.getRecipeName()).isEqualTo(RECIPE_NAME_3);
		assertThat(recipeStatsEntity2.getTimesAssessed()).isEqualTo(1);
		assertThat(recipeStatsEntity2.getLastAssessed()).isEqualTo(lastAssessed3);
	}

	/**
	 * Tests deletion, keep it last.
	 */
	@Test
	@Order(10)
	void testDeleteByUser(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserRecipeStatsEntityDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> sut.deleteByUser(tx, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		UserRecipeStatsEntity recipeStatsEntity1 = factory.withTransaction(tx ->
						tx.find(UserRecipeStatsEntity.class, new UserRecipeStatsId(USER_ID, APPLICATION_ID, RECIPE_URL_1)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		UserRecipeStatsEntity recipeStatsEntity2 = factory.withTransaction(tx ->
						tx.find(UserRecipeStatsEntity.class, new UserRecipeStatsId(USER_ID, APPLICATION_ID, RECIPE_URL_2)))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(recipeStatsEntity1).isNull();
		assertThat(recipeStatsEntity2).isNull();
	}
}
