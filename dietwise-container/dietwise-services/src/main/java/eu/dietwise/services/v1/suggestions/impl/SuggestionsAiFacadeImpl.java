package eu.dietwise.services.v1.suggestions.impl;

import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.model.suggestions.AlternativeIngredient;
import eu.dietwise.services.model.suggestions.RoleOrTechnique;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiService;
import eu.dietwise.services.v1.scoring.impl.ScoringAiFacadeImpl;
import eu.dietwise.services.v1.suggestions.IngredientRoleAiService;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SuggestionsAiFacadeImpl implements SuggestionsAiFacade {
	private static final Logger LOG = LoggerFactory.getLogger(ScoringAiFacadeImpl.class);

	private final RoleOrTechniqueDao roleOrTechniqueDao;
	private final TriggerIngredientDao triggerIngredientDao;
	private final AlternativeIngredientDao alternativeIngredientDao;
	private final RecommendationDao recommendationDao;
	private final IngredientRoleAiService ingredientRoleAiService;
	private final TriggerIngredientMatcherAiService triggerIngredientMatcherAiService;
	private final IngredientMatchInRecommendationsAiService ingredientMatchInRecommendationsAiService;

	private final CachedUniValue<Map<String, RoleOrTechnique>> cachedRoles = new CachedUniValue<>();
	private final CachedUniValue<Map<String, TriggerIngredient>> cachedTriggerIngredients = new CachedUniValue<>();
	private final CachedUniValue<Map<String, AlternativeIngredient>> cachedAlternatives = new CachedUniValue<>();

	public SuggestionsAiFacadeImpl(
			RoleOrTechniqueDao roleOrTechniqueDao,
			TriggerIngredientDao triggerIngredientDao,
			AlternativeIngredientDao alternativeIngredientDao,
			RecommendationDao recommendationDao,
			IngredientRoleAiService ingredientRoleAiService,
			TriggerIngredientMatcherAiService triggerIngredientMatcherAiService,
			IngredientMatchInRecommendationsAiService ingredientMatchInRecommendationsAiService
	) {
		this.roleOrTechniqueDao = roleOrTechniqueDao;
		this.triggerIngredientDao = triggerIngredientDao;
		this.alternativeIngredientDao = alternativeIngredientDao;
		this.recommendationDao = recommendationDao;
		this.ingredientRoleAiService = ingredientRoleAiService;
		this.triggerIngredientMatcherAiService = triggerIngredientMatcherAiService;
		this.ingredientMatchInRecommendationsAiService = ingredientMatchInRecommendationsAiService;
	}

	@Override
	public Uni<Map<String, RoleOrTechnique>> retrieveAllRolesKeyedByNormalizedName(ReactivePersistenceContext em) {
		return cachedRoles.getOrLoad(() -> roleOrTechniqueDao.findAll(em)
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
	public Uni<Map<String, TriggerIngredient>> retrieveAllTriggerIngredientsKeyedByNormalizedName(ReactivePersistenceContext em) {
		return cachedTriggerIngredients.getOrLoad(() -> triggerIngredientDao.findAll(em)
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
	public Uni<Map<String, AlternativeIngredient>> retrieveAllAlternativesKeyedByNormalizedName(ReactivePersistenceContext em) {
		return cachedAlternatives.getOrLoad(() -> alternativeIngredientDao.findAll(em)
				.map(list -> list.stream().collect(toMap(
						x -> normalizeRoleName(x.getName()),
						Function.identity(),
						(existing, _) -> existing,
						LinkedHashMap::new)))
				.map(Map::copyOf));
	}

	@Override
	public Uni<Map<String, RecommendationComponent>> retrieveAllRecommendationsKeyedByNormalizedName(ReactivePersistenceContext em) {
		return recommendationDao.listAllRecommendationsForScoring(em)
				.map(list -> list.stream().collect(toMap(this::normalizeRecommendationComponentName, Function.identity())));
	}

	private String normalizeRecommendationComponentName(RecommendationComponent rc) {
		return rc.getRecommendation().asString().trim().toLowerCase();
	}

	@Override
	public Uni<String> assessIngredientRole(String availableRolesAsMarkdownList, String ingredientNameInRecipe, String instructionsAsMarkdownList) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> ingredientRoleAiService.assessIngredientRole(
						availableRolesAsMarkdownList, ingredientNameInRecipe, instructionsAsMarkdownList))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(this::normalizeRoleName);
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	@Override
	public Uni<String> matchIngredientToTrigger(String availableTriggerIngredientsAsMarkdownList, String ingredientNameInRecipe, RoleOrTechnique role) {
		Context callerContext = Vertx.currentContext();
		Uni<String> resultUni = Uni.createFrom().item(() -> triggerIngredientMatcherAiService.matchIngredientToTrigger(
						availableTriggerIngredientsAsMarkdownList, ingredientNameInRecipe, role != null ? role.getName() : null))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(this::normalizeTriggerIngredientName);
		if (callerContext == null) return resultUni;
		return resultUni.emitOn(command -> callerContext.runOnContext(_ -> command.run()));
	}

	@Override
	public Uni<Set<String>> matchIngredientsWithRecommendations(String availableRecommendationsAsMarkdownList, String ingredientNameInRecipe) {
		Context callerContext = Vertx.currentContext();
		Uni<Set<String>> resultUni = Uni.createFrom().item(() -> ingredientMatchInRecommendationsAiService.matchIngredientsWithRecommendations(
						availableRecommendationsAsMarkdownList, ingredientNameInRecipe))
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
	public Uni<String> suggestAlternatives(String availableAlternativesAsMarkdownList, String recipeName, String ingredientNameInRecipe, String ingredientRoleOrTechnique) {
		// TODO Implement
		return null;
	}
}
