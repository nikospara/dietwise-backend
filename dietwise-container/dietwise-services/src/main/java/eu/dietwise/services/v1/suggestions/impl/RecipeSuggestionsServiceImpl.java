package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.suggestions.RecipeSuggestionsService;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.Suggestion;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RecipeSuggestionsServiceImpl implements RecipeSuggestionsService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final SuggestionsAiFacade suggestionsAiFacade;
	private final SuggestionDao suggestionDao;

	public RecipeSuggestionsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory, SuggestionsAiFacade suggestionsAiFacade, SuggestionDao suggestionDao) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.suggestionsAiFacade = suggestionsAiFacade;
		this.suggestionDao = suggestionDao;
	}

	@Override
	public Uni<SuggestionsRecipeAssessmentMessage> makeSuggestions(User user, Recipe recipe) {
		return persistenceContextFactory.withTransaction(tx -> makeSuggestions(tx, user, recipe));
	}

	private Uni<SuggestionsRecipeAssessmentMessage> makeSuggestions(ReactivePersistenceTxContext tx, User user, Recipe recipe) {
		return forc(
				readAllNecessaryData(tx),
				extractSuggestionsForRecipe(tx, recipe),
				prioritizeSuggestions(tx, user),
				calculateScore(tx, recipe)
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
				.map(suggestionsAiFacade::normalizeRoleName)
				.map(data.roles()::get);
	}

	private Function<? super RoleOrTechnique, Uni<? extends TriggerIngredient>> matchIngredientToTrigger(RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String availableTriggerIngredientsAsMarkdownList = suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(data.triggerIngredients().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		return role -> suggestionsAiFacade.matchIngredientToTrigger(availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role)
				.map(suggestionsAiFacade::normalizeTriggerIngredientName)
				.map(data.triggerIngredients()::get);
	}

	private BiFunction<? super RoleOrTechnique, ? super TriggerIngredient, Uni<? extends List<Suggestion>>> extractRuleAndAlternativesFromDb(
			ReactivePersistenceTxContext tx, Ingredient ingredient) {
		return (role, triggerIngredient) -> {
			if (role == null || triggerIngredient == null) return Uni.createFrom().item(Collections.emptyList());
			else return suggestionDao.findByRoleAndTriggerIngredient(tx, role, triggerIngredient, ingredient);
		};
	}

	private Function<? super List<Suggestion>, Uni<? extends List<Suggestion>>> suggestAlternatives(Recipe recipe, RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		// TODO Dummy for now, implement to (a) filter the suggestions (b) fill-in the text
		return list -> {
			List<Suggestion> result = list.stream()
					.filter(_ -> Math.random() < 0.8)
					.map(s -> (Suggestion) ImmutableSuggestion.copyOf(s).withText("We suggest: " + s.getAlternative().asString() + " instead of: " + ingredient.getNameInRecipe()))
					.toList();
			return Uni.createFrom().item(result);
		};
	}

	private Function<? super List<Suggestion>, Uni<? extends List<Suggestion>>> prioritizeSuggestions(ReactivePersistenceTxContext tx, User user) {
		// TODO Noop for now, implement
		return list -> Uni.createFrom().item(list);
	}

	private Function<? super List<Suggestion>, Uni<? extends SuggestionsRecipeAssessmentMessage>> calculateScore(ReactivePersistenceTxContext tx, Recipe recipe) {
		// TODO Dummy for now, implement
		return list -> Uni.createFrom().item(new SuggestionsRecipeAssessmentMessage(2.5, list));
	}
}
