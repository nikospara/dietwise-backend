package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.test.model.HasRuleIdArgumentMatcher.hasRuleId;
import static eu.dietwise.v1.types.Country.BELGIUM;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.v1.types.UserId;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.ImmutableTriggerIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.types.suggestions.TriggerIngredientId;
import eu.dietwise.services.v1.i18n.I18nMessages;
import eu.dietwise.services.v1.suggestions.MakeSuggestionsResult;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RecommendationWeight;
import eu.dietwise.v1.types.RuleId;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.RoleOrTechniqueImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeSuggestionsServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final String APPLICATION_ID = "dietwise-web";
	private static final UUID CORRELATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UserId USER_ID = new UserIdImpl("user-1");
	private static final String INGREDIENT_NAME = "flour";
	private static final String ROLE_MARKDOWN = "- binder";
	private static final String INSTRUCTIONS_MARKDOWN = "- mix";
	private static final SuggestionTemplateId FIRST_SUGGESTION_ID =
			new GenericSuggestionTemplateId("00000000-0000-0000-0000-000000000001");
	private static final SuggestionTemplateId SECOND_SUGGESTION_ID =
			new GenericSuggestionTemplateId("00000000-0000-0000-0000-000000000002");
	private static final Suggestion FIRST_SUGGESTION = suggestion(FIRST_SUGGESTION_ID, "first");
	private static final Suggestion SECOND_SUGGESTION = suggestion(SECOND_SUGGESTION_ID, "second");
	private static final Recipe RECIPE = ImmutableRecipe.builder()
			.addRecipeIngredients(ImmutableIngredient.builder().id(new GenericIngredientId("ingredient-1")).nameInRecipe(INGREDIENT_NAME).build())
			.addRecipeInstructions("Mix ingredients")
			.build();
	private static final Map<String, RecommendationComponent> RECOMMENDATIONS = Map.of(
			"fiber", recommendationComponent("Fiber", RecommendationWeight.ENCOURAGED, "explanation of Fiber"),
			"sodium", recommendationComponent("Sodium", RecommendationWeight.LIMITED, null)
	);
	private static final RuleId RULE1_ID = new GenericRuleId("rule-1");
	private static final Rule RULE1 = ImmutableRule.builder()
			.id(RULE1_ID)
			.recommendation(new RecommendationImpl("fiber"))
			.triggerIngredient(new TriggerIngredientImpl("trigger ingredient 1"))
			.roleOrTechnique(new RoleOrTechniqueImpl("role 1"))
			.build();
	private static final String TRIGGER_INGREDIENT_NAME_FIBER = "fiber trigger ingredient";
	private static final String TRIGGER_INGREDIENT_NAME_FLOUR = "flour trigger ingredient";
	private static final TriggerIngredient TRIGGER_INGREDIENT_FIBER = ImmutableTriggerIngredient.builder()
			.id(mock(TriggerIngredientId.class))
			.name(TRIGGER_INGREDIENT_NAME_FIBER)
			.build();

	@Mock
	private PersonalInfoDao personalInfoDao;
	@Mock
	private SuggestionsAiFacade suggestionsAiFacade;
	@Mock
	private SuggestionDao suggestionDao;
	@Mock
	private RuleDao ruleDao;
	@Mock
	private SuggestionPrioritizer suggestionPrioritizer;
	@Mock
	private UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory =
			new MockReactivePersistenceContextFactory();

	private RecipeSuggestionsServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecipeSuggestionsServiceImpl(
				persistenceContextFactory, personalInfoDao, suggestionsAiFacade, suggestionDao, ruleDao, suggestionPrioritizer, userSuggestionStatsEntityDao, new I18nMessages());
	}

	@Test
	void makeSuggestionsReturnsEmptyMessageWhenNoRuleMatches() {
		var hasUserId = hasUserId();
		var personalInfo = ImmutablePersonalInfo.builder().build();
		RoleOrTechnique role = mock(RoleOrTechnique.class);
		when(suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(Map.of("binder", role)));
		when(suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(Map.of(TRIGGER_INGREDIENT_NAME_FIBER, TRIGGER_INGREDIENT_FIBER)));
		when(suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(RECOMMENDATIONS));
		when(suggestionsAiFacade.convertRolesToMarkdownList(any())).thenReturn(ROLE_MARKDOWN);
		when(suggestionsAiFacade.convertInstructionsToMarkdownList(eq(RECIPE.getRecipeInstructions()))).thenReturn(INSTRUCTIONS_MARKDOWN);
		when(suggestionsAiFacade.assessIngredientRole(RecipeLanguage.EN, ROLE_MARKDOWN, INGREDIENT_NAME, INSTRUCTIONS_MARKDOWN))
				.thenReturn(Uni.createFrom().item("missing-role"));
		when(suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(any())).thenReturn("");
		when(suggestionsAiFacade.matchIngredientToTrigger(RecipeLanguage.EN, "", INGREDIENT_NAME, null)).thenReturn(Uni.createFrom().item(TRIGGER_INGREDIENT_NAME_FIBER));
		when(ruleDao.findByTriggerIngredient(any(), any(), eq(RecipeLanguage.EN))).thenAnswer(_ -> Uni.createFrom().item(Collections.emptyList()));
		when(suggestionPrioritizer.prioritizeSuggestions(any(), any(), eq(List.of()))).thenReturn(Uni.createFrom().item(List.of()));
		when(personalInfoDao.findByUser(any(), eq(hasUserId))).thenReturn(Uni.createFrom().item(personalInfo));

		MakeSuggestionsResult result = sut.makeSuggestions(CORRELATION_ID, hasUserId, RecipeLanguage.EN, RECIPE, null)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result.message().suggestions()).isEmpty();
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(suggestionPrioritizer).prioritizeSuggestions(any(), any(), eq(List.of()));
	}

	@Test
	void makeSuggestionsReturnsPrioritizedSuggestionsWithGeneratedTextWhenRuleMatches() {
		var hasUserId = hasUserId();
		var personalInfo = ImmutablePersonalInfo.builder().country(BELGIUM).build();
		RoleOrTechnique role = mock(RoleOrTechnique.class);
		var triggerIngredient = mock(eu.dietwise.services.model.suggestions.TriggerIngredient.class);

		when(suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(Map.of("binder", role)));
		when(suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(Map.of(TRIGGER_INGREDIENT_NAME_FLOUR, triggerIngredient)));
		when(suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(any(), eq(RecipeLanguage.EN))).thenReturn(Uni.createFrom().item(RECOMMENDATIONS));
		when(suggestionsAiFacade.convertRolesToMarkdownList(any())).thenReturn(ROLE_MARKDOWN);
		when(suggestionsAiFacade.convertInstructionsToMarkdownList(eq(RECIPE.getRecipeInstructions()))).thenReturn(INSTRUCTIONS_MARKDOWN);
		when(suggestionsAiFacade.assessIngredientRole(RecipeLanguage.EN, ROLE_MARKDOWN, INGREDIENT_NAME, INSTRUCTIONS_MARKDOWN))
				.thenReturn(Uni.createFrom().item("binder"));
		when(suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(any())).thenReturn("- flour");
		when(suggestionsAiFacade.matchIngredientToTrigger(RecipeLanguage.EN, "- flour", INGREDIENT_NAME, role)).thenReturn(Uni.createFrom().item(TRIGGER_INGREDIENT_NAME_FLOUR));
		when(ruleDao.findByTriggerIngredient(any(), any(), eq(RecipeLanguage.EN))).thenAnswer(_ -> Uni.createFrom().item(List.of(RULE1)));
		when(suggestionsAiFacade.matchIngredientsWithRecommendations(eq(RecipeLanguage.EN), any(), any())).thenAnswer(_ -> Uni.createFrom().item(Set.of("fiber")));
		when(suggestionsAiFacade.findBestRule(eq(RecipeLanguage.EN), any(), any(), any(), any(), any())).thenAnswer(_ -> Uni.createFrom().item(RULE1.getId().asString()));
		when(suggestionsAiFacade.suggestAlternatives(eq(RecipeLanguage.EN), any(), any(), any())).thenAnswer(_ -> Uni.createFrom().item("DUMMY STRING, REPLACE WHEN SUGGEST ALTERNATIVES IS COMPLETE"));
		when(suggestionDao.retrieveByRule(any(), argThat(hasRuleId(RULE1_ID)), eq(GREECE), eq(RECIPE.getRecipeIngredients().getFirst()), eq(RecipeLanguage.EN)))
				.thenReturn(Uni.createFrom().item(List.of(FIRST_SUGGESTION)));

		Suggestion prioritizedSuggestion = ImmutableSuggestion.copyOf(FIRST_SUGGESTION)
				.withText("We suggest: alternative-first instead of: " + INGREDIENT_NAME + " [prioritized]");
		when(suggestionPrioritizer.prioritizeSuggestions(any(), any(), any()))
				.thenReturn(Uni.createFrom().item(List.of(prioritizedSuggestion)));
		when(personalInfoDao.findByUser(any(), eq(hasUserId))).thenReturn(Uni.createFrom().item(personalInfo));

		MakeSuggestionsResult result = sut.makeSuggestions(CORRELATION_ID, hasUserId, RecipeLanguage.EN, RECIPE, GREECE)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result.message().suggestions()).containsExactly(prioritizedSuggestion);
		assertThat(result.message().suggestions().getFirst().getText())
				.isEqualTo("We suggest: alternative-first instead of: " + INGREDIENT_NAME + " [prioritized]");
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(suggestionPrioritizer).prioritizeSuggestions(
				any(),
				any(),
				eq(List.of(ImmutableSuggestion.copyOf(FIRST_SUGGESTION)
						.withText("We suggest: alternative-first instead of: " + INGREDIENT_NAME)))
		);
	}

	@Test
	void increaseTimesSuggestedIncreasesEverySuggestionIdInSingleTransaction() {
		var hasUserId = hasUserId();
		HasSuggestionTemplateIds suggestions = () -> Set.of(FIRST_SUGGESTION_ID, SECOND_SUGGESTION_ID);

		when(userSuggestionStatsEntityDao.increaseTimesSuggested(any(), eq(APPLICATION_ID), eq(hasUserId), any()))
				.thenReturn(Uni.createFrom().item(1));

		Void result = sut.increaseTimesSuggested(CORRELATION_ID, APPLICATION_ID, hasUserId, suggestions)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result).isNull();
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(userSuggestionStatsEntityDao).increaseTimesSuggested(any(), eq(APPLICATION_ID), eq(hasUserId), eq(FIRST_SUGGESTION_ID));
		verify(userSuggestionStatsEntityDao).increaseTimesSuggested(any(), eq(APPLICATION_ID), eq(hasUserId), eq(SECOND_SUGGESTION_ID));
	}

	@Test
	void enrichWithStatisticsAppliesRetrievedStatsAndDefaultsMissingOnesToZero() {
		var hasUserId = hasUserId();
		var message = new SuggestionsRecipeAssessmentMessage(List.of(FIRST_SUGGESTION, SECOND_SUGGESTION));
		SuggestionStats userStats = new SuggestionStats(3, 2, 1);
		SuggestionStats totalStats = new SuggestionStats(8, 5, 2);

		when(userSuggestionStatsEntityDao.retrieveUserSuggestionStats(any(), eq(APPLICATION_ID), eq(hasUserId), eq(message.getSuggestionTemplateIds())))
				.thenReturn(Uni.createFrom().item(Map.of(FIRST_SUGGESTION_ID, userStats)));
		when(userSuggestionStatsEntityDao.retrieveTotalSuggestionStats(any(), eq(APPLICATION_ID), eq(message.getSuggestionTemplateIds())))
				.thenReturn(Uni.createFrom().item(Map.of(SECOND_SUGGESTION_ID, totalStats)));

		SuggestionsRecipeAssessmentMessage result = sut.enrichWithStatistics(CORRELATION_ID, APPLICATION_ID, hasUserId, message)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(result.suggestions()).hasSize(2);
		assertThat(result.suggestions().getFirst().getId()).isEqualTo(FIRST_SUGGESTION_ID);
		assertThat(result.suggestions().get(0).getUserSuggestionStats()).isEqualTo(userStats);
		assertThat(result.suggestions().get(0).getTotalSuggestionStats()).isEqualTo(SuggestionStats.ALL_ZEROES);
		assertThat(result.suggestions().get(1).getId()).isEqualTo(SECOND_SUGGESTION_ID);
		assertThat(result.suggestions().get(1).getUserSuggestionStats()).isEqualTo(SuggestionStats.ALL_ZEROES);
		assertThat(result.suggestions().get(1).getTotalSuggestionStats()).isEqualTo(totalStats);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
	}

	private static eu.dietwise.common.v1.types.HasUserId hasUserId() {
		return () -> USER_ID;
	}

	private static Suggestion suggestion(SuggestionTemplateId suggestionId, String suffix) {
		return ImmutableSuggestion.builder()
				.id(suggestionId)
				.alternative(new AlternativeIngredientImpl("alternative-" + suffix))
				.target(new AppliesTo.AppliesToRecipe("recipe-" + suffix))
				.ruleId(new GenericRuleId("rule-" + suffix))
				.recommendation(new RecommendationImpl("recommendation-" + suffix))
				.text("text-" + suffix)
				.build();
	}

	private static RecommendationComponent recommendationComponent(
			String componentName, RecommendationWeight weight, String explanationForLlm) {
		return ImmutableRecommendationComponent.builder()
				.recommendation(new RecommendationImpl(componentName + "-recommendation"))
				.componentForScoring(new RecommendationComponentNameImpl(componentName))
				.weight(weight)
				.explanationForLlm(Optional.ofNullable(explanationForLlm))
				.build();
	}
}
