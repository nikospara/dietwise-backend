package eu.dietwise.services.v1.scoring.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.RecommendationWeight;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeScoringServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final String INGREDIENT_1 = "ingredient-1";

	@Mock
	private ReactivePersistenceContextFactory persistenceContextFactory;

	@Mock
	private ReactivePersistenceContext persistenceContext;

	@Mock
	private RecommendationDao recommendationDao;

	private RecipeScoringServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecipeScoringServiceImpl(persistenceContextFactory, recommendationDao);
	}

	@Test
	void scoreRecipeBuildsScoringDataAndKeepsOnlyKnownRecommendations() {
		var recommendationComp1 = recommendationComponent("Fiber", RecommendationWeight.ENCOURAGED, "High-fiber foods");
		var recommendationComp2 = recommendationComponent("Sodium", RecommendationWeight.LIMITED, null);
		var ingredientId1 = new GenericIngredientId(INGREDIENT_1);
		List<RecommendationComponent> recommendationComponents = List.of(recommendationComp1, recommendationComp2);
		when(persistenceContextFactory.withoutTransaction(any()))
				.thenAnswer(invocation -> {
					Function<ReactivePersistenceContext, Uni<ScoringRecipeAssessmentMessage>> work = invocation.getArgument(0);
					return work.apply(persistenceContext);
				});
		when(recommendationDao.listAllRecommendationsForScoring(persistenceContext, RecipeLanguage.EN))
				.thenAnswer(iom -> Uni.createFrom().item(recommendationComponents));

		ScoringRecipeAssessmentMessage message = sut.makeScoringMessage(Map.of(ingredientId1, Set.of(recommendationComp1)), RecipeLanguage.EN)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(message.scoringData().getTotalNumberOfRecomendations()).isEqualTo(2);
		assertThat(message.scoringData().getRecommendationWeights())
				.containsEntry(new RecommendationComponentNameImpl("Fiber"), RecommendationWeight.ENCOURAGED)
				.containsEntry(new RecommendationComponentNameImpl("Sodium"), RecommendationWeight.LIMITED);
		assertThat(message.scoringData().getRecommendationsPerIngredient())
				.containsEntry(
						new GenericIngredientId(INGREDIENT_1),
						Set.of(new RecommendationComponentNameImpl("Fiber"))
				);

		verify(recommendationDao).listAllRecommendationsForScoring(persistenceContext, RecipeLanguage.EN);
	}

	private static RecommendationComponent recommendationComponent(
			String componentName, RecommendationWeight weight, String explanationForLlm) {
		return ImmutableRecommendationComponent.builder()
				.recommendation(new RecommendationImpl(componentName + "-recommendation"))
				.componentForScoring(new RecommendationComponentNameImpl(componentName))
				.weight(weight)
				.explanationForLlm(Optional.ofNullable(explanationForLlm))
				.build();
	}
}
