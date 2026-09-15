package eu.dietwise.services.v1.scoring.impl;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.recommendations.RecommendationService;
import eu.dietwise.services.v1.scoring.RecipeScoringService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecommendationSpecialWeight;
import eu.dietwise.v1.model.ImmutableScoringData;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.RecommendationSpecialWeight;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RecipeScoringServiceImpl implements RecipeScoringService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final RecommendationService recommendationService;

	public RecipeScoringServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			RecommendationService recommendationService
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.recommendationService = recommendationService;
	}

	@Override
	public Uni<ScoringRecipeAssessmentMessage> makeScoringMessage(Map<IngredientId, Set<RecommendationComponent>> recommendations, RecipeLanguage lang, PersonalInfo personalInfo) {
		return persistenceContextFactory.withoutTransaction(em -> makeScoringMessageInternal(em, recommendations, lang, personalInfo));
	}

	private Uni<ScoringRecipeAssessmentMessage> makeScoringMessageInternal(ReactivePersistenceContext em, Map<IngredientId, Set<RecommendationComponent>> recommendations, RecipeLanguage lang, PersonalInfo personalInfo) {
		return forcm(
				recommendationService.listComponentsForScoring(em, lang),
				_ -> recommendationService.findRecommendationWeights(em, personalInfo),
				(recommendationComponents, weights) ->
						toScoringRecipeAssessmentMessage(recommendationComponents, weights, recommendations)
		);
	}

	private ScoringRecipeAssessmentMessage toScoringRecipeAssessmentMessage(
			List<RecommendationComponent> recommendationComponents,
			Map<Recommendation, BigDecimal> weights,
			Map<IngredientId, Set<RecommendationComponent>> recommendationsPerIngredient
	) {
		var recommendationNamesPerIngredient = recommendationsPerIngredient.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream().map(RecommendationComponent::getComponentForScoring).collect(toSet())));
		var scoringData = ImmutableScoringData.builder()
				.totalNumberOfRecomendations(recommendationComponents.size())
				.recommendationWeights(recommendationComponents.stream().collect(toMap(RecommendationComponent::getComponentForScoring, rc -> toSpecialWeight(rc, weights))))
				.humanFriendlyDisplays(recommendationComponents.stream()
						.filter(rc -> rc.getHumanFriendlyDisplay().isPresent())
						.collect(toMap(RecommendationComponent::getComponentForScoring, rc -> rc.getHumanFriendlyDisplay().get()))
				)
				.recommendationsPerIngredient(recommendationNamesPerIngredient)
				.build();
		return new ScoringRecipeAssessmentMessage(scoringData);
	}

	/**
	 * A component the user has no applicable recommendation weight for weighs nothing, so it takes no part in the score.
	 * This is how the age groups that the guidelines deliberately leave uncovered behave.
	 */
	private RecommendationSpecialWeight toSpecialWeight(RecommendationComponent component, Map<Recommendation, BigDecimal> weights) {
		return ImmutableRecommendationSpecialWeight.builder()
				.typeOfRecommendation(component.getTypeOfRecommendation())
				.weight(weights.getOrDefault(component.getRecommendation(), BigDecimal.ZERO))
				.build();
	}
}
