package eu.dietwise.services.v1.scoring.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.scoring.ScoringAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.types.RecommendationWeight;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeScoringServiceImplTest {
	private static final String AVAILABLE_RECOMMENDATIONS_AS_MARKDOWN =
			"- RecommendationComponentNameImpl(Fiber) (High-fiber foods)\n- RecommendationComponentNameImpl(Sodium)";
	private static final String INGREDIENT_1 = "ingredient-1";
	private static final String INGREDIENT_2 = "ingredient-2";
	private static final String TOMATO = "tomato";
	private static final String SALT = "salt";

	@Mock
	private ReactivePersistenceContextFactory persistenceContextFactory;

	@Mock
	private ReactivePersistenceContext persistenceContext;

	@Mock
	private RecommendationDao recommendationDao;

	@Mock
	private ScoringAiFacade scoringAiFacade;

	private RecipeScoringServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecipeScoringServiceImpl(persistenceContextFactory, recommendationDao, scoringAiFacade);
	}

	@Test
	void scoreRecipeBuildsScoringDataAndKeepsOnlyKnownRecommendations() {
		Recipe recipe = recipe(
				ingredient(INGREDIENT_1, TOMATO),
				ingredient(INGREDIENT_2, SALT)
		);
		List<RecommendationComponent> recommendationComponents = List.of(
				recommendationComponent("Fiber", RecommendationWeight.ENCOURAGED, "High-fiber foods"),
				recommendationComponent("Sodium", RecommendationWeight.LIMITED, null)
		);
		when(persistenceContextFactory.withoutTransaction(any()))
				.thenAnswer(invocation -> {
					Function<ReactivePersistenceContext, Uni<ScoringRecipeAssessmentMessage>> work = invocation.getArgument(0);
					return work.apply(persistenceContext);
				});
		when(recommendationDao.listAllRecommendationsForScoring(persistenceContext))
				.thenReturn(Uni.createFrom().item(recommendationComponents));
		when(scoringAiFacade.matchIngredientsWithRecommendations(any(), eq(TOMATO)))
				.thenReturn(Uni.createFrom().item(Set.of("FIBER", "unknown")));
		when(scoringAiFacade.matchIngredientsWithRecommendations(any(), eq(SALT)))
				.thenReturn(Uni.createFrom().item(Set.of("SODIUM", "fiber")));

		ScoringRecipeAssessmentMessage message = sut.scoreRecipe(recipe)
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(message.scoringData().getTotalNumberOfRecomendations()).isEqualTo(2);
		assertThat(message.scoringData().getRecommendationWeights())
				.containsEntry(new RecommendationComponentNameImpl("Fiber"), RecommendationWeight.ENCOURAGED)
				.containsEntry(new RecommendationComponentNameImpl("Sodium"), RecommendationWeight.LIMITED);
		assertThat(message.scoringData().getRecommendationsPerIngredient())
				.containsEntry(
						new GenericIngredientId(INGREDIENT_1),
						Set.of(new RecommendationComponentNameImpl("FIBER"))
				)
				.containsEntry(
						new GenericIngredientId(INGREDIENT_2),
						Set.of(new RecommendationComponentNameImpl("SODIUM"), new RecommendationComponentNameImpl("fiber"))
				);

		verify(recommendationDao).listAllRecommendationsForScoring(persistenceContext);
		var tomatoMarkdownCaptor = ArgumentCaptor.forClass(String.class);
		verify(scoringAiFacade).matchIngredientsWithRecommendations(tomatoMarkdownCaptor.capture(), eq(TOMATO));
		assertThat(tomatoMarkdownCaptor.getValue()).isEqualTo(AVAILABLE_RECOMMENDATIONS_AS_MARKDOWN);

		var saltMarkdownCaptor = ArgumentCaptor.forClass(String.class);
		verify(scoringAiFacade).matchIngredientsWithRecommendations(saltMarkdownCaptor.capture(), eq(SALT));
		assertThat(saltMarkdownCaptor.getValue()).isEqualTo(AVAILABLE_RECOMMENDATIONS_AS_MARKDOWN);
	}

	private static Recipe recipe(Ingredient... ingredients) {
		return ImmutableRecipe.builder()
				.recipeIngredients(List.of(ingredients))
				.recipeInstructions(List.of("step"))
				.build();
	}

	private static Ingredient ingredient(String ingredientId, String nameInRecipe) {
		return ImmutableIngredient.builder()
				.id(new GenericIngredientId(ingredientId))
				.nameInRecipe(nameInRecipe)
				.build();
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
