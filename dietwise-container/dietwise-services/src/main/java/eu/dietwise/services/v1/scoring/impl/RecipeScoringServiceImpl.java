package eu.dietwise.services.v1.scoring.impl;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static eu.dietwise.common.utils.UniComprehensions.forcm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.common.types.RepresentableAsString;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.scoring.RecipeScoringService;
import eu.dietwise.services.v1.scoring.ScoringAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableScoringData;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.types.IngredientId;
import eu.dietwise.v1.types.RecommendationComponentName;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class RecipeScoringServiceImpl implements RecipeScoringService {
	private final ReactivePersistenceContextFactory persistenceContextFactory;
	private final RecommendationDao recommendationDao;
	private final ScoringAiFacade scoringAiFacade;

	public RecipeScoringServiceImpl(
			ReactivePersistenceContextFactory persistenceContextFactory,
			RecommendationDao recommendationDao,
			ScoringAiFacade scoringAiFacade
	) {
		this.persistenceContextFactory = persistenceContextFactory;
		this.recommendationDao = recommendationDao;
		this.scoringAiFacade = scoringAiFacade;
	}

	@Override
	public Uni<ScoringRecipeAssessmentMessage> scoreRecipe(Recipe recipe) {
		return persistenceContextFactory.withoutTransaction(em -> scoreRecipeInternal(em, recipe));
	}

	private Uni<ScoringRecipeAssessmentMessage> scoreRecipeInternal(ReactivePersistenceContext em, Recipe recipe) {
		return forcm(
				recommendationDao.listAllRecommendationsForScoring(em),
				extractRecommendationsForIngredient(recipe),
				this::toScoringRecipeAssessmentMessage
		);
	}

	private Function<? super List<RecommendationComponent>, Uni<? extends Map<IngredientId, Set<RecommendationComponentName>>>> extractRecommendationsForIngredient(Recipe recipe) {
		return recommendationComponents -> Multi.createFrom().iterable(recipe.getRecipeIngredients())
				.onItem().transformToUniAndConcatenate(processIngredient(recommendationComponents))
				.collect().asMap(IngredientIdAndRecommendations::ingredientId, IngredientIdAndRecommendations::recommendations);
	}

	private Function<? super Ingredient, Uni<? extends IngredientIdAndRecommendations>> processIngredient(List<RecommendationComponent> recommendationComponents) {
		String availableRecommendationsAsMarkdownList = recommendationComponents.stream()
				.map(c -> "- " + c.getComponentForScoring() + c.getExplanationForLlm().map(e -> " (" + e + ')').orElse(""))
				.collect(Collectors.joining("\n"));
		Set<String> lowerCaseComponentNames = recommendationComponents.stream()
				.map(RecommendationComponent::getComponentForScoring)
				.map(RepresentableAsString::asString)
				.map(String::toLowerCase)
				.collect(toSet());
		return ingredient -> scoringAiFacade.matchIngredientsWithRecommendations(availableRecommendationsAsMarkdownList, ingredient.getNameInRecipe())
				.map(recommendations -> new IngredientIdAndRecommendations(ingredient.getId(), sanitizeRecommendationsFromAi(lowerCaseComponentNames, recommendations)));
	}

	private Set<RecommendationComponentName> sanitizeRecommendationsFromAi(Set<String> lowerCaseComponentNames, Set<String> recommendationsFromAi) {
		return recommendationsFromAi.stream()
				.filter(Objects::nonNull)
				.filter(r -> lowerCaseComponentNames.contains(r.trim().toLowerCase()))
				.map(RecommendationComponentNameImpl::new)
				.collect(toSet());
	}

	private ScoringRecipeAssessmentMessage toScoringRecipeAssessmentMessage(
			List<RecommendationComponent> recommendationComponents, Map<IngredientId, Set<RecommendationComponentName>> recommendationsPerIngredient) {
		var scoringData = ImmutableScoringData.builder()
				.totalNumberOfRecomendations(recommendationComponents.size())
				.recommendationWeights(recommendationComponents.stream().collect(toMap(RecommendationComponent::getComponentForScoring, RecommendationComponent::getWeight)))
				.recommendationsPerIngredient(recommendationsPerIngredient)
				.build();
		return new ScoringRecipeAssessmentMessage(scoringData);
	}
}
