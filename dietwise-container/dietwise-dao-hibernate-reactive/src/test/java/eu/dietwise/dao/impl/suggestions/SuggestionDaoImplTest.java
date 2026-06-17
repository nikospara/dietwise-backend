package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.Cost.HI;
import static eu.dietwise.v1.types.Cost.LO;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientCostEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeAll;
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
class SuggestionDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID RULE_ID = UUID.fromString("efd2ae9e-73af-494a-bced-5f276a8d3e6e");
	private static final UUID INGREDIENT_ID = UUID.fromString("67c852ee-f6d9-459e-94ff-93df60449da8");
	private static final UUID CHICKPEA_AUBERGINE_MIX_ID = UUID.fromString("70000000-0000-0000-0000-000000000019");
	private static final UUID PANEER_ID = UUID.fromString("70000000-0000-0000-0000-000000000036");

	// A rule with one active and one deactivated master template; assessment must skip the deactivated one.
	private static final UUID PARTIAL_ACTIVE_RULE_ID = UUID.fromString("14000000-0000-4000-8000-000000000002");
	private static final UUID PARTIAL_ACTIVE_TEMPLATE_ID = UUID.fromString("14000000-0000-4000-8000-0000000000a1");
	private static final UUID PARTIAL_DEACTIVATED_TEMPLATE_ID = UUID.fromString("14000000-0000-4000-8000-0000000000a2");
	private static final String ACTIVE_ALTERNATIVE_RESTRICTION = "Issue 14 active alternative";
	private static final String DEACTIVATED_ALTERNATIVE_RESTRICTION = "Issue 14 deactivated alternative";
	// A rule whose only template is deactivated; assessment must yield no suggestions and no error.
	private static final UUID ALL_DEACTIVATED_RULE_ID = UUID.fromString("14000000-0000-4000-8000-000000000003");
	private static final UUID ALL_DEACTIVATED_TEMPLATE_ID = UUID.fromString("14000000-0000-4000-8000-0000000000b1");

	private static final UUID DECREASE_RED_MEAT_RECOMMENDATION_ID = UUID.fromString("9977a713-0608-4f0c-9933-f7788b4f0225");
	private static final UUID BEEF_ID = UUID.fromString("f8e6df4f-72f5-4f92-b3ca-05328707fd5e");
	// Seeded AlternativeIngredients reused as the alternatives of the active-filter fixtures' templates.
	private static final UUID FIRST_ALTERNATIVE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
	private static final UUID SECOND_ALTERNATIVE_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
	private static final UUID THIRD_ALTERNATIVE_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");

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

	@BeforeAll
	static void seedActiveFilterFixtures(Mutiny.SessionFactory sessionFactory) {
		sessionFactory.withTransaction(session -> SuggestionTemplateFixtures.insertRuleWithTemplates(
						session, PARTIAL_ACTIVE_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 14 partial-filter role",
						List.of(
								new SuggestionTemplateFixtures.Template(PARTIAL_ACTIVE_TEMPLATE_ID, FIRST_ALTERNATIVE_ID, 0, ACTIVE_ALTERNATIVE_RESTRICTION, true),
								new SuggestionTemplateFixtures.Template(PARTIAL_DEACTIVATED_TEMPLATE_ID, SECOND_ALTERNATIVE_ID, 1, DEACTIVATED_ALTERNATIVE_RESTRICTION, false)
						))
						.chain(() -> SuggestionTemplateFixtures.insertRuleWithTemplates(
								session, ALL_DEACTIVATED_RULE_ID, DECREASE_RED_MEAT_RECOMMENDATION_ID, BEEF_ID, "Issue 14 all-off role",
								List.of(
										new SuggestionTemplateFixtures.Template(ALL_DEACTIVATED_TEMPLATE_ID, THIRD_ALTERNATIVE_ID, 0, "Issue 14 sole deactivated alternative", false)
								))))
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	@Test
	@Order(1)
	void testRetrieveByRule(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var ingredient = ImmutableIngredient.builder()
				.id(new GenericIngredientId(INGREDIENT_ID.toString()))
				.nameInRecipe("beef mince")
				.build();

		factory.withTransaction(tx ->
				tx.find(AlternativeIngredientEntity.class, CHICKPEA_AUBERGINE_MIX_ID)
						.flatMap(chickpeaAubergineMix ->
								tx.find(AlternativeIngredientEntity.class, PANEER_ID)
										.flatMap(paneer -> {
											var greeceSeasonality = new AlternativeIngredientSeasonalityEntity();
											greeceSeasonality.setAlternativeIngredient(chickpeaAubergineMix);
											greeceSeasonality.setCountry(GREECE);
											greeceSeasonality.setMonthFrom(8);
											greeceSeasonality.setMonthTo(10);

											var belgiumSeasonality = new AlternativeIngredientSeasonalityEntity();
											belgiumSeasonality.setAlternativeIngredient(paneer);
											belgiumSeasonality.setCountry(BELGIUM);
											belgiumSeasonality.setMonthFrom(8);
											belgiumSeasonality.setMonthTo(9);

											var greeceCost = new AlternativeIngredientCostEntity();
											greeceCost.setAlternativeIngredient(chickpeaAubergineMix);
											greeceCost.setCountry(GREECE);
											greeceCost.setCost(LO);

											var belgiumCost = new AlternativeIngredientCostEntity();
											belgiumCost.setAlternativeIngredient(paneer);
											belgiumCost.setCountry(BELGIUM);
											belgiumCost.setCost(HI);

											return tx.persistAll(greeceSeasonality, belgiumSeasonality, greeceCost, belgiumCost);
										})
						)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var suggestionsWithoutCountry = factory.withoutTransaction(em ->
				sut.retrieveByRule(em, new GenericRuleId(RULE_ID.toString()), null, ingredient, RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(suggestionsWithoutCountry).hasSize(3);
		assertThat(suggestionsWithoutCountry).allSatisfy(suggestion -> {
			assertThat(suggestion.getTarget()).isEqualTo(new AppliesTo.AppliesToIngredient(ingredient.getId()));
			assertThat(suggestion.getRuleId()).isEqualTo(new GenericRuleId(RULE_ID.toString()));
			assertThat(suggestion.getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
			assertThat(suggestion.getSeasonality()).isEmpty();
			assertThat(suggestion.getCost()).isEmpty();
		});
		assertThat(suggestionsWithoutCountry).anySatisfy(suggestion -> {
			assertThat(suggestion.getId().asString()).isEqualTo("b4cba823-e8aa-4e4f-a81a-0e3c3dd6816c");
			assertThat(suggestion.getAlternative().asString()).isEqualTo("Chickpea + aubergine mix");
			assertThat(suggestion.getRestriction()).contains("Lower protein");
			assertThat(suggestion.getEquivalence()).contains("1:1 by volume");
			assertThat(suggestion.getTechniqueNotes()).contains("Roast aubergine first");
			assertThat(suggestion.getAlternativeComponentNames()).containsExactlyInAnyOrder(
					new RecommendationComponentNameImpl("legumes"),
					new RecommendationComponentNameImpl("fiber"),
					new RecommendationComponentNameImpl("vegetables")
			);
		});
		assertThat(suggestionsWithoutCountry).anySatisfy(suggestion -> {
			assertThat(suggestion.getId().asString()).isEqualTo("89ae6ce0-bf2f-4b17-b382-2bc78c8163b7");
			assertThat(suggestion.getAlternative().asString()).isEqualTo("Paneer (if dairy ok)");
			assertThat(suggestion.getRestriction()).contains("Not vegan");
			assertThat(suggestion.getEquivalence()).contains("1:1 by volume");
			assertThat(suggestion.getTechniqueNotes()).contains("Roast aubergine first");
		});
		assertThat(suggestionsWithoutCountry).anySatisfy(suggestion -> {
			assertThat(suggestion.getId().asString()).isEqualTo("0e489ba0-3f89-4753-8f13-bc162fa0bbf1");
			assertThat(suggestion.getAlternative().asString()).isEqualTo("Tofu cubes");
			assertThat(suggestion.getRestriction()).isEmpty();
			assertThat(suggestion.getEquivalence()).contains("1:1 by volume");
			assertThat(suggestion.getTechniqueNotes()).contains("Roast aubergine first");
		});

		var suggestionsForGreece = factory.withoutTransaction(em ->
				sut.retrieveByRule(em, new GenericRuleId(RULE_ID.toString()), GREECE, ingredient, RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(suggestionsForGreece).hasSize(3);
		assertThat(suggestionsForGreece).allSatisfy(suggestion -> {
			assertThat(suggestion.getTarget()).isEqualTo(new AppliesTo.AppliesToIngredient(ingredient.getId()));
			assertThat(suggestion.getRuleId()).isEqualTo(new GenericRuleId(RULE_ID.toString()));
			assertThat(suggestion.getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
		});
		assertThat(suggestionsForGreece).anySatisfy(suggestion -> {
			assertThat(suggestion.getId().asString()).isEqualTo("b4cba823-e8aa-4e4f-a81a-0e3c3dd6816c");
			assertThat(suggestion.getAlternative().asString()).isEqualTo("Chickpea + aubergine mix");
			assertThat(suggestion.getSeasonality()).contains(ImmutableSeasonality.builder().monthFrom(8).monthTo(10).build());
			assertThat(suggestion.getCost()).contains(LO);
			assertThat(suggestion.getAlternativeComponentNames()).containsExactlyInAnyOrder(
					new RecommendationComponentNameImpl("legumes"),
					new RecommendationComponentNameImpl("fiber"),
					new RecommendationComponentNameImpl("vegetables")
			);
		});
		assertThat(suggestionsForGreece).filteredOn(suggestion -> !suggestion.getId().asString().equals("b4cba823-e8aa-4e4f-a81a-0e3c3dd6816c"))
				.allSatisfy(suggestion -> {
					assertThat(suggestion.getSeasonality()).isEmpty();
					assertThat(suggestion.getCost()).isEmpty();
				});
	}

	@Test
	@Order(2)
	void testRetrieveByRuleReturnsLocalizedSuggestionTemplateStrings(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var ingredient = ImmutableIngredient.builder()
				.id(new GenericIngredientId(INGREDIENT_ID.toString()))
				.nameInRecipe("beef mince")
				.build();

		var suggestions = factory.withoutTransaction(em ->
				sut.retrieveByRule(em, new GenericRuleId(RULE_ID.toString()), null, ingredient, RecipeLanguage.NL)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(suggestions).anySatisfy(suggestion -> {
			assertThat(suggestion.getId().asString()).isEqualTo("b4cba823-e8aa-4e4f-a81a-0e3c3dd6816c");
			assertThat(suggestion.getRestriction()).contains("Lager eiwitgehalte");
			assertThat(suggestion.getEquivalence()).contains("1:1 op volume");
		});
	}

	@Test
	@Order(3)
	void retrieveByRuleSkipsDeactivatedTemplates(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var ingredient = ImmutableIngredient.builder()
				.id(new GenericIngredientId(INGREDIENT_ID.toString()))
				.nameInRecipe("beef mince")
				.build();

		var suggestions = factory.withoutTransaction(em ->
				sut.retrieveByRule(em, new GenericRuleId(PARTIAL_ACTIVE_RULE_ID.toString()), null, ingredient, RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(suggestions).hasSize(1);
		assertThat(suggestions).extracting(suggestion -> suggestion.getRestriction().orElse(null))
				.containsExactly(ACTIVE_ALTERNATIVE_RESTRICTION);
		assertThat(suggestions).noneSatisfy(suggestion ->
				assertThat(suggestion.getRestriction()).contains(DEACTIVATED_ALTERNATIVE_RESTRICTION));
	}

	@Test
	@Order(4)
	void retrieveByRuleReturnsNoSuggestionsWhenAllTemplatesDeactivated(Mutiny.SessionFactory sessionFactory) {
		var sut = new SuggestionDaoImpl();
		var factory = new ReactivePersistenceContextFactoryImpl(sessionFactory);
		var ingredient = ImmutableIngredient.builder()
				.id(new GenericIngredientId(INGREDIENT_ID.toString()))
				.nameInRecipe("beef mince")
				.build();

		var suggestions = factory.withoutTransaction(em ->
				sut.retrieveByRule(em, new GenericRuleId(ALL_DEACTIVATED_RULE_ID.toString()), null, ingredient, RecipeLanguage.EN)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(suggestions).isEmpty();
	}
}
