package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.suggestions.RecipeSuggestionsService;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeSuggestionsServiceImpl implements RecipeSuggestionsService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeSuggestionsServiceImpl.class);

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final SuggestionsAiFacade suggestionsAiFacade;
	private final SuggestionDao suggestionDao;
	private final SuggestionPrioritizer suggestionPrioritizer;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;

	public RecipeSuggestionsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			SuggestionsAiFacade suggestionsAiFacade,
			SuggestionDao suggestionDao,
			SuggestionPrioritizer suggestionPrioritizer,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.suggestionsAiFacade = suggestionsAiFacade;
		this.suggestionDao = suggestionDao;
		this.suggestionPrioritizer = suggestionPrioritizer;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
	}

	@Override
	public Uni<SuggestionsRecipeAssessmentMessage> makeSuggestions(HasUserId hasUserId, Recipe recipe) {
		return persistenceContextFactory.withTransaction(tx -> makeSuggestions(tx, hasUserId, recipe));
	}

	private Uni<SuggestionsRecipeAssessmentMessage> makeSuggestions(ReactivePersistenceTxContext tx, HasUserId hasUserId, Recipe recipe) {
		return forcm(
				readAllNecessaryData(tx),
				extractSuggestionsForRecipe(tx, recipe),
				prioritizeSuggestions(tx, hasUserId),
				SuggestionsRecipeAssessmentMessage::new
		);
	}

	private Uni<RecipeSuggestionNecessaryData> readAllNecessaryData(ReactivePersistenceTxContext tx) {
		return forcm(
				suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(tx),
				_ -> suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(tx),
				(_, _) -> suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(tx),
				RecipeSuggestionNecessaryData::new
		);
	}

	private Function<? super RecipeSuggestionNecessaryData, Uni<? extends List<Suggestion>>> extractSuggestionsForRecipe(
			ReactivePersistenceTxContext tx, Recipe recipe) {
		return data -> Multi.createFrom().iterable(recipe.getRecipeIngredients())
				.onItem().transformToUniAndConcatenate(processIngredient(tx, recipe, data))
				.onItem().transformToMultiAndConcatenate(Multi.createFrom()::iterable)
				.collect().asList();
	}

	private Function<? super Ingredient, Uni<? extends List<Suggestion>>> processIngredient(
			ReactivePersistenceTxContext tx, Recipe recipe, RecipeSuggestionNecessaryData data) {
		return ingredient -> forc(
				assessIngredientRole(recipe, data, ingredient),
				matchIngredientToTrigger(data, ingredient),
				extractRuleAndAlternativesFromDb(tx, ingredient),
				suggestAlternatives(recipe, data, ingredient)
		);
	}

	private Uni<RoleOrTechnique> assessIngredientRole(Recipe recipe, RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String rolesMarkdownList = suggestionsAiFacade.convertRolesToMarkdownList(data.roles().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		String instructionsAsMarkdownList = suggestionsAiFacade.convertInstructionsToMarkdownList(recipe.getRecipeInstructions());
		return suggestionsAiFacade.assessIngredientRole(rolesMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList)
				.invoke(r -> LOG.debug("assessIngredientRole for {}: {}", ingredientNameInRecipe, r))
				.map(data.roles()::get);
	}

	private Function<? super RoleOrTechnique, Uni<? extends TriggerIngredient>> matchIngredientToTrigger(RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String availableTriggerIngredientsAsMarkdownList = suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(data.triggerIngredients().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		return role -> suggestionsAiFacade.matchIngredientToTrigger(availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role)
				.invoke(r -> LOG.debug("matchIngredientToTrigger for {}: {}", ingredientNameInRecipe, r))
				.map(data.triggerIngredients()::get);
	}

	private BiFunction<? super RoleOrTechnique, ? super TriggerIngredient, Uni<? extends List<Suggestion>>> extractRuleAndAlternativesFromDb(
			ReactivePersistenceTxContext tx, Ingredient ingredient) {
		return (role, triggerIngredient) -> {
			LOG.debug("Rule matching coordinates for {}: {} / {}", ingredient.getNameInRecipe(), toLogString(role), toLogString(triggerIngredient));
			if (role == null || triggerIngredient == null) return Uni.createFrom().item(Collections.emptyList());
			else return suggestionDao.findByRoleAndTriggerIngredient(tx, role, triggerIngredient, ingredient);
		};
	}

	private String toLogString(RoleOrTechnique role) {
		return role == null ? "null" : role.getName() + " (" + role.getId().asString() + ")";
	}

	private String toLogString(TriggerIngredient triggerIngredient) {
		return triggerIngredient == null ? "null" : triggerIngredient.getName() + " (" + triggerIngredient.getId().asString() + ")";
	}

	private Function<? super List<Suggestion>, Uni<? extends List<Suggestion>>> suggestAlternatives(Recipe recipe, RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		// TODO Dummy for now, implement to (a) filter the alternatives (b) fill-in the text
		return list -> {
			List<Suggestion> result = list.stream()
					.filter(_ -> Math.random() < 0.8)
					.map(s -> (Suggestion) ImmutableSuggestion.copyOf(s).withText("We suggest: " + s.getAlternative().asString() + " instead of: " + ingredient.getNameInRecipe()))
					.toList();
			return Uni.createFrom().item(result);
		};
	}

	private Function<? super List<Suggestion>, Uni<? extends List<Suggestion>>> prioritizeSuggestions(ReactivePersistenceTxContext tx, HasUserId hasUserId) {
		return list -> suggestionPrioritizer.prioritizeSuggestions(tx, hasUserId, list);
	}

	@Override
	public Uni<Void> increaseTimesSuggested(UUID correlationId, String applicationId, HasUserId hasUserId, HasSuggestionTemplateIds suggestions) {
		return persistenceContextFactory.withTransaction(tx ->
				Multi.createFrom().iterable(suggestions.getSuggestionTemplateIds())
						.onItem().transformToUniAndConcatenate(sid -> userSuggestionStatsEntityDao.increaseTimesSuggested(tx, applicationId, hasUserId, sid))
						.onItem().ignoreAsUni()
						.onFailure()
						.recoverWithItem(t -> {
							LOG.error("Failed to increase times suggested for user <{}> {}: {}", correlationId, hasUserId, t.getMessage(), t);
							return null;
						})
		);
	}
}
