package eu.dietwise.dao.impl;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.v1.model.UserData;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class UserDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_IDM_ID = UUID.fromString("716e038e-9f28-4f67-a797-2cb297818546");

	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>(POSTGRES_IMAGE);

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
}
