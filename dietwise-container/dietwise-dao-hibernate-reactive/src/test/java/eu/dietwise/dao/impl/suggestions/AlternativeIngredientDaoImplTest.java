package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientWcEntity;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Seasonality;
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
class AlternativeIngredientDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID ALTERNATIVE_INGREDIENT_ID = UUID.fromString("70000000-0000-0000-0000-00000000001f");
	private static final String NEW_ALTERNATIVE_INGREDIENT_NAME = "Aquafaba";
	private static final String OVERLAID_NAME = "Brown lentils (revised)";

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

		var alternatives = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.EN)).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var alternative = alternatives.stream()
				.filter(a -> a.getId().getId().asUuid().equals(ALTERNATIVE_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(alternative.getSeasonalityByCountry()).isPresent();
		assertThat(alternative.getSeasonalityByCountry().orElseThrow())
				.containsEntry(GREECE, seasonality(8, 10))
				.containsEntry(BELGIUM, seasonality(7, 9));
	}

	@Test
	@Order(1)
	void testFindAllReturnsLocalizedAlternativeIngredientWithFallback(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var alternatives = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var alternative = alternatives.stream()
				.filter(a -> a.getId().getId().asUuid().equals(ALTERNATIVE_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(alternative.getName()).isEqualTo("Bruine linzen (gekookt)");
		assertThat(alternative.getExplanationForLlm()).isEmpty();
	}

	@Test
	@Order(1)
	void listOptionsReturnsPublishedAlternativeIngredientsSortedByName(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var options = factory.withoutTransaction(sut::listOptions).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).contains(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Brown lentils (cooked)"));
		assertThat(options).isSortedAccordingTo(Comparator.comparing(ReferenceOption::name));
	}

	@Test
	@Order(2)
	void createAlternativeIngredientStagesAMirrorRowVisibleInListOptions(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var newId = factory.withTransaction(tx -> sut.createAlternativeIngredient(tx, NEW_ALTERNATIVE_INGREDIENT_NAME))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(options).contains(new ReferenceOption(newId, NEW_ALTERNATIVE_INGREDIENT_NAME));

		var stored = factory.withoutTransaction(em -> em.find(AlternativeIngredientWcEntity.class, newId))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		assertThat(stored.getVersion()).isEqualTo(1L);
		assertThat(stored.getExplanationForLlm()).isNull();
	}

	@Test
	@Order(3)
	void listOptionsOverlaysWorkingCopyOnMasterMirrorWinningById(Mutiny.SessionFactory sessionFactory) {
		var sut = new AlternativeIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> {
			var mirror = new AlternativeIngredientWcEntity();
			mirror.setId(ALTERNATIVE_INGREDIENT_ID);
			mirror.setName(OVERLAID_NAME);
			mirror.setVersion(1L);
			return tx.persist(mirror);
		}).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var options = factory.withoutTransaction(sut::listOptions).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(options).contains(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, OVERLAID_NAME));
		assertThat(options).doesNotContain(new ReferenceOption(ALTERNATIVE_INGREDIENT_ID, "Brown lentils (cooked)"));
		assertThat(options.stream().filter(option -> option.id().equals(ALTERNATIVE_INGREDIENT_ID))).hasSize(1);
		assertThat(options).isSortedAccordingTo(Comparator.comparing(ReferenceOption::name));
	}

	private static Seasonality seasonality(int monthFrom, int monthTo) {
		return eu.dietwise.v1.types.ImmutableSeasonality.builder().monthFrom(monthFrom).monthTo(monthTo).build();
	}
}
