package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import eu.dietwise.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.dietwise.services.v1.recommendations.RecommendationService;
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
	private static final PersonalInfo PERSONAL_INFO = ImmutablePersonalInfo.builder()
			.yearOfBirth(1996)
			.gender(BiologicalGender.FEMALE)
			.build();

	@Mock
	private RecommendationService recommendationService;

	@RegisterExtension
	private final MockReactivePersistenceContextFactory persistenceContextFactory =
			new MockReactivePersistenceContextFactory();

	@Test
	void ordersTheSuggestionsByTheWeightOfTheirRecommendationHighestFirst() {
		var sut = new SuggestionPrioritizerImpl(recommendationService);
		when(recommendationService.findRecommendationWeights(any(), eq(PERSONAL_INFO))).thenReturn(Uni.createFrom().item(WEIGHTS));

		List<Suggestion> result = persistenceContextFactory
				.withoutTransaction(em -> sut.prioritizeSuggestions(em, PERSONAL_INFO, INPUT_SUGGESTIONS))
				.await().indefinitely();

		// A recommendation the user has no weight for weighs nothing, so its suggestion ranks last.
		assertThat(result).containsExactly(HIGH_SUGGESTION, LOW_SUGGESTION, MISSING_SUGGESTION);
		assertThat(persistenceContextFactory.getOpenedTransactions()).isEmpty();
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
