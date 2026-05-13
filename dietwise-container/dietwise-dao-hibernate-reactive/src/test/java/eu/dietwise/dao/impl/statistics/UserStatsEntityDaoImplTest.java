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
import eu.dietwise.dao.jpa.statistics.UserStatsEntity;
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
public class UserStatsEntityDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("c7a7416f-5cb4-46c8-be7f-5fd1ef7d84f8");
	private static final UUID USER_IDM_ID = UUID.fromString("66b9e1d7-1490-40f8-81af-a172f90931b6");

	private static final String APPLICATION_ID = "recipewatch";

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
	void testFindSetAndIncreaseStats(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserStatsEntityDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var user = new UserEntity();
			user.setId(USER_ID);
			user.setIdmId(USER_IDM_ID.toString());
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		UserStatsEntity userStatsEntity = factory.withTransaction(tx -> sut.findByUserId(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userStatsEntity).isNull();

		LocalDateTime lastLaunched1 = LocalDateTime.of(2026, 2, 26, 10, 0, 0);
		LocalDateTime returnedLastLaunched1 = factory.withTransaction(tx -> sut.setLastLaunched(tx, APPLICATION_ID, USER_ID, lastLaunched1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(returnedLastLaunched1).isNull();

		LocalDateTime lastLaunched2 = LocalDateTime.of(2026, 2, 27, 11, 0, 0);
		LocalDateTime returnedLastLaunched2 = factory.withTransaction(tx -> sut.setLastLaunched(tx, APPLICATION_ID, USER_ID, lastLaunched2))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(returnedLastLaunched2).isEqualTo(lastLaunched1);

		LocalDateTime lastSeen1 = LocalDateTime.of(2026, 2, 26, 10, 15, 0);
		LocalDateTime returnedLastSeen1 = factory.withTransaction(tx -> sut.setLastSeen(tx, APPLICATION_ID, USER_ID, lastSeen1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(returnedLastSeen1).isNull();

		LocalDateTime lastSeen2 = LocalDateTime.of(2026, 2, 26, 10, 15, 0);
		LocalDateTime returnedLastSeen2 = factory.withTransaction(tx -> sut.setLastSeen(tx, APPLICATION_ID, USER_ID, lastSeen2))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(returnedLastSeen2).isEqualTo(lastSeen1);

		Integer daysLaunched = factory.withTransaction(tx -> sut.increaseDaysLaunched(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(daysLaunched).isEqualTo(0);
		daysLaunched = factory.withTransaction(tx -> sut.increaseDaysLaunched(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(daysLaunched).isEqualTo(1);

		Integer recipesAssessed = factory.withTransaction(tx -> sut.increaseRecipesAssessed(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(recipesAssessed).isEqualTo(0);
		recipesAssessed = factory.withTransaction(tx -> sut.increaseRecipesAssessed(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(recipesAssessed).isEqualTo(1);

		userStatsEntity = factory.withTransaction(tx -> sut.findByUserId(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userStatsEntity).isNotNull();
		assertThat(userStatsEntity.getLastLaunched()).isEqualTo(lastLaunched2);
		assertThat(userStatsEntity.getLastSeen()).isEqualTo(lastSeen2);
		assertThat(userStatsEntity.getDaysLaunched()).isEqualTo(2);
		assertThat(userStatsEntity.getRecipesAssessed()).isEqualTo(2);

		// Test multiple updates; we should count queries, there should be exactly two (1 SELECT & 1 UPDATE)
		userStatsEntity = factory.withTransaction(tx ->
				sut.increaseDaysLaunched(tx, APPLICATION_ID, USER_ID)
						.flatMap(x -> sut.increaseRecipesAssessed(tx, APPLICATION_ID, USER_ID))
						.flatMap(x -> sut.findByUserId(tx, APPLICATION_ID, USER_ID))
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userStatsEntity.getDaysLaunched()).isEqualTo(3);
		assertThat(userStatsEntity.getRecipesAssessed()).isEqualTo(3);
	}

	/**
	 * Tests deletion, keep it last.
	 */
	@Test
	@Order(10)
	void testDeleteByUser(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserStatsEntityDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> sut.deleteByUser(tx, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		UserStatsEntity userStatsEntity = factory.withTransaction(tx -> sut.findByUserId(tx, APPLICATION_ID, USER_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userStatsEntity).isNull();
	}
}
