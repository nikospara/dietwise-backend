package eu.dietwise.services.v1.scoring.impl;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.scoring.RecipeScoringService;
import eu.dietwise.services.v1.scoring.ScoringAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableScoringData;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RecipeScoringServiceImpl implements RecipeScoringService {
	private final RecommendationDao recommendationDao;
	private final ScoringAiFacade scoringAiFacade;

	public RecipeScoringServiceImpl(RecommendationDao recommendationDao, ScoringAiFacade scoringAiFacade) {
		this.recommendationDao = recommendationDao;
		this.scoringAiFacade = scoringAiFacade;
	}

	@Override
	public Uni<ScoringRecipeAssessmentMessage> scoreRecipe(ReactivePersistenceContext em, Recipe recipe) {
		return forcm(
				recommendationDao.listAllRecommendationsForScoring(em),
				extractRecommendationsForIngredient(recipe),
				this::toScoringRecipeAssessmentMessage
		);
	}

	private Function<? super List<RecommendationComponent>, Uni<? extends Map<IngredientId, Set<Recommendation>>>> extractRecommendationsForIngredient(Recipe recipe) {
		return recommendationComponents -> Multi.createFrom().iterable(recipe.getRecipeIngredients())
				.onItem().transformToUniAndConcatenate(processIngredient(recommendationComponents))
				.collect().asMap(IngredientIdAndRecommendations::ingredientId, IngredientIdAndRecommendations::recommendations);
	}

	private Function<? super Ingredient, Uni<? extends IngredientIdAndRecommendations>> processIngredient(List<RecommendationComponent> recommendationComponents) {
		String availableRecommendationsAsMarkdownList = recommendationComponents.stream()
				.map(c -> "- " + c.getComponentForScoring() + c.getExplanationForLlm().map(e -> " (" + e + ')').orElse(""))
				.collect(Collectors.joining("\n"));
		return ingredient -> scoringAiFacade.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredient.getNameInRecipe())
				.map(recommendations -> new IngredientIdAndRecommendations(ingredient.getId(), recommendations.stream().map(RecommendationImpl::new).collect(toSet())));
	}

	private ScoringRecipeAssessmentMessage toScoringRecipeAssessmentMessage(
			List<RecommendationComponent> recommendationComponents, Map<IngredientId, Set<Recommendation>> recommendationsPerIngredient) {
		var scoringData = ImmutableScoringData.builder()
				.totalNumberOfRecomendations(recommendationComponents.size())
				.recommendationWeights(recommendationComponents.stream().collect(toMap(RecommendationComponent::getRecommendation, RecommendationComponent::getWeight)))
				.recommendationsPerIngredient(recommendationsPerIngredient)
				.build();
		return new ScoringRecipeAssessmentMessage(scoringData);
	}
}
