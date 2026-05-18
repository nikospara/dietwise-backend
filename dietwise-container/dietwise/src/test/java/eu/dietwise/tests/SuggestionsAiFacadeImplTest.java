package eu.dietwise.tests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import jakarta.inject.Inject;

import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiSelector;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiSelector;
import eu.dietwise.services.v1.suggestions.FindBestRuleAiSelector;
import eu.dietwise.services.v1.suggestions.IngredientRoleAiSelector;
import eu.dietwise.services.v1.suggestions.SuggestionsAiFacade;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiSelector;
import eu.dietwise.v1.types.RecipeLanguage;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(BreakerTestProfile.class)
class SuggestionsAiFacadeImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);

	@Inject
	SuggestionsAiFacade sut;

	@InjectMock
	RoleOrTechniqueDao roleOrTechniqueDao;

	@InjectMock
	TriggerIngredientDao triggerIngredientDao;

	@InjectMock
	AlternativeIngredientDao alternativeIngredientDao;

	@InjectMock
	RecommendationDao recommendationDao;

	@InjectMock
	IngredientRoleAiSelector ingredientRoleAiSelector;

	@InjectMock
	TriggerIngredientMatcherAiSelector triggerIngredientMatcherAiSelector;

	@InjectMock
	IngredientMatchInRecommendationsAiSelector ingredientMatchInRecommendationsAiSelector;

	@InjectMock
	FindBestRuleAiSelector findBestRuleAiSelector;

	@InjectMock
	AlternativeSuggestionAiSelector alternativeSuggestionAiSelector;

	@Test
	void breakerOpensAfterRepeatedAiFailures() {
		when(ingredientRoleAiSelector.assessIngredientRole(any(), anyString(), anyString(), anyString()))
				.thenThrow(new RuntimeException("Simulated Ollama timeout"));

		// Four consecutive failures should trip the breaker (requestVolumeThreshold=4, failureRatio=0.5)
		for (int i = 0; i < 4; i++) {
			sut.assessIngredientRole(RecipeLanguage.EN, "roles", "ingredient", "instructions")
					.subscribe().withSubscriber(UniAssertSubscriber.create())
					.awaitFailure(AWAIT);
		}
		verify(ingredientRoleAiSelector, times(4))
				.assessIngredientRole(any(), anyString(), anyString(), anyString());

		// The next call must fail fast with CircuitBreakerOpenException without reaching the AI selector
		clearInvocations(ingredientRoleAiSelector);
		sut.assessIngredientRole(RecipeLanguage.EN, "roles", "ingredient", "instructions")
				.subscribe().withSubscriber(UniAssertSubscriber.create())
				.awaitFailure(AWAIT)
				.assertFailedWith(CircuitBreakerOpenException.class);
		verifyNoInteractions(ingredientRoleAiSelector);
	}
}
