package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.dao.PersonalInfoDao;
import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.services.nondomain.DateTimeService;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutablePersonalInfo;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.PersonalInfo;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.BiologicalGender;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuggestionPrioritizerImplTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);
	private static final Recommendation HIGH_RECOMMENDATION = new RecommendationImpl("high");
	private static final Recommendation LOW_RECOMMENDATION = new RecommendationImpl("low");
	private static final Recommendation MISSING_RECOMMENDATION = new RecommendationImpl("missing");
	private static final Suggestion HIGH_SUGGESTION = suggestion("high", HIGH_RECOMMENDATION);
	private static final Suggestion LOW_SUGGESTION = suggestion("low", LOW_RECOMMENDATION);
	private static final Suggestion MISSING_SUGGESTION = suggestion("missing", MISSING_RECOMMENDATION);
	private static final List<Suggestion> INPUT_SUGGESTIONS = List.of(LOW_SUGGESTION, MISSING_SUGGESTION, HIGH_SUGGESTION);
	private static final Map<Recommendation, BigDecimal> WEIGHTS = Map.of(
			HIGH_RECOMMENDATION, BigDecimal.valueOf(0.9),
			LOW_RECOMMENDATION, BigDecimal.valueOf(0.2)
	);

	@Mock
	private DateTimeService dateTimeService;
	@Mock
	private RecommendationDao recommendationDao;
	@Mock
	private User user;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory =
			new MockReactivePersistenceContextFactory();

	@Test
	void prioritizeSuggestionsUsesAgeAndGenderWeightsAndSortsDescending() {
		var sut = new SuggestionPrioritizerImpl(dateTimeService, recommendationDao);

		PersonalInfo personalInfo = ImmutablePersonalInfo.builder()
				.yearOfBirth(1996)
				.gender(BiologicalGender.FEMALE)
				.build();
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(recommendationDao.findRecommendations(any(), eq(30), eq(BiologicalGender.FEMALE))).thenReturn(Uni.createFrom().item(WEIGHTS));

		List<Suggestion> result = persistenceContextFactory
				.withoutTransaction(em -> sut.prioritizeSuggestions(em, personalInfo, INPUT_SUGGESTIONS))
				.await().indefinitely();

		assertThat(result).containsExactly(HIGH_SUGGESTION, LOW_SUGGESTION, MISSING_SUGGESTION);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
		verify(recommendationDao).findRecommendations(any(), eq(30), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any(), eq(30));
		verify(recommendationDao, never()).findRecommendations(any(), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any());
	}

	@Test
	void prioritizeSuggestionsUsesGenderOnlyWeightsWhenAgeIsNull() {
		var sut = new SuggestionPrioritizerImpl(dateTimeService, recommendationDao);
		PersonalInfo personalInfo = org.mockito.Mockito.mock(PersonalInfo.class);
		when(personalInfo.getYearOfBirth()).thenReturn(null);
		when(personalInfo.getGender()).thenReturn(BiologicalGender.FEMALE);
		when(recommendationDao.findRecommendations(any(), eq(BiologicalGender.FEMALE))).thenReturn(Uni.createFrom().item(WEIGHTS));

		List<Suggestion> result = persistenceContextFactory
				.withoutTransaction(em -> sut.prioritizeSuggestions(em, personalInfo, INPUT_SUGGESTIONS))
				.await().indefinitely();

		assertThat(result).containsExactly(HIGH_SUGGESTION, LOW_SUGGESTION, MISSING_SUGGESTION);
		verify(recommendationDao).findRecommendations(any(), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any(), eq(30), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any(), eq(30));
		verify(recommendationDao, never()).findRecommendations(any());
		verify(dateTimeService, never()).getNow();
	}

	@Test
	void prioritizeSuggestionsUsesAgeOnlyWeightsWhenGenderIsNull() {
		var sut = new SuggestionPrioritizerImpl(dateTimeService, recommendationDao);
		PersonalInfo personalInfo = org.mockito.Mockito.mock(PersonalInfo.class);
		when(personalInfo.getYearOfBirth()).thenReturn(1996);
		when(personalInfo.getGender()).thenReturn(null);
		when(dateTimeService.getNow()).thenReturn(NOW);
		when(recommendationDao.findRecommendations(any(), eq(30))).thenReturn(Uni.createFrom().item(WEIGHTS));

		List<Suggestion> result = persistenceContextFactory
				.withoutTransaction(em -> sut.prioritizeSuggestions(em, personalInfo, INPUT_SUGGESTIONS))
				.await().indefinitely();

		assertThat(result).containsExactly(HIGH_SUGGESTION, LOW_SUGGESTION, MISSING_SUGGESTION);
		verify(recommendationDao).findRecommendations(any(), eq(30));
		verify(recommendationDao, never()).findRecommendations(any(), eq(30), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any(), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any());
	}

	@Test
	void prioritizeSuggestionsUsesDefaultWeightsWhenAgeAndGenderAreNull() {
		var sut = new SuggestionPrioritizerImpl(dateTimeService, recommendationDao);
		PersonalInfo personalInfo = org.mockito.Mockito.mock(PersonalInfo.class);
		when(personalInfo.getYearOfBirth()).thenReturn(null);
		when(personalInfo.getGender()).thenReturn(null);
		when(recommendationDao.findRecommendations(any())).thenReturn(Uni.createFrom().item(WEIGHTS));

		List<Suggestion> result = persistenceContextFactory
				.withoutTransaction(em -> sut.prioritizeSuggestions(em, personalInfo, INPUT_SUGGESTIONS))
				.await().indefinitely();

		assertThat(result).containsExactly(HIGH_SUGGESTION, LOW_SUGGESTION, MISSING_SUGGESTION);
		verify(recommendationDao).findRecommendations(any());
		verify(recommendationDao, never()).findRecommendations(any(), eq(30), eq(BiologicalGender.FEMALE));
		verify(recommendationDao, never()).findRecommendations(any(), eq(30));
		verify(recommendationDao, never()).findRecommendations(any(), eq(BiologicalGender.FEMALE));
		verify(dateTimeService, never()).getNow();
	}

	private static Suggestion suggestion(String suffix, Recommendation recommendation) {
		return ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId("suggestion-" + suffix))
				.alternative(new AlternativeIngredientImpl("alternative-" + suffix))
				.target(new AppliesTo.AppliesToRecipe("recipe-" + suffix))
				.ruleId(new GenericRuleId("rule-" + suffix))
				.recommendation(recommendation)
				.text("text-" + suffix)
				.build();
	}
}
