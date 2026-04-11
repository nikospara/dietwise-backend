package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientTranslationEntity;
import eu.dietwise.v1.types.RecipeLanguage;
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
class TriggerIngredientDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID TRIGGER_INGREDIENT_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");

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
	void testFindAllReturnsBaseTriggerIngredientWhenTranslationIsMissing(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var triggerIngredient = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(triggerIngredient.getName()).isEqualTo("Beef");
		assertThat(triggerIngredient.getExplanationForLlm()).isEmpty();
	}

	@Test
	@Order(2)
	void testFindAllReturnsLocalizedTriggerIngredientWhenTranslationExists(Mutiny.SessionFactory sessionFactory) {
		var sut = new TriggerIngredientDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> tx.find(TriggerIngredientEntity.class, TRIGGER_INGREDIENT_ID)
				.flatMap(triggerIngredient -> {
					var translation = new TriggerIngredientTranslationEntity();
					translation.setTriggerIngredient(triggerIngredient);
					translation.setLang(RecipeLanguage.NL);
					translation.setName("Rundvlees");
					translation.setExplanationForLlm("NL trigger");
					return tx.persist(translation);
				}))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var triggerIngredients = factory.withoutTransaction(em -> sut.findAll(em, RecipeLanguage.NL))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
		var triggerIngredient = triggerIngredients.stream()
				.filter(t -> t.getId().asUuid().equals(TRIGGER_INGREDIENT_ID))
				.findFirst()
				.orElseThrow();

		assertThat(triggerIngredient.getName()).isEqualTo("Rundvlees");
		assertThat(triggerIngredient.getExplanationForLlm()).contains("NL trigger");
	}
}
