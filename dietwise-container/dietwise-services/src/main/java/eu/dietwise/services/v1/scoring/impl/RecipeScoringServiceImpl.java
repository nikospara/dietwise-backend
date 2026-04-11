package eu.dietwise.services.v1.scoring.impl;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.scoring.RecipeScoringService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableScoringData;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecipeLanguage;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RecipeScoringServiceImpl implements RecipeScoringService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final RecommendationDao recommendationDao;

	public RecipeScoringServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			RecommendationDao recommendationDao
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.recommendationDao = recommendationDao;
	}

	@Override
	public Uni<ScoringRecipeAssessmentMessage> makeScoringMessage(Map<IngredientId, Set<RecommendationComponent>> recommendations, RecipeLanguage lang) {
		return persistenceContextFactory.withoutTransaction(em -> makeScoringMessageInternal(em, recommendations, lang));
	}

	private Uni<ScoringRecipeAssessmentMessage> makeScoringMessageInternal(ReactivePersistenceContext em, Map<IngredientId, Set<RecommendationComponent>> recommendations, RecipeLanguage lang) {
		return recommendationDao.listAllRecommendationsForScoring(em, lang).map(recommendationComponents ->
				toScoringRecipeAssessmentMessage(recommendationComponents, recommendations)
		);
	}

	private ScoringRecipeAssessmentMessage toScoringRecipeAssessmentMessage(
			List<RecommendationComponent> recommendationComponents,
			Map<IngredientId, Set<RecommendationComponent>> recommendationsPerIngredient
	) {
		var recommendationNamesPerIngredient = recommendationsPerIngredient.entrySet().stream()
				.collect(toMap(Map.Entry::getKey, e -> e.getValue().stream().map(RecommendationComponent::getComponentForScoring).collect(toSet())));
		var scoringData = ImmutableScoringData.builder()
				.totalNumberOfRecomendations(recommendationComponents.size())
				.recommendationWeights(recommendationComponents.stream().collect(toMap(RecommendationComponent::getComponentForScoring, RecommendationComponent::getWeight)))
				.recommendationsPerIngredient(recommendationNamesPerIngredient)
				.build();
		return new ScoringRecipeAssessmentMessage(scoringData);
	}
}
