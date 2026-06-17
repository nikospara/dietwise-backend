package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.recommendations.RecommendationEntity;
import eu.dietwise.dao.jpa.suggestions.RoleOrTechniqueEntity;
import eu.dietwise.dao.jpa.suggestions.RuleEntity;
import eu.dietwise.dao.jpa.suggestions.TriggerIngredientEntity;
import eu.dietwise.v1.model.ImmutableSuggestionTemplate;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SuggestionTemplateDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID DECREASE_RED_MEAT_RECOMMENDATION_ID = UUID.fromString("9977a713-0608-4f0c-9933-f7788b4f0225");
	private static final UUID BEEF_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	private static final UUID MINCED_IN_SAUCE_RULE_ID = UUID.fromString("6629b06b-2756-4e26-ace8-95c1fda46cfe");
	private static final UUID EMPTY_RULE_ID = UUID.fromString("c1c1c1c1-0000-4000-8000-000000000001");

	private static final UUID FIRST_TEMPLATE_ID = UUID.fromString("90984ed6-5e6b-4381-ac0d-92b6dd3cf503");
	private static final String BROWN_LENTILS = "Brown lentils (cooked)";
	private static final String SOY_MINCE = "Soy mince";
	private static final String MUSHROOM_LENTIL_MIX = "Mushroom–lentil mix";
	private static final String FIRST_RESTRICTION = "Not for burgers without binder";
	private static final String SECOND_RESTRICTION = "Avoid if soy allergy";
	private static final String THIRD_RESTRICTION = "Use only in sauces";
	private static final String SHARED_EQUIVALENCE = "1:1 (200g beef → 200g cooked lentils) or 200g beef → 120g lentils + 150g mushrooms";
	private static final String SHARED_TECHNIQUE_NOTES = "Finely chop mushrooms; dry sauté before adding";

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
	void findByRuleReturnsTemplatesOrderedByAlternativeOrderWithIngredientNamesAndFields(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		var templates = factory.withoutTransaction(em -> sut.findByRule(em, MINCED_IN_SAUCE_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(templates).hasSize(3);
		assertThat(templates).extracting(template -> template.getAlternative().asString())
				.containsExactly(BROWN_LENTILS, SOY_MINCE, MUSHROOM_LENTIL_MIX);
		assertThat(templates).extracting(template -> template.getRestriction().orElse(null))
				.containsExactly(FIRST_RESTRICTION, SECOND_RESTRICTION, THIRD_RESTRICTION);
		assertThat(templates.getFirst()).isEqualTo(ImmutableSuggestionTemplate.builder()
				.id(new GenericSuggestionTemplateId(FIRST_TEMPLATE_ID.toString()))
				.alternative(new AlternativeIngredientImpl(BROWN_LENTILS))
				.restriction(Optional.of(FIRST_RESTRICTION))
				.equivalence(Optional.of(SHARED_EQUIVALENCE))
				.techniqueNotes(Optional.of(SHARED_TECHNIQUE_NOTES))
				.build());
	}

	@Test
	void findByRuleReturnsAnEmptyListForARuleWithNoTemplates(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionTemplateDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);

		factory.withTransaction(tx -> createRuleWithoutTemplates(tx, EMPTY_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var templates = factory.withoutTransaction(em -> sut.findByRule(em, EMPTY_RULE_ID))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(templates).isEmpty();
	}

	private static Uni<Void> createRuleWithoutTemplates(ReactivePersistenceTxContext tx, UUID id) {
		var role = new RoleOrTechniqueEntity();
		role.setId(id);
		role.setName("Role for rule " + id);
		var rule = new RuleEntity();
		rule.setId(id);
		rule.setRecommendation(tx.getReference(RecommendationEntity.class, DECREASE_RED_MEAT_RECOMMENDATION_ID));
		rule.setTriggerIngredient(tx.getReference(TriggerIngredientEntity.class, BEEF_ID));
		rule.setRoleOrTechnique(role);
		rule.setRationale("A rule with no suggestion templates.");
		return tx.persist(role).chain(() -> tx.persist(rule)).replaceWithVoid();
	}
}
