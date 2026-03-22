package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.v1.types.UserId;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.RecommendationWeight;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeSuggestionsServiceImplTest {
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
			"fiber", recommendationComponent("Fiber", RecommendationWeight.ENCOURAGED, "High-fiber foods"),
			"sodium", recommendationComponent("Sodium", RecommendationWeight.LIMITED, null)
	);

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
				persistenceContextFactory, suggestionsAiFacade, suggestionDao, ruleDao, suggestionPrioritizer, userSuggestionStatsEntityDao);
	}

	@Test
	void makeSuggestionsReturnsEmptyMessageWhenNoRuleMatches() {
		var hasUserId = hasUserId();
		RoleOrTechnique role = org.mockito.Mockito.mock(RoleOrTechnique.class);
		when(suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(Map.of("binder", role)));
		when(suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(RECOMMENDATIONS));
		when(suggestionsAiFacade.convertRolesToMarkdownList(any())).thenReturn(ROLE_MARKDOWN);
		when(suggestionsAiFacade.convertInstructionsToMarkdownList(eq(RECIPE.getRecipeInstructions()))).thenReturn(INSTRUCTIONS_MARKDOWN);
		when(suggestionsAiFacade.assessIngredientRole(ROLE_MARKDOWN, INGREDIENT_NAME, INSTRUCTIONS_MARKDOWN))
				.thenReturn(Uni.createFrom().item("missing-role"));
		when(suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(any())).thenReturn("");
		when(suggestionsAiFacade.matchIngredientToTrigger("", INGREDIENT_NAME, null)).thenReturn(Uni.createFrom().item("missing-trigger"));
		when(suggestionPrioritizer.prioritizeSuggestions(any(), eq(hasUserId), eq(List.of()))).thenReturn(Uni.createFrom().item(List.of()));

		SuggestionsRecipeAssessmentMessage result = sut.makeSuggestions(hasUserId, RECIPE)
				.await().indefinitely();

		assertThat(result.suggestions()).isEmpty();
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(suggestionDao, never()).findByRoleAndTriggerIngredient(any(), any(), any(), any());
		verify(suggestionPrioritizer).prioritizeSuggestions(any(), eq(hasUserId), eq(List.of()));
	}

	@Test
	void makeSuggestionsReturnsPrioritizedSuggestionsWithGeneratedTextWhenRuleMatches() {
		var hasUserId = hasUserId();
		RoleOrTechnique role = org.mockito.Mockito.mock(RoleOrTechnique.class);
		var triggerIngredient = org.mockito.Mockito.mock(eu.dietwise.services.model.suggestions.TriggerIngredient.class);
		var roleId = org.mockito.Mockito.mock(eu.dietwise.services.types.suggestions.RoleOrTechniqueId.class);
		var triggerIngredientId = org.mockito.Mockito.mock(eu.dietwise.services.types.suggestions.TriggerIngredientId.class);

		when(role.getId()).thenReturn(roleId);
		when(role.getName()).thenReturn("binder");
		when(roleId.asString()).thenReturn("role-1");
		when(triggerIngredient.getId()).thenReturn(triggerIngredientId);
		when(triggerIngredient.getName()).thenReturn("flour");
		when(triggerIngredientId.asString()).thenReturn("trigger-1");

		when(suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(Map.of("binder", role)));
		when(suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(Map.of("flour", triggerIngredient)));
		when(suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(Map.of()));
		when(suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(any())).thenReturn(Uni.createFrom().item(RECOMMENDATIONS));
		when(suggestionsAiFacade.convertRolesToMarkdownList(any())).thenReturn(ROLE_MARKDOWN);
		when(suggestionsAiFacade.convertInstructionsToMarkdownList(eq(RECIPE.getRecipeInstructions()))).thenReturn(INSTRUCTIONS_MARKDOWN);
		when(suggestionsAiFacade.assessIngredientRole(ROLE_MARKDOWN, INGREDIENT_NAME, INSTRUCTIONS_MARKDOWN))
				.thenReturn(Uni.createFrom().item("binder"));
		when(suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(any())).thenReturn("- flour");
		when(suggestionsAiFacade.matchIngredientToTrigger("- flour", INGREDIENT_NAME, role)).thenReturn(Uni.createFrom().item("flour"));
		when(suggestionDao.findByRoleAndTriggerIngredient(any(), eq(role), eq(triggerIngredient), eq(RECIPE.getRecipeIngredients().getFirst())))
				.thenReturn(Uni.createFrom().item(List.of(FIRST_SUGGESTION)));

		Suggestion prioritizedSuggestion = ImmutableSuggestion.copyOf(FIRST_SUGGESTION)
				.withText("We suggest: alternative-first instead of: " + INGREDIENT_NAME + " [prioritized]");
		when(suggestionPrioritizer.prioritizeSuggestions(any(), eq(hasUserId), any()))
				.thenReturn(Uni.createFrom().item(List.of(prioritizedSuggestion)));

		SuggestionsRecipeAssessmentMessage result = sut.makeSuggestions(hasUserId, RECIPE)
				.await().indefinitely();

		assertThat(result.suggestions()).containsExactly(prioritizedSuggestion);
		assertThat(result.suggestions().getFirst().getText())
				.isEqualTo("We suggest: alternative-first instead of: " + INGREDIENT_NAME + " [prioritized]");
		assertThat(persistenceContextFactory.getOpenedTransactions()).hasSize(1);
		verify(suggestionDao).findByRoleAndTriggerIngredient(any(), eq(role), eq(triggerIngredient), eq(RECIPE.getRecipeIngredients().getFirst()));
		verify(suggestionPrioritizer).prioritizeSuggestions(
				any(),
				eq(hasUserId),
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
				.await().indefinitely();

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
				.await().indefinitely();

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
