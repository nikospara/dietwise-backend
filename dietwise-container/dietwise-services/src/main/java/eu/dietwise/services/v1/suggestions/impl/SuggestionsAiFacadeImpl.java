package eu.dietwise.services.v1.suggestions.impl;

import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiSelector;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiSelector;
import eu.dietwise.services.v1.suggestions.FindBestRuleAiSelector;
import eu.dietwise.services.v1.suggestions.IngredientRoleAiSelector;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiSelector;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SuggestionsAiFacadeImpl implements SuggestionsAiFacade {
	private static final Logger LOG = LoggerFactory.getLogger(SuggestionsAiFacadeImpl.class);

	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final TriggerIngredientDao triggerIngredientDao;
	private final AlternativeIngredientDao alternativeIngredientDao;
	private final RecommendationDao recommendationDao;
	private final IngredientRoleAiSelector ingredientRoleAiSelector;
	private final TriggerIngredientMatcherAiSelector triggerIngredientMatcherAiSelector;
	private final IngredientMatchInRecommendationsAiSelector ingredientMatchInRecommendationsAiSelector;
	private final FindBestRuleAiSelector findBestRuleAiSelector;
	private final AlternativeSuggestionAiSelector alternativeSuggestionAiSelector;

	private final CachedUniMap<RecipeLanguage, Map<String, RoleOrTechnique>> cachedRoles = new CachedUniMap<>();
	private final CachedUniMap<RecipeLanguage, Map<String, TriggerIngredient>> cachedTriggerIngredients = new CachedUniMap<>();
	private final CachedUniMap<RecipeLanguage, Map<String, AlternativeIngredient>> cachedAlternatives = new CachedUniMap<>();
	private final CachedUniMap<RecipeLanguage, Map<String, RecommendationComponent>> cachedRecommendations = new CachedUniMap<>();

	public SuggestionsAiFacadeImpl(
			RoleOrTechniqueDao roleOrTechniqueDao,
			TriggerIngredientDao triggerIngredientDao,
			AlternativeIngredientDao alternativeIngredientDao,
			RecommendationDao recommendationDao,
			IngredientRoleAiSelector ingredientRoleAiSelector,
			TriggerIngredientMatcherAiSelector triggerIngredientMatcherAiSelector,
			IngredientMatchInRecommendationsAiSelector ingredientMatchInRecommendationsAiSelector,
			FindBestRuleAiSelector findBestRuleAiSelector,
			AlternativeSuggestionAiSelector alternativeSuggestionAiSelector
	) {
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.triggerIngredientDao = triggerIngredientDao;
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.recommendationDao = recommendationDao;
		this.ingredientRoleAiSelector = ingredientRoleAiSelector;
		this.triggerIngredientMatcherAiSelector = triggerIngredientMatcherAiSelector;
		this.ingredientMatchInRecommendationsAiSelector = ingredientMatchInRecommendationsAiSelector;
		this.findBestRuleAiSelector = findBestRuleAiSelector;
		this.alternativeSuggestionAiSelector = alternativeSuggestionAiSelector;
	}

	@Override
	public Uni<Map<String, RoleOrTechnique>> retrieveAllRolesKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang) {
		return cachedRoles.getOrLoad(lang, () -> roleOrTechniqueDao.findAll(em, lang)
				.map(list -> list.stream().collect(toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	private String normalizeRoleName(String roleName) {
		return roleName == null ? "" : roleName.trim().toLowerCase();
	}

	@Override
	public String convertRolesToMarkdownList(Collection<RoleOrTechnique> rolesOrTechniques) {
		return rolesOrTechniques.stream().map(r -> "- " + r.getName() + (r.getExplanationForLlm().map(e -> " (" + e + ")").orElse("")))
				.collect(Collectors.joining("\n"));
	}

	@Override
	public String convertInstructionsToMarkdownList(List<String> instructions) {
		return instructions.stream().map(i -> "- " + i).collect(Collectors.joining("\n"));
	}

	@Override
	public Uni<Map<String, TriggerIngredient>> retrieveAllTriggerIngredientsKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang) {
		return cachedTriggerIngredients.getOrLoad(lang, () -> triggerIngredientDao.findAll(em, lang)
				.map(list -> list.stream().collect(toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	private String normalizeTriggerIngredientName(String triggerIngredientName) {
		return triggerIngredientName == null ? "" : triggerIngredientName.trim().toLowerCase();
	}

	@Override
	public String convertTriggerIngredientsToMarkdownList(Collection<TriggerIngredient> triggerIngredients) {
		return triggerIngredients.stream().map(ti -> "- " + ti.getName() + (ti.getExplanationForLlm().map(e -> " (" + e + ")").orElse("")))
				.collect(Collectors.joining("\n"));
	}

	@Override
	public Uni<Map<String, AlternativeIngredient>> retrieveAllAlternativesKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang) {
		return cachedAlternatives.getOrLoad(lang, () -> alternativeIngredientDao.findAll(em, lang)
				.map(list -> list.stream().collect(toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	@Override
	public Uni<Map<String, RecommendationComponent>> retrieveAllRecommendationsKeyedByNormalizedName(ReactivePersistenceContext em, RecipeLanguage lang) {
		return cachedRecommendations.getOrLoad(lang, () -> recommendationDao.listAllRecommendationsForScoring(em, lang)
				.map(list -> list.stream().collect(toMap(this::normalizeRecommendationComponentName, Function.identity())))
				.map(Map::copyOf));
	}

	private String normalizeRecommendationComponentName(RecommendationComponent rc) {
		return rc.getComponentForScoring().asString().trim().toLowerCase();
	}

	@Override
	public String convertRecommendationsToMarkdownList(Collection<RecommendationComponent> recommendations) {
		return recommendations.stream()
				.map(c -> "- " + c.getComponentForScoring().asString() + c.getExplanationForLlm().map(e -> " (" + e + ')').orElse(""))
				.collect(Collectors.joining("\n"));
	}

	@Override
	public Uni<String> assessIngredientRole(RecipeLanguage lang, String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> ingredientRoleAiSelector.assessIngredientRole(
						lang, availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(this::normalizeRoleName);
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	@Override
	public Uni<String> matchIngredientToTrigger(RecipeLanguage lang, String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, RoleOrTechnique role) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> triggerIngredientMatcherAiSelector.matchIngredientToTrigger(
						lang, availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role != null ? role.getName() : null))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(this::normalizeTriggerIngredientName);
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	@Override
	public Uni<Set<String>> matchIngredientsWithRecommendations(RecipeLanguage lang, String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe) {
		Context callerContext = Vertx.currentContext();
		Uni<Set<String>> resultUni = Uni.createFrom().item(() -> ingredientMatchInRecommendationsAiSelector.matchIngredientsWithRecommendations(
						lang, availableRecommendationsAsMarkdownList, ingredientNameInRecipe))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(recommendationsFromAi -> postProcessRecommendationsFromAi(ingredientNameInRecipe, recommendationsFromAi));
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	private Set<String> postProcessRecommendationsFromAi(String ingredientNameInRecipe, String recommendationsFromAi) {
		LOG.debug("matchIngredientsWithRecommendations responded for ingredient <{}>: {}", ingredientNameInRecipe, recommendationsFromAi);
		if (recommendationsFromAi == null) return Collections.emptySet();
		recommendationsFromAi = recommendationsFromAi.trim();
		return Set.of(recommendationsFromAi.split("\\r?\\n"));
	}

	@Override
	public Uni<String> findBestRule(
			RecipeLanguage lang,
			String ingredientNameInRecipe,
			RoleOrTechnique role,
			TriggerIngredient triggerIngredient,
			Collection<RecommendationComponent> dietaryComponents,
			Collection<Rule> filteredRules
	) {
		String dietaryComponentsMarkdownList = convertRecommendationsToMarkdownList(dietaryComponents);
		var mappedRules = mapRules(filteredRules);
		String filteredRulesMarkdownList = convertMappedRulesToMarkdownList(mappedRules);
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> findBestRuleAiSelector.findBestRule(
						lang,
						ingredientNameInRecipe,
						role == null ? "-" : role.getName(),
						triggerIngredient.getName(),
						dietaryComponentsMarkdownList,
						filteredRulesMarkdownList
				))
				.map(ruleId -> {
					if (ruleId == null) return "-AI returned null-";
					return Optional.ofNullable(mappedRules.get(ruleId.trim()))
							.map(Rule::getId)
							.map(RepresentableAsString::asString)
							.orElse("-AI returned unknown: " + ruleId + "-");
				})
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	private Map<String, Rule> mapRules(Collection<Rule> rules) {
		int index = 1;
		var result = new LinkedHashMap<String, Rule>();
		for (Rule r : rules) {
			result.put(Integer.toString(index++), r);
		}
		return result;
	}

	private String convertMappedRulesToMarkdownList(Map<String, Rule> rules) {
		return rules.entrySet().stream()
				.flatMap(e -> Stream.of(
						"- id: " + e.getKey(),
						"    - recommendation: " + e.getValue().getRecommendation().asString(),
						"    - role: " + e.getValue().getRoleOrTechnique().asString()
				))
				.collect(Collectors.joining("\n"));
	}

	@Override
	public Uni<String> suggestAlternatives(RecipeLanguage lang, String ingredientNameInRecipe, RoleOrTechnique role, List<Suggestion> alternatives) {
		String alternativesAsMarkdownList = convertSuggestionsToMarkdownList(alternatives);
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> alternativeSuggestionAiSelector.suggestAlternatives(
						lang,
						ingredientNameInRecipe,
						role == null ? "-" : role.getName(),
						alternativesAsMarkdownList
				))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor());
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	private String convertSuggestionsToMarkdownList(List<Suggestion> alternatives) {
		return alternatives.stream()
				.flatMap(a -> Stream.of(
						"- " + a.getAlternative().asString(),
						a.getRationale().map(r -> "    - Rationale: " + r).orElse(null),
						a.getRestriction().map(r -> "    - Restrictions: " + r).orElse(null),
						a.getEquivalence().map(e -> "    - Equivalence: " + e).orElse(null),
						a.getTechniqueNotes().map(n -> "    - Technique notes: " + n).orElse(null)
				))
				.filter(Objects::nonNull)
				.collect(Collectors.joining("\n"));
	}
}
