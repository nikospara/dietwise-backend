package eu.dietwise.services.v1.scoring.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.common.dao.reactive.ReactivePersistenceContextFactory;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.v1.recommendations.RecommendationService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.RecommendationSpecialWeight;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.TypeOfRecommendation;
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
	private static final String FIBER = "Fiber";
	private static final String SODIUM = "Sodium";
	private static final BigDecimal FIBER_WEIGHT = new BigDecimal("0.42");
	private static final BigDecimal SODIUM_WEIGHT = new BigDecimal("0.64");

	private static final int YEAR_OF_BIRTH = 1996;
	private static final PersonalInfo PERSONAL_INFO = ImmutablePersonalInfo.builder()
			.gender(BiologicalGender.FEMALE)
			.yearOfBirth(YEAR_OF_BIRTH)
			.build();

	@Mock
	private ReactivePersistenceContextFactory persistenceContextFactory;

	@Mock
	private ReactivePersistenceContext persistenceContext;

	@Mock
	private RecommendationService recommendationService;

	private RecipeScoringServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecipeScoringServiceImpl(persistenceContextFactory, recommendationService);
		when(persistenceContextFactory.withoutTransaction(any()))
				.thenAnswer(invocation -> {
					Function<ReactivePersistenceContext, Uni<ScoringRecipeAssessmentMessage>> work = invocation.getArgument(0);
					return work.apply(persistenceContext);
				});
	}

	@Test
	void scoreRecipeBuildsScoringDataAndKeepsOnlyKnownRecommendations() {
		givenRecommendationComponents();
		givenTheWeightsThatApplyTo(PERSONAL_INFO);

		ScoringRecipeAssessmentMessage message = makeScoringMessage(PERSONAL_INFO);

		assertThat(message.scoringData().getTotalNumberOfRecomendations()).isEqualTo(2);
		assertThat(message.scoringData().getHumanFriendlyDisplays())
				.containsEntry(new RecommendationComponentNameImpl(FIBER), "High-fiber foods")
				.doesNotContainKey(new RecommendationComponentNameImpl(SODIUM));
		assertThat(message.scoringData().getRecommendationsPerIngredient())
				.containsEntry(
						new GenericIngredientId(INGREDIENT_1),
						Set.of(new RecommendationComponentNameImpl(FIBER))
				);

		verify(recommendationService).listComponentsForScoring(persistenceContext, RecipeLanguage.EN);
	}

	@Test
	void weighsEachComponentWithTheWeightThatAppliesToTheUser() {
		givenRecommendationComponents();
		givenTheWeightsThatApplyTo(PERSONAL_INFO);

		ScoringRecipeAssessmentMessage message = makeScoringMessage(PERSONAL_INFO);

		assertThat(weightOf(message, FIBER).getTypeOfRecommendation()).isEqualTo(TypeOfRecommendation.ENCOURAGED);
		assertThat(weightOf(message, FIBER).getWeight()).isEqualByComparingTo(FIBER_WEIGHT);
		assertThat(weightOf(message, SODIUM).getTypeOfRecommendation()).isEqualTo(TypeOfRecommendation.LIMITED);
		assertThat(weightOf(message, SODIUM).getWeight()).isEqualByComparingTo(SODIUM_WEIGHT);

		verify(recommendationService).findRecommendationWeights(persistenceContext, PERSONAL_INFO);
	}

	@Test
	void asksForTheWeightsThatApplyWhenTheUserHasNoProfile() {
		givenRecommendationComponents();
		when(recommendationService.findRecommendationWeights(persistenceContext, null))
				.thenAnswer(_ -> Uni.createFrom().item(Map.of(recommendationOf(FIBER), FIBER_WEIGHT)));

		ScoringRecipeAssessmentMessage message = makeScoringMessage(null);

		assertThat(weightOf(message, FIBER).getWeight()).isEqualByComparingTo(FIBER_WEIGHT);

		verify(recommendationService).findRecommendationWeights(persistenceContext, null);
	}

	@Test
	void weighsNothingAComponentTheUserHasNoApplicableWeightFor() {
		givenRecommendationComponents();
		when(recommendationService.findRecommendationWeights(persistenceContext, null))
				.thenAnswer(_ -> Uni.createFrom().item(Map.<Recommendation, BigDecimal>of()));

		ScoringRecipeAssessmentMessage message = makeScoringMessage(null);

		// The age groups below 15 carry no recommendation weight at all; such a component takes no part in the score.
		assertThat(weightOf(message, FIBER).getWeight()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(weightOf(message, SODIUM).getWeight()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	private void givenRecommendationComponents() {
		when(recommendationService.listComponentsForScoring(persistenceContext, RecipeLanguage.EN))
				.thenAnswer(_ -> Uni.createFrom().item(List.of(
						recommendationComponent(FIBER, TypeOfRecommendation.ENCOURAGED, "High-fiber foods"),
						recommendationComponent(SODIUM, TypeOfRecommendation.LIMITED, null))));
	}

	private void givenTheWeightsThatApplyTo(PersonalInfo personalInfo) {
		when(recommendationService.findRecommendationWeights(persistenceContext, personalInfo))
				.thenAnswer(_ -> Uni.createFrom().item(Map.of(
						recommendationOf(FIBER), FIBER_WEIGHT,
						recommendationOf(SODIUM), SODIUM_WEIGHT)));
	}

	private ScoringRecipeAssessmentMessage makeScoringMessage(PersonalInfo personalInfo) {
		var ingredientId1 = new GenericIngredientId(INGREDIENT_1);
		var fiber = recommendationComponent(FIBER, TypeOfRecommendation.ENCOURAGED, "High-fiber foods");
		return sut.makeScoringMessage(Map.of(ingredientId1, Set.of(fiber)), RecipeLanguage.EN, personalInfo)
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static RecommendationSpecialWeight weightOf(ScoringRecipeAssessmentMessage message, String componentName) {
		return message.scoringData().getRecommendationWeights().get(new RecommendationComponentNameImpl(componentName));
	}

	private static Recommendation recommendationOf(String componentName) {
		return new RecommendationImpl(componentName + "-recommendation");
	}

	private static RecommendationComponent recommendationComponent(
			String componentName, TypeOfRecommendation typeOfRecommendation, String humanFriendlyDisplay) {
		return ImmutableRecommendationComponent.builder()
				.recommendation(recommendationOf(componentName))
				.componentForScoring(new RecommendationComponentNameImpl(componentName))
				.typeOfRecommendation(typeOfRecommendation)
				.humanFriendlyDisplay(Optional.ofNullable(humanFriendlyDisplay))
				.build();
	}
}
