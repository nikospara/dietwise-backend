package eu.dietwise.dao.impl;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.v1.model.UserData;
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
public class UserDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_IDM_ID = UUID.fromString("716e038e-9f28-4f67-a797-2cb297818546");
	private static final UUID DELETED_USER_IDM_ID = UUID.fromString("15b68af2-af20-4bba-a224-8b9a42285d69");
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 5, 13, 0, 0);

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
	void testStartingEmpty(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		UserData userData1 = factory.withTransaction(tx -> sut.findOrCreateByIdmId(tx, USER_IDM_ID.toString()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userData1).isNotNull();
		UserData userData2 = factory.withTransaction(tx -> sut.findOrCreateByIdmId(tx, USER_IDM_ID.toString()))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(userData2.getId().asString()).isEqualTo(userData1.getId().asString());
	}

	@Test
	@Order(2)
	void testFindOrCreateByIdmIdRejectsTombstonedUser(Mutiny.SessionFactory sessionFactory) {
		var sut = new UserDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		factory.withTransaction(tx -> sut.tombstoneByIdmId(tx, DELETED_USER_IDM_ID.toString(), DELETED_AT))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThatThrownBy(() ->
				factory.withTransaction(tx -> sut.findOrCreateByIdmId(tx, DELETED_USER_IDM_ID.toString()))
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).isInstanceOf(NotAuthenticatedException.class);
	}
}
