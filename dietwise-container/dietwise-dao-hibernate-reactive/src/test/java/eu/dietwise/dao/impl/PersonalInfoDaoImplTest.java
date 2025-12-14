package eu.dietwise.dao.impl;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.BiologicalGender.FEMALE;
import static eu.dietwise.v1.types.BiologicalGender.MALE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.jpa.UserEntity;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.PersonalInfo;
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
public class PersonalInfoDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID USER_ID = UUID.fromString("42e9efa3-5682-403c-ab60-c96395e5c669");
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
		var sut = new PersonalInfoDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		// setup: create the user
		factory.withTransaction(tx -> {
			var user = new UserEntity();
			user.setId(USER_ID);
			user.setIdmId(USER_IDM_ID.toString());
			return tx.persist(user);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		// read with no data
		PersonalInfo pi = factory.withTransaction(tx -> sut.findByUser(tx, new UserIdImpl(USER_ID.toString())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(pi).isNull();
		// store
		var piToStore1 = ImmutablePersonalInfo.builder().gender(MALE).yearOfBirth(1977).build();
		pi = factory.withTransaction(tx -> sut.storeForUser(tx, new UserIdImpl(USER_ID.toString()), piToStore1))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(pi.getGender()).isEqualTo(MALE);
		assertThat(pi.getYearOfBirth()).isEqualTo(1977);
		// read again, expect the one just stored
		pi = factory.withTransaction(tx -> sut.findByUser(tx, new UserIdImpl(USER_ID.toString())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(pi.getGender()).isEqualTo(MALE);
		assertThat(pi.getYearOfBirth()).isEqualTo(1977);
		// update
		var piToStore2 = ImmutablePersonalInfo.builder().gender(FEMALE).yearOfBirth(1987).build();
		pi = factory.withTransaction(tx -> sut.storeForUser(tx, new UserIdImpl(USER_ID.toString()), piToStore2))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(pi.getGender()).isEqualTo(FEMALE);
		assertThat(pi.getYearOfBirth()).isEqualTo(1987);
		pi = factory.withTransaction(tx -> sut.findByUser(tx, new UserIdImpl(USER_ID.toString())))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(pi.getGender()).isEqualTo(FEMALE);
		assertThat(pi.getYearOfBirth()).isEqualTo(1987);
	}
}
