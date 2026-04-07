package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.v1.types.Seasonality;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class AlternativeIngredientDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("70000000-0000-0000-0000-00000000001f");

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
	void testFindAllReturnsSeasonalityByCountry(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx ->
				tx.find(AlternativeIngredientEntity.class, ALTERNATIVE_INGREDIENT_ID)
						.flatMap(alternativeIngredient -> {
							var greece = new AlternativeIngredientSeasonalityEntity();
							greece.setAlternativeIngredient(alternativeIngredient);
							greece.setCountry(GREECE);
							greece.setMonthFrom(8);
							greece.setMonthTo(10);

							var belgium = new AlternativeIngredientSeasonalityEntity();
							belgium.setAlternativeIngredient(alternativeIngredient);
							belgium.setCountry(BELGIUM);
							belgium.setMonthFrom(7);
							belgium.setMonthTo(9);

							alternativeIngredient.setSeasonalityByCountry(Set.of(greece, belgium));
							return tx.persistAll(greece, belgium).replaceWith(alternativeIngredient);
						})
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var alternatives = factory.withoutTransaction(sut::findAll).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var alternative = alternatives.stream()
				.filter(a -> a.getId().getId().asUuid().equals(ALTERNATIVE_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(alternative.getSeasonalityByCountry()).isPresent();
		assertThat(alternative.getSeasonalityByCountry().orElseThrow())
				.containsEntry(GREECE, seasonality(8, 10))
				.containsEntry(BELGIUM, seasonality(7, 9));
	}

	private static Seasonality seasonality(int monthFrom, int monthTo) {
		return eu.dietwise.v1.types.ImmutableSeasonality.builder().monthFrom(monthFrom).monthTo(monthTo).build();
	}
}
