package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.statistics.UserSuggestionStatsEntityDao;
import eu.dietwise.dao.suggestions.RuleDao;
import eu.dietwise.dao.suggestions.SuggestionDao;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.suggestions.MakeSuggestionsResult;
import eu.dietwise.services.v1.suggestions.RecipeSuggestionsService;
import eu.dietwise.services.v1.suggestions.SuggestionPrioritizer;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.SuggestionStats;
import eu.dietwise.v1.types.SuggestionTemplateId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Functions.Function3;
import io.smallrye.mutiny.tuples.Functions.Function4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeSuggestionsServiceImpl implements RecipeSuggestionsService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeSuggestionsServiceImpl.class);

	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final SuggestionsAiFacade suggestionsAiFacade;
	private final SuggestionDao suggestionDao;
	private final RuleDao ruleDao;
	private final SuggestionPrioritizer suggestionPrioritizer;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;

	public RecipeSuggestionsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			SuggestionsAiFacade suggestionsAiFacade,
			SuggestionDao suggestionDao, RuleDao ruleDao,
			SuggestionPrioritizer suggestionPrioritizer,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.suggestionsAiFacade = suggestionsAiFacade;
		this.suggestionDao = suggestionDao;
		this.ruleDao = ruleDao;
		this.suggestionPrioritizer = suggestionPrioritizer;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
	}

	@Override
	public Uni<MakeSuggestionsResult> makeSuggestions(UUID correlationId, HasUserId hasUserId, Recipe recipe) {
		return persistenceContextFactory.withTransaction(tx -> makeSuggestions(tx, hasUserId, recipe));
	}

	private Uni<MakeSuggestionsResult> makeSuggestions(ReactivePersistenceTxContext tx, HasUserId hasUserId, Recipe recipe) {
		return forcm(
				readAllNecessaryData(tx),
				extractSuggestionsForRecipePerIngredient(tx, recipe),
				prioritizeSuggestions(tx, hasUserId),
				data -> new MakeSuggestionsResult(
						new SuggestionsRecipeAssessmentMessage(data.suggestions()),
						data.recommendations()
				)
		);
	}

	private Uni<RecipeSuggestionNecessaryData> readAllNecessaryData(ReactivePersistenceTxContext tx) {
		return forcm(
				suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(tx),
				_ -> suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(tx),
				(_, _) -> suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(tx),
				(_, _, _) -> suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(tx),
				RecipeSuggestionNecessaryData::new
		);
	}

	private Function<? super RecipeSuggestionNecessaryData, Uni<? extends SuggestionsAndRecommendationsPerIngredient>> extractSuggestionsForRecipePerIngredient(
			ReactivePersistenceTxContext tx,
			Recipe recipe
	) {
		return data -> {
			String availableRecommendationsAsMarkdownList = data.recommendations().values().stream()
					.map(c -> "- " + c.getComponentForScoring().asString() + c.getExplanationForLlm().map(e -> " (" + e + ')').orElse(""))
					.collect(Collectors.joining("\n"));
			return Multi.createFrom().iterable(recipe.getRecipeIngredients())
					.onItem().transformToUniAndConcatenate(processIngredient(tx, recipe, data, availableRecommendationsAsMarkdownList))
					.collect()
					.in(
							SuggestionsAndRecommendationsPerIngredient::emptyMutable,
							(acc, cur) -> {
								if (cur.ingredient() != null) {
									acc.suggestions().addAll(cur.suggestions());
									acc.recommendations().put(cur.ingredient().getId(), cur.components());
								}
							}
					);
		};
	}

	private Function<? super Ingredient, Uni<? extends SuggestionsAndComponents>> processIngredient(
			ReactivePersistenceTxContext tx,
			Recipe recipe,
			RecipeSuggestionNecessaryData data,
			String availableRecommendationsAsMarkdownList
	) {
		return ingredient -> forc(
				determineRoleOrTechnique(recipe, data, ingredient),
				determineTriggerIngredient(data, ingredient),
				loadMatchingRulesAndDetermineComposition(tx, data, availableRecommendationsAsMarkdownList, ingredient),
				identifyBestFittingRule(),
				loadAlternativesFromDbAndSelectBest(tx, ingredient),
				postProcessAlternatives(recipe, data, ingredient)
		).onFailure(NonFatalIngredientProcessingException.class).recoverWithItem(t -> {
			LOG.warn("Failed to process ingredient <{}>: {}", ingredient.getNameInRecipe(), t.getMessage());
			return SuggestionsAndComponents.empty(ingredient);
		});
	}

	private Uni<RoleOrTechnique> determineRoleOrTechnique(Recipe recipe, RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String rolesMarkdownList = suggestionsAiFacade.convertRolesToMarkdownList(data.roles().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		String instructionsAsMarkdownList = suggestionsAiFacade.convertInstructionsToMarkdownList(recipe.getRecipeInstructions());
		return suggestionsAiFacade.assessIngredientRole(rolesMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList)
				.invoke(r -> LOG.debug("assessIngredientRole for <{}>: {}", ingredientNameInRecipe, r))
				.map(data.roles()::get);
	}

	private Function<? super RoleOrTechnique, Uni<? extends TriggerIngredient>> determineTriggerIngredient(RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String availableTriggerIngredientsAsMarkdownList = suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(data.triggerIngredients().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		return role -> suggestionsAiFacade.matchIngredientToTrigger(availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role)
				.flatMap(triggerIngredientFromAi -> {
					LOG.debug("matchIngredientToTrigger for <{}>: {}", ingredientNameInRecipe, triggerIngredientFromAi);
					TriggerIngredient triggerIngredient = data.triggerIngredients().get(triggerIngredientFromAi);
					return triggerIngredient == null
							? Uni.createFrom().failure(() -> new TriggerIngredientFromAiNotInDbException(ingredient, triggerIngredientFromAi))
							: Uni.createFrom().item(triggerIngredient);
				});
	}

	private Function<? super TriggerIngredient, Uni<? extends RulesAndComponents>> loadMatchingRulesAndDetermineComposition(
			ReactivePersistenceTxContext tx,
			RecipeSuggestionNecessaryData data,
			String availableRecommendationsAsMarkdownList,
			Ingredient ingredient
	) {
		// let's ask the DB and the AI in parallel, they are independent operations
		return triggerIngredient -> Uni.combine().all().unis(
				findByTriggerIngredientAndThrowIfNotFound(tx, triggerIngredient),
				suggestionsAiFacade.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredient.getNameInRecipe())
		).with((rules, componentNames) -> {
			var components = componentNames.stream()
					.filter(Objects::nonNull)
					.map(name -> name.trim().toLowerCase())
					.map(key -> data.recommendations().get(key))
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
			return new RulesAndComponents(rules, components);
		});
	}

	private Uni<List<Rule>> findByTriggerIngredientAndThrowIfNotFound(ReactivePersistenceTxContext tx, TriggerIngredient triggerIngredient) {
		return ruleDao.findByTriggerIngredient(tx, triggerIngredient)
				.flatMap(rules ->
						rules.isEmpty()
								? Uni.createFrom().failure(() -> new NoRulesForTriggerIngredientException(triggerIngredient))
								: Uni.createFrom().item(rules)
				);
	}

	private Function3<? super RoleOrTechnique, ? super TriggerIngredient, ? super RulesAndComponents, Uni<? extends Rule>> identifyBestFittingRule() {
		return (role, trigger, rulesAndComponents) -> {
			// TODO Dummy, implement by calling the AI
			return Uni.createFrom().item(rulesAndComponents.rules().getFirst());
		};
	}

	private Function4<? super RoleOrTechnique, ? super TriggerIngredient, ? super RulesAndComponents, ? super Rule, Uni<? extends List<Suggestion>>> loadAlternativesFromDbAndSelectBest(
			ReactivePersistenceTxContext tx,
			Ingredient ingredient
	) {
		return (role, trigger, rulesAndComponents, rule) -> {
			return forc(
					suggestionDao.findByRule(tx, rule, ingredient),
					suggestions -> {
						// TODO dummy, implement with AI
						return Uni.createFrom().item(suggestions);
					}
			);
		};
	}

	private String toLogString(RoleOrTechnique role) {
		return role == null ? "null" : role.getName() + " (" + role.getId().asString() + ")";
	}

	private String toLogString(TriggerIngredient triggerIngredient) {
		return triggerIngredient == null ? "null" : triggerIngredient.getName() + " (" + triggerIngredient.getId().asString() + ")";
	}

	private Function3<? super RulesAndComponents, ? super Rule, ? super List<Suggestion>, Uni<? extends SuggestionsAndComponents>> postProcessAlternatives(
			Recipe recipe,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient
	) {
		// TODO Dummy for now, implement to fill-in the text - filtering happens in loadAlternativesFromDbAndSelectBest
		return (rulesAndComponents, _, list) -> {
			List<Suggestion> result = list.stream()
					.map(s -> (Suggestion) ImmutableSuggestion.copyOf(s).withText("We suggest: " + s.getAlternative().asString() + " instead of: " + ingredient.getNameInRecipe()))
					.toList();
			return Uni.createFrom().item(new SuggestionsAndComponents(ingredient, result, rulesAndComponents.components()));
		};
	}

	private Function<? super SuggestionsAndRecommendationsPerIngredient, Uni<? extends SuggestionsAndRecommendationsPerIngredient>> prioritizeSuggestions(ReactivePersistenceTxContext tx, HasUserId hasUserId) {
		return data -> suggestionPrioritizer.prioritizeSuggestions(tx, hasUserId, data.suggestions())
				.map(data::withSuggestions);
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

	@Override
	public Uni<SuggestionsRecipeAssessmentMessage> enrichWithStatistics(UUID correlationId, String applicationId, HasUserId hasUserId, SuggestionsRecipeAssessmentMessage message) {
		Set<SuggestionTemplateId> suggestionIds = message.getSuggestionTemplateIds();
		return persistenceContextFactory.withoutTransaction(em ->
				forcm(
						userSuggestionStatsEntityDao.retrieveUserSuggestionStats(em, applicationId, hasUserId, suggestionIds),
						_ -> userSuggestionStatsEntityDao.retrieveTotalSuggestionStats(em, applicationId, suggestionIds),
						(u, t) -> enrichWithStatistics(message, u, t)
				)
		);
	}

	private SuggestionsRecipeAssessmentMessage enrichWithStatistics(
			SuggestionsRecipeAssessmentMessage message,
			Map<SuggestionTemplateId, SuggestionStats> userStats,
			Map<SuggestionTemplateId, SuggestionStats> totalStats
	) {
		List<Suggestion> enrichedSuggestions = message.suggestions().stream().map(s -> {
			SuggestionStats userStatsForSuggestion = userStats.getOrDefault(s.getId(), SuggestionStats.ALL_ZEROES);
			SuggestionStats totalStatsForSuggestion = totalStats.getOrDefault(s.getId(), SuggestionStats.ALL_ZEROES);
			return (Suggestion) ImmutableSuggestion.builder().from(s).userSuggestionStats(userStatsForSuggestion).totalSuggestionStats(totalStatsForSuggestion).build();
		}).toList();
		return new SuggestionsRecipeAssessmentMessage(enrichedSuggestions);
	}
}
