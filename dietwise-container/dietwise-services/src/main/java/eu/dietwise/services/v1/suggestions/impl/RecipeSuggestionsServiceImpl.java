package eu.dietwise.services.v1.suggestions.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.dao.reactive.ReactivePersistenceTxContext;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.dao.PersonalInfoDao;
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
import eu.dietwise.v1.types.RecipeLanguage;
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
	private final PersonalInfoDao personalInfoDao;
	private final SuggestionsAiFacade suggestionsAiFacade;
	private final SuggestionDao suggestionDao;
	private final RuleDao ruleDao;
	private final SuggestionPrioritizer suggestionPrioritizer;
	private final UserSuggestionStatsEntityDao userSuggestionStatsEntityDao;

	public RecipeSuggestionsServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			PersonalInfoDao personalInfoDao,
			SuggestionsAiFacade suggestionsAiFacade,
			SuggestionDao suggestionDao, RuleDao ruleDao,
			SuggestionPrioritizer suggestionPrioritizer,
			UserSuggestionStatsEntityDao userSuggestionStatsEntityDao
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.personalInfoDao = personalInfoDao;
		this.suggestionsAiFacade = suggestionsAiFacade;
		this.suggestionDao = suggestionDao;
		this.ruleDao = ruleDao;
		this.suggestionPrioritizer = suggestionPrioritizer;
		this.userSuggestionStatsEntityDao = userSuggestionStatsEntityDao;
	}

	@Override
	public Uni<MakeSuggestionsResult> makeSuggestions(UUID correlationId, HasUserId hasUserId, RecipeLanguage lang, Recipe recipe) {
		return persistenceContextFactory.withTransaction(tx -> makeSuggestions(tx, hasUserId, lang, recipe));
	}

	private Uni<MakeSuggestionsResult> makeSuggestions(ReactivePersistenceTxContext tx, HasUserId hasUserId, RecipeLanguage lang, Recipe recipe) {
		return forcm(
				readAllNecessaryData(tx, hasUserId, lang),
				extractSuggestionsForRecipePerIngredient(tx, lang, recipe),
				prioritizeSuggestions(tx),
				data -> new MakeSuggestionsResult(
						new SuggestionsRecipeAssessmentMessage(data.suggestions()),
						data.recommendations()
				)
		);
	}

	private Uni<RecipeSuggestionNecessaryData> readAllNecessaryData(ReactivePersistenceTxContext tx, HasUserId hasUserId, RecipeLanguage lang) {
		return forcm(
				personalInfoDao.findByUser(tx, hasUserId),
				_ -> suggestionsAiFacade.retrieveAllRolesKeyedByNormalizedName(tx, lang),
				_ -> suggestionsAiFacade.retrieveAllTriggerIngredientsKeyedByNormalizedName(tx, lang),
				_ -> suggestionsAiFacade.retrieveAllAlternativesKeyedByNormalizedName(tx, lang),
				_ -> suggestionsAiFacade.retrieveAllRecommendationsKeyedByNormalizedName(tx, lang),
				RecipeSuggestionNecessaryData::new
		);
	}

	private Function<? super RecipeSuggestionNecessaryData, Uni<? extends SuggestionsAndRecommendationsPerIngredient>> extractSuggestionsForRecipePerIngredient(
			ReactivePersistenceTxContext tx,
			RecipeLanguage lang,
			Recipe recipe
	) {
		return data -> {
			String availableRecommendationsAsMarkdownList = suggestionsAiFacade.convertRecommendationsToMarkdownList(data.recommendations().values());
			return Multi.createFrom().iterable(recipe.getRecipeIngredients())
					.onItem().transformToUniAndConcatenate(processIngredient(tx, recipe, lang, data, availableRecommendationsAsMarkdownList))
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
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			String availableRecommendationsAsMarkdownList
	) {
		return ingredient -> forc(
				determineRoleOrTechnique(recipe, lang, data, ingredient),
				determineTriggerIngredient(lang, data, ingredient),
				loadMatchingRulesAndDetermineComposition(tx, lang, data, availableRecommendationsAsMarkdownList, ingredient),
				identifyBestFittingRule(lang, ingredient),
				loadAlternativesFromDbAndSelectBest(tx, lang, data, ingredient),
				postProcessAlternatives(recipe, data, ingredient)
		).onFailure(NonFatalIngredientProcessingException.class).recoverWithItem(t -> {
			LOG.warn("Failed to process ingredient <{}>: {}", ingredient.getNameInRecipe(), t.getMessage());
			return SuggestionsAndComponents.empty(ingredient);
		});
	}

	private Uni<RoleOrTechnique> determineRoleOrTechnique(Recipe recipe, RecipeLanguage lang, RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String rolesMarkdownList = suggestionsAiFacade.convertRolesToMarkdownList(data.roles().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		String instructionsAsMarkdownList = suggestionsAiFacade.convertInstructionsToMarkdownList(recipe.getRecipeInstructions());
		return suggestionsAiFacade.assessIngredientRole(lang, rolesMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList)
				.invoke(r -> LOG.debug("assessIngredientRole for <{}>: {}", ingredientNameInRecipe, r))
				.map(data.roles()::get);
	}

	private Function<? super RoleOrTechnique, Uni<? extends TriggerIngredient>> determineTriggerIngredient(RecipeLanguage lang, RecipeSuggestionNecessaryData data, Ingredient ingredient) {
		String availableTriggerIngredientsAsMarkdownList = suggestionsAiFacade.convertTriggerIngredientsToMarkdownList(data.triggerIngredients().values());
		String ingredientNameInRecipe = ingredient.getNameInRecipe();
		return role -> suggestionsAiFacade.matchIngredientToTrigger(lang, availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role)
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
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			String availableRecommendationsAsMarkdownList,
			Ingredient ingredient
	) {
		// let's ask the DB and the AI in parallel, they are independent operations
		return triggerIngredient -> Uni.combine().all().unis(
				findByTriggerIngredientAndThrowIfNotFound(tx, triggerIngredient, lang),
				suggestionsAiFacade.matchIngredientsWithRecommendations(lang, availableRecommendationsAsMarkdownList, ingredient.getNameInRecipe())
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

	private Uni<List<Rule>> findByTriggerIngredientAndThrowIfNotFound(
			ReactivePersistenceTxContext tx,
			TriggerIngredient triggerIngredient,
			RecipeLanguage lang
	) {
		return ruleDao.findByTriggerIngredient(tx, triggerIngredient, lang)
				.flatMap(rules ->
						rules.isEmpty()
								? Uni.createFrom().failure(() -> new NoRulesForTriggerIngredientException(triggerIngredient))
								: Uni.createFrom().item(rules)
				);
	}

	private Function3<? super RoleOrTechnique, ? super TriggerIngredient, ? super RulesAndComponents, Uni<? extends Rule>> identifyBestFittingRule(
			RecipeLanguage lang,
			Ingredient ingredient
	) {
		return (role, trigger, rulesAndComponents) ->
				suggestionsAiFacade.findBestRule(lang, ingredient.getNameInRecipe(), role, trigger, rulesAndComponents.components(), rulesAndComponents.rules())
						.map(determineRuleFromStringId(rulesAndComponents.rules()));
	}

	private Function<String, Rule> determineRuleFromStringId(List<Rule> rules) {
		return ruleId -> {
			String normalizedRuleId = ruleId.trim().toLowerCase();
			return rules.stream().filter(r -> r.getId().asString().toLowerCase().equals(normalizedRuleId)).findFirst()
					.orElseThrow(() -> new NoMatchingRuleException(ruleId));
		};
	}

	private Function4<? super RoleOrTechnique, ? super TriggerIngredient, ? super RulesAndComponents, ? super Rule, Uni<? extends List<Suggestion>>> loadAlternativesFromDbAndSelectBest(
			ReactivePersistenceTxContext tx,
			RecipeLanguage lang,
			RecipeSuggestionNecessaryData data,
			Ingredient ingredient
	) {
		return (role, _, _, rule) -> forc(
				suggestionDao.retrieveByRule(tx, rule, data.personalInfo().getCountry(), ingredient, lang),
				suggestions -> suggestionsAiFacade.suggestAlternatives(lang, ingredient.getNameInRecipe(), role, suggestions),
				(suggestions, responseFromAi) -> {
					// TODO dummy, process response from AI
					if (LOG.isDebugEnabled()) {
						var suggestionsStr = suggestions.stream().map(Suggestion::getAlternative).map(RepresentableAsString::asString).collect(Collectors.joining(","));
						LOG.debug("Suggest alternatives for <{}>, initial list is {}:\n{}", ingredient.getNameInRecipe(), suggestionsStr, responseFromAi);
					}
					return Uni.createFrom().item(suggestions);
				}
		);
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
		// TODO Add language
		return (rulesAndComponents, _, list) -> {
			List<Suggestion> result = list.stream()
					.map(s -> (Suggestion) ImmutableSuggestion.copyOf(s).withText("We suggest: " + s.getAlternative().asString() + " instead of: " + ingredient.getNameInRecipe()))
					.toList();
			return Uni.createFrom().item(new SuggestionsAndComponents(ingredient, result, rulesAndComponents.components()));
		};
	}

	private BiFunction<? super RecipeSuggestionNecessaryData, ? super SuggestionsAndRecommendationsPerIngredient, Uni<? extends SuggestionsAndRecommendationsPerIngredient>> prioritizeSuggestions(ReactivePersistenceTxContext tx) {
		return (data, suggestionsAndRecommendations) -> suggestionPrioritizer.prioritizeSuggestions(tx, data.personalInfo(), suggestionsAndRecommendations.suggestions())
				.map(suggestionsAndRecommendations::withSuggestions);
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
