package eu.dietwise.services.v1.suggestions.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import eu.dietwise.dao.recommendations.RecommendationDao;
import eu.dietwise.dao.suggestions.AlternativeIngredientDao;
import eu.dietwise.dao.suggestions.RoleOrTechniqueDao;
import eu.dietwise.dao.suggestions.TriggerIngredientDao;
import eu.dietwise.services.model.suggestions.ImmutableTriggerIngredient;
import eu.dietwise.services.model.suggestions.TriggerIngredient;
import eu.dietwise.services.types.suggestions.TriggerIngredientId;
import eu.dietwise.services.v1.ai.AiCircuitBreaker;
import eu.dietwise.services.v1.i18n.I18nMessages;
import eu.dietwise.services.v1.scoring.IngredientMatchInRecommendationsAiSelector;
import eu.dietwise.services.v1.suggestions.AlternativeSuggestionAiSelector;
import eu.dietwise.services.v1.suggestions.FindBestRuleAiSelector;
import eu.dietwise.services.v1.suggestions.IngredientRoleAiSelector;
import eu.dietwise.services.v1.suggestions.TriggerIngredientMatcherAiSelector;
import eu.dietwise.v1.model.ImmutableRule;
import eu.dietwise.v1.model.Rule;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import eu.dietwise.v1.types.impl.TriggerIngredientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuggestionsAiFacadeImplTest {
	private static final Duration AWAIT = Duration.ofSeconds(5);

	@Mock
	private FindBestRuleAiSelector findBestRuleAiSelector;

	@Test
	void findBestRuleRendersCandidateRuleWithoutRoleOrTechnique() {
		var sut = facadeWith(findBestRuleAiSelector);

		Rule ruleWithoutRole = ImmutableRule.builder()
				.id(new GenericRuleId("rule-without-role"))
				.recommendation(new RecommendationImpl("Decrease red meat"))
				.triggerIngredient(new TriggerIngredientImpl("Beef"))
				.build();
		TriggerIngredient triggerIngredient = ImmutableTriggerIngredient.builder()
				.id(mock(TriggerIngredientId.class))
				.name("Beef")
				.build();

		var promptCaptor = ArgumentCaptor.forClass(String.class);
		when(findBestRuleAiSelector.findBestRule(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn("1");

		String resolvedRuleId = sut.findBestRule(
				RecipeLanguage.EN, "minced beef", null, triggerIngredient, List.of(), List.of(ruleWithoutRole))
				.await().atMost(AWAIT);

		assertThat(resolvedRuleId).isEqualTo(ruleWithoutRole.getId().asString());
		verify(findBestRuleAiSelector)
				.findBestRule(any(), anyString(), anyString(), anyString(), anyString(), promptCaptor.capture());
		assertThat(promptCaptor.getValue()).contains("Decrease red meat");
	}

	private static SuggestionsAiFacadeImpl facadeWith(FindBestRuleAiSelector findBestRuleAiSelector) {
		return new SuggestionsAiFacadeImpl(
				mock(RoleOrTechniqueDao.class),
				mock(TriggerIngredientDao.class),
				mock(AlternativeIngredientDao.class),
				mock(RecommendationDao.class),
				mock(IngredientRoleAiSelector.class),
				mock(TriggerIngredientMatcherAiSelector.class),
				mock(IngredientMatchInRecommendationsAiSelector.class),
				findBestRuleAiSelector,
				mock(AlternativeSuggestionAiSelector.class),
				new I18nMessages(),
				new AiCircuitBreaker());
	}
}
