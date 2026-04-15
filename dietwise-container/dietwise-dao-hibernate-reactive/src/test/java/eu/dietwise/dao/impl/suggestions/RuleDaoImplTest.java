package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.RuleTranslationEntity;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
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
class RuleDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID BEEF_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	private static final UUID MINCED_IN_SAUCE_RULE_ID = UUID.fromString("6629b06b-2756-4e26-ace8-95c1fda46cfe");

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
	void testFindByTriggerIngredient(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> tx.find(RuleEntity.class, MINCED_IN_SAUCE_RULE_ID)
				.invoke(rule -> rule.setRationale("Use this when the ingredient is minced and cooked into a sauce.")))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var rules = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID), RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).hasSize(3);
		assertThat(rules).allSatisfy(suggestion -> {
			assertThat(suggestion.getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
			assertThat(suggestion.getTriggerIngredient()).isEqualTo(new TriggerIngredientImpl("Beef"));
		});
		assertThat(rules.stream().map(Rule::getRoleOrTechnique).map(RepresentableAsString::asString).collect(Collectors.toSet()))
				.containsExactlyInAnyOrder("minced in sauce", "cubes stew", "steak centerpiece");
		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(MINCED_IN_SAUCE_RULE_ID.toString());
			assertThat(rule.getRationale()).isEqualTo("Use this when the ingredient is minced and cooked into a sauce.");
		});
	}

	@Test
	@Order(2)
	void testFindByTriggerIngredientReturnsLocalizedRationale(Mutiny.SessionFactory sessionFactory) {
		var sut = new RuleDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		UUID ruleId = MINCED_IN_SAUCE_RULE_ID;

		factory.withTransaction(tx -> tx.find(RuleEntity.class, ruleId)
				.flatMap(rule -> {
					rule.setRationale("Use this when the ingredient is minced and cooked into a sauce.");
					var translation = new RuleTranslationEntity();
					translation.setRule(rule);
					translation.setLang(RecipeLanguage.NL);
					translation.setRationale("Gebruik dit wanneer het ingrediënt fijngesneden is en in saus gaart.");
					return tx.persist(translation);
				}))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var rules = factory.withoutTransaction(em ->
				sut.findByTriggerIngredient(em, new UuidTriggerIngredientId(BEEF_ID), RecipeLanguage.NL)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(rules).anySatisfy(rule -> {
			assertThat(rule.getId().asString()).isEqualTo(ruleId.toString());
			assertThat(rule.getRationale()).isEqualTo("Gebruik dit wanneer het ingrediënt fijngesneden is en in saus gaart.");
		});
	}
}
