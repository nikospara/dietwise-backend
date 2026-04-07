package eu.dietwise.dao.impl.suggestions;

import static eu.dietwise.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import eu.dietwise.common.dao.reactive.hibernate.ReactivePersistenceContextFactoryImpl;
import eu.dietwise.common.test.jpa.HibernateReactiveExtension;
import eu.dietwise.common.test.liquibase.LiquibaseExtension;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientEntity;
import eu.dietwise.dao.jpa.suggestions.AlternativeIngredientSeasonalityEntity;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.types.ImmutableSeasonality;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
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
class SuggestionDaoImplTest {
	private static final long ASYNC_WAIT_SECONDS = 300;

	private static final UUID CURRY_CUBES_ID = UUID.fromString("17f6b8f6-bf3b-47f9-ab6c-cf1b6b1bd7f3");
	private static final UUID LAMB_ID = UUID.fromString("36786d90-0f4d-40d5-98ca-6561514f0f43");
	private static final UUID RULE_ID = UUID.fromString("efd2ae9e-73af-494a-bced-5f276a8d3e6e");
	private static final UUID INGREDIENT_ID = UUID.fromString("67c852ee-f6d9-459e-94ff-93df60449da8");
	private static final UUID CHICKPEA_AUBERGINE_MIX_ID = UUID.fromString("70000000-0000-0000-0000-000000000019");
	private static final UUID PANEER_ID = UUID.fromString("70000000-0000-0000-0000-000000000036");

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

											return tx.persistAll(greeceSeasonality, belgiumSeasonality);
										})
						)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		var suggestionsWithoutCountry = factory.withoutTransaction(em ->
				sut.retrieveByRule(em, new GenericRuleId(RULE_ID.toString()), null, ingredient)
		).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(suggestionsWithoutCountry).hasSize(3);
		assertThat(suggestionsWithoutCountry).allSatisfy(suggestion -> {
			assertThat(suggestion.getTarget()).isEqualTo(new AppliesTo.AppliesToIngredient(ingredient.getId()));
			assertThat(suggestion.getRuleId()).isEqualTo(new GenericRuleId(RULE_ID.toString()));
			assertThat(suggestion.getRecommendation()).isEqualTo(new RecommendationImpl("Decrease red meat"));
			assertThat(suggestion.getSeasonality()).isEmpty();
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
				sut.retrieveByRule(em, new GenericRuleId(RULE_ID.toString()), GREECE, ingredient)
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
			assertThat(suggestion.getAlternativeComponentNames()).containsExactlyInAnyOrder(
					new RecommendationComponentNameImpl("legumes"),
					new RecommendationComponentNameImpl("fiber"),
					new RecommendationComponentNameImpl("vegetables")
			);
		});
		assertThat(suggestionsForGreece).filteredOn(suggestion -> !suggestion.getId().asString().equals("b4cba823-e8aa-4e4f-a81a-0e3c3dd6816c"))
				.allSatisfy(suggestion -> assertThat(suggestion.getSeasonality()).isEmpty());
	}
}
