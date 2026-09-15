package eu.dietwise.services.v1.scoring.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
import eu.dietwise.services.nondomain.DateTimeService;
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
	private static final BigDecimal FIBER_VALUE = new BigDecimal("0.42");
	private static final BigDecimal SODIUM_VALUE = new BigDecimal("0.64");

	private static final int CURRENT_YEAR = 2026;
	private static final int YEAR_OF_BIRTH = 1996;
	private static final int AGE = CURRENT_YEAR - YEAR_OF_BIRTH;
	private static final PersonalInfo PERSONAL_INFO = ImmutablePersonalInfo.builder()
			.gender(BiologicalGender.FEMALE)
			.yearOfBirth(YEAR_OF_BIRTH)
			.build();

	@Mock
	private ReactivePersistenceContextFactory persistenceContextFactory;

	@Mock
	private ReactivePersistenceContext persistenceContext;

	@Mock
	private RecommendationDao recommendationDao;

	@Mock
	private DateTimeService dateTimeService;

	private RecipeScoringServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecipeScoringServiceImpl(persistenceContextFactory, recommendationDao, dateTimeService);
		when(persistenceContextFactory.withoutTransaction(any()))
				.thenAnswer(invocation -> {
					Function<ReactivePersistenceContext, Uni<ScoringRecipeAssessmentMessage>> work = invocation.getArgument(0);
					return work.apply(persistenceContext);
				});
	}

	@Test
	void scoreRecipeBuildsScoringDataAndKeepsOnlyKnownRecommendations() {
		givenRecommendationComponents();
		when(dateTimeService.getNow()).thenReturn(LocalDateTime.of(CURRENT_YEAR, 5, 1, 12, 0));
		when(recommendationDao.findRecommendations(persistenceContext, AGE, BiologicalGender.FEMALE))
				.thenAnswer(_ -> Uni.createFrom().item(Map.of(
						recommendationOf(FIBER), FIBER_VALUE,
						recommendationOf(SODIUM), SODIUM_VALUE)));

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

		verify(recommendationDao).listAllRecommendationsForScoring(persistenceContext, RecipeLanguage.EN);
	}

	@Test
	void weighsEachComponentWithTheValueThatAppliesToTheAgeAndGenderOfTheUser() {
		givenRecommendationComponents();
		when(dateTimeService.getNow()).thenReturn(LocalDateTime.of(CURRENT_YEAR, 5, 1, 12, 0));
		when(recommendationDao.findRecommendations(persistenceContext, AGE, BiologicalGender.FEMALE))
				.thenAnswer(_ -> Uni.createFrom().item(Map.of(
						recommendationOf(FIBER), FIBER_VALUE,
						recommendationOf(SODIUM), SODIUM_VALUE)));

		ScoringRecipeAssessmentMessage message = makeScoringMessage(PERSONAL_INFO);

		assertThat(weightOf(message, FIBER).getTypeOfRecommendation()).isEqualTo(TypeOfRecommendation.ENCOURAGED);
		assertThat(weightOf(message, FIBER).getWeight()).isEqualByComparingTo(FIBER_VALUE);
		assertThat(weightOf(message, SODIUM).getTypeOfRecommendation()).isEqualTo(TypeOfRecommendation.LIMITED);
		assertThat(weightOf(message, SODIUM).getWeight()).isEqualByComparingTo(SODIUM_VALUE);

		verify(recommendationDao).findRecommendations(persistenceContext, AGE, BiologicalGender.FEMALE);
	}

	@Test
	void averagesOverEveryAgeAndGenderWhenTheUserHasNoProfile() {
		givenRecommendationComponents();
		when(recommendationDao.findRecommendations(persistenceContext))
				.thenAnswer(_ -> Uni.createFrom().item(Map.of(recommendationOf(FIBER), FIBER_VALUE)));

		ScoringRecipeAssessmentMessage message = makeScoringMessage(null);

		assertThat(weightOf(message, FIBER).getWeight()).isEqualByComparingTo(FIBER_VALUE);

		verify(recommendationDao).findRecommendations(persistenceContext);
	}

	@Test
	void weighsNothingAComponentTheUserHasNoApplicableValueFor() {
		givenRecommendationComponents();
		when(recommendationDao.findRecommendations(persistenceContext))
				.thenAnswer(_ -> Uni.createFrom().item(Map.<Recommendation, BigDecimal>of()));

		ScoringRecipeAssessmentMessage message = makeScoringMessage(null);

		// The age groups below 15 carry no recommendation value at all; such a component takes no part in the score.
		assertThat(weightOf(message, FIBER).getWeight()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(weightOf(message, SODIUM).getWeight()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	private void givenRecommendationComponents() {
		when(recommendationDao.listAllRecommendationsForScoring(persistenceContext, RecipeLanguage.EN))
				.thenAnswer(_ -> Uni.createFrom().item(List.of(
						recommendationComponent(FIBER, TypeOfRecommendation.ENCOURAGED, "High-fiber foods"),
						recommendationComponent(SODIUM, TypeOfRecommendation.LIMITED, null))));
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
