package eu.dietwise.services.v1.recommendations.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.model.recommendations.ImmutableRecommendationComponent;
import eu.dietwise.services.model.recommendations.RecommendationComponent;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.TypeOfRecommendation;
import eu.dietwise.v1.types.impl.RecommendationComponentNameImpl;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final int CURRENT_YEAR = 2026;
	private static final int YEAR_OF_BIRTH = 1996;
	private static final int AGE = CURRENT_YEAR - YEAR_OF_BIRTH;
	private static final BiologicalGender GENDER = BiologicalGender.FEMALE;

	private static final Recommendation FIBER = new RecommendationImpl("fiber");
	private static final RecommendationComponent FIBER_COMPONENT = ImmutableRecommendationComponent.builder()
			.recommendation(FIBER)
			.componentForScoring(new RecommendationComponentNameImpl("fiber"))
			.typeOfRecommendation(TypeOfRecommendation.ENCOURAGED)
			.build();
	private static final Map<Recommendation, BigDecimal> WEIGHTS = Map.of(FIBER, new BigDecimal("0.42"));

	@Mock
	private ReactivePersistenceContext persistenceContext;

	@Mock
	private RecommendationDao recommendationDao;

	@Mock
	private DateTimeService dateTimeService;

	private RecommendationServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecommendationServiceImpl(recommendationDao, dateTimeService);
	}

	@Test
	void findsTheWeightsOfTheAgeGroupAndGenderOfTheUser() {
		givenTheCurrentYear();
		when(recommendationDao.findRecommendations(persistenceContext, AGE, GENDER)).thenReturn(Uni.createFrom().item(WEIGHTS));

		assertThat(findRecommendationWeights(profileOf(YEAR_OF_BIRTH, GENDER))).isEqualTo(WEIGHTS);

		verify(recommendationDao).findRecommendations(persistenceContext, AGE, GENDER);
		verify(recommendationDao, never()).findRecommendations(persistenceContext);
	}

	@Test
	void averagesOverTheAgeGroupsWhenOnlyTheGenderIsKnown() {
		when(recommendationDao.findRecommendations(persistenceContext, GENDER)).thenReturn(Uni.createFrom().item(WEIGHTS));

		assertThat(findRecommendationWeights(profileOf(null, GENDER))).isEqualTo(WEIGHTS);

		verify(recommendationDao).findRecommendations(persistenceContext, GENDER);
		verify(dateTimeService, never()).getNow();
	}

	@Test
	void averagesOverBothGendersWhenOnlyTheAgeIsKnown() {
		givenTheCurrentYear();
		when(recommendationDao.findRecommendations(persistenceContext, AGE)).thenReturn(Uni.createFrom().item(WEIGHTS));

		assertThat(findRecommendationWeights(profileOf(YEAR_OF_BIRTH, null))).isEqualTo(WEIGHTS);

		verify(recommendationDao).findRecommendations(persistenceContext, AGE);
	}

	@Test
	void averagesOverEveryAgeGroupAndGenderWhenTheProfileSaysNeither() {
		when(recommendationDao.findRecommendations(persistenceContext)).thenReturn(Uni.createFrom().item(WEIGHTS));

		assertThat(findRecommendationWeights(profileOf(null, null))).isEqualTo(WEIGHTS);

		verify(recommendationDao).findRecommendations(persistenceContext);
		verify(dateTimeService, never()).getNow();
	}

	@Test
	void averagesOverEveryAgeGroupAndGenderWhenThereIsNoProfileAtAll() {
		when(recommendationDao.findRecommendations(persistenceContext)).thenReturn(Uni.createFrom().item(WEIGHTS));

		assertThat(findRecommendationWeights(null)).isEqualTo(WEIGHTS);

		verify(recommendationDao).findRecommendations(persistenceContext);
		verify(dateTimeService, never()).getNow();
	}

	@Test
	void queriesTheDatabaseOnceHoweverOftenTheSameProfileAsks() {
		givenTheCurrentYear();
		var loads = countedWeights(persistenceContext, AGE, GENDER);

		assertThat(findRecommendationWeights(profileOf(YEAR_OF_BIRTH, GENDER))).isEqualTo(WEIGHTS);
		assertThat(findRecommendationWeights(profileOf(YEAR_OF_BIRTH, GENDER))).isEqualTo(WEIGHTS);

		assertThat(loads).hasValue(1);
	}

	@Test
	void queriesTheDatabaseSeparatelyForEachProfile() {
		givenTheCurrentYear();
		var femaleLoads = countedWeights(persistenceContext, AGE, GENDER);
		var maleLoads = countedWeights(persistenceContext, AGE, BiologicalGender.MALE);

		findRecommendationWeights(profileOf(YEAR_OF_BIRTH, GENDER));
		findRecommendationWeights(profileOf(YEAR_OF_BIRTH, BiologicalGender.MALE));

		assertThat(femaleLoads).hasValue(1);
		assertThat(maleLoads).hasValue(1);
	}

	@Test
	void queriesTheDatabaseAgainWhenTheQueryFailed() {
		givenTheCurrentYear();
		var loads = new AtomicInteger();
		when(recommendationDao.findRecommendations(persistenceContext, AGE, GENDER)).thenAnswer(_ ->
				loads.incrementAndGet() == 1
						? Uni.createFrom().failure(new IllegalStateException("the database is down"))
						: Uni.createFrom().item(WEIGHTS));

		assertThatThrownBy(() -> findRecommendationWeights(profileOf(YEAR_OF_BIRTH, GENDER)))
				.isInstanceOf(IllegalStateException.class);

		assertThat(findRecommendationWeights(profileOf(YEAR_OF_BIRTH, GENDER))).isEqualTo(WEIGHTS);
		assertThat(loads).hasValue(2);
	}

	@Test
	void keepsNothingForAnAgeNobodyReaches() {
		// The profile is not validated, so the age is whatever the user typed; caching it would grow without bound.
		when(dateTimeService.getNow()).thenReturn(LocalDateTime.of(CURRENT_YEAR, 5, 1, 12, 0));
		var loads = countedWeights(persistenceContext, CURRENT_YEAR - 1, GENDER);

		findRecommendationWeights(profileOf(1, GENDER));
		findRecommendationWeights(profileOf(1, GENDER));

		assertThat(loads).hasValue(2);
	}

	@Test
	void listsTheComponentsForScoringOncePerLanguage() {
		var englishLoads = countedComponents(RecipeLanguage.EN);
		var greekLoads = countedComponents(RecipeLanguage.EL);

		assertThat(listComponentsForScoring(RecipeLanguage.EN)).containsExactly(FIBER_COMPONENT);
		assertThat(listComponentsForScoring(RecipeLanguage.EN)).containsExactly(FIBER_COMPONENT);
		listComponentsForScoring(RecipeLanguage.EL);

		assertThat(englishLoads).hasValue(1);
		assertThat(greekLoads).hasValue(1);
	}

	private AtomicInteger countedWeights(ReactivePersistenceContext em, int age, BiologicalGender gender) {
		var loads = new AtomicInteger();
		when(recommendationDao.findRecommendations(em, age, gender)).thenAnswer(_ -> {
			loads.incrementAndGet();
			return Uni.createFrom().item(WEIGHTS);
		});
		return loads;
	}

	private AtomicInteger countedComponents(RecipeLanguage lang) {
		var loads = new AtomicInteger();
		when(recommendationDao.listAllRecommendationsForScoring(persistenceContext, lang)).thenAnswer(_ -> {
			loads.incrementAndGet();
			return Uni.createFrom().item(List.of(FIBER_COMPONENT));
		});
		return loads;
	}

	private List<RecommendationComponent> listComponentsForScoring(RecipeLanguage lang) {
		return sut.listComponentsForScoring(persistenceContext, lang).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private void givenTheCurrentYear() {
		when(dateTimeService.getNow()).thenReturn(LocalDateTime.of(CURRENT_YEAR, 5, 1, 12, 0));
	}

	private Map<Recommendation, BigDecimal> findRecommendationWeights(PersonalInfo personalInfo) {
		return sut.findRecommendationWeights(persistenceContext, personalInfo).await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));
	}

	private static PersonalInfo profileOf(Integer yearOfBirth, BiologicalGender gender) {
		return ImmutablePersonalInfo.builder().yearOfBirth(yearOfBirth).gender(gender).build();
	}
}
