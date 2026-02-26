package eu.dietwise.dao.impl.statistics;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
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
public class UserSuggestionStatsEntityDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("509f2670-b04d-4f5e-8a2b-4a08f3c87062");
	private static final UUID USER_IDM_ID = UUID.fromString("0bc0df44-f71d-43ed-a388-38f04300f452");

	private static final SuggestionTemplateId SUGGESTION_ID_1 =
			new GenericSuggestionTemplateId("90984ed6-5e6b-4381-ac0d-92b6dd3cf503");
	private static final SuggestionTemplateId SUGGESTION_ID_2 =
			new GenericSuggestionTemplateId("ce09b981-0d78-499a-9430-42a324d665a4");

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
	void testRetrieveAndUpdateStats(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new UserSuggestionStatsEntityDaoImpl();
		var userId = new UserIdImpl(USER_ID.toString());
		Set<SuggestionTemplateId> ids = new LinkedHashSet<>();
		ids.add(SUGGESTION_ID_1);
		ids.add(SUGGESTION_ID_2);

		factory.withTransaction(tx -> {
			var user = new UserEntity();
			user.setId(USER_ID);
			user.setIdmId(USER_IDM_ID.toString());
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var totalStats = factory.withTransaction(tx -> sut.retrieveTotalSuggestionStats(tx, APPLICATION_ID, ids))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertZeroStats(totalStats.get(SUGGESTION_ID_1));
		assertZeroStats(totalStats.get(SUGGESTION_ID_2));

		var userStats = factory.withTransaction(tx -> sut.retrieveUserSuggestionStats(tx, APPLICATION_ID, userId, ids))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertZeroStats(userStats.get(SUGGESTION_ID_1));
		assertZeroStats(userStats.get(SUGGESTION_ID_2));

		Integer value = factory.withTransaction(tx -> sut.increaseTimesSuggested(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(0);
		value = factory.withTransaction(tx -> sut.increaseTimesSuggested(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(1);

		value = factory.withTransaction(tx -> sut.increaseTimesAccepted(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(0);
		value = factory.withTransaction(tx -> sut.decreaseTimesAccepted(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(1);
		value = factory.withTransaction(tx -> sut.decreaseTimesAccepted(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(0);

		value = factory.withTransaction(tx -> sut.increaseTimesRejected(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(0);
		value = factory.withTransaction(tx -> sut.decreaseTimesRejected(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(1);
		value = factory.withTransaction(tx -> sut.decreaseTimesRejected(tx, APPLICATION_ID, userId, SUGGESTION_ID_1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(value).isEqualTo(0);

		userStats = factory.withTransaction(tx -> sut.retrieveUserSuggestionStats(tx, APPLICATION_ID, userId, ids))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userStats.get(SUGGESTION_ID_1)).isEqualTo(new SuggestionStats(2, 0, 0));
		assertZeroStats(userStats.get(SUGGESTION_ID_2));

		totalStats = factory.withTransaction(tx -> sut.retrieveTotalSuggestionStats(tx, APPLICATION_ID, ids))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(totalStats.get(SUGGESTION_ID_1)).isEqualTo(new SuggestionStats(2, 0, 0));
		assertZeroStats(totalStats.get(SUGGESTION_ID_2));
	}

	@Test
	@Order(2)
	void testRejectTooManyIds(Mutiny.SessionFactory sessionFactory) {
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var sut = new UserSuggestionStatsEntityDaoImpl();
		Set<SuggestionTemplateId> ids = new LinkedHashSet<>();
		for (int i = 0; i < 21; i++) {
			ids.add(new GenericSuggestionTemplateId(UUID.randomUUID().toString()));
		}

		assertThatThrownBy(() -> factory.withTransaction(tx -> sut.retrieveTotalSuggestionStats(tx, APPLICATION_ID, ids))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS)))
				.hasMessageContaining("Too many suggestion ids");
	}

	private void assertZeroStats(SuggestionStats stats) {
		assertThat(stats).isEqualTo(new SuggestionStats(0, 0, 0));
	}
}
